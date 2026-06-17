package com.rifas.platform.domain.payment.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.*;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.rifas.platform.config.AppProperties;
import com.rifas.platform.config.MercadoPagoProperties;
import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.payment.repository.PaymentMethodRepository;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.shared.enums.PaymentMethodType;
import com.rifas.platform.shared.enums.PaymentStatus;
import com.rifas.platform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoService {

    private final MercadoPagoProperties mpProps;
    private final AppProperties appProps;
    private final PaymentService paymentService;
    private final PaymentMethodRepository paymentMethodRepo;
    private final ObjectMapper objectMapper;

    public record PreferenceResult(String preferenceId, String checkoutUrl) {}

    public PreferenceResult createPreference(Reservation reservation) {
        String accessToken = resolveToken(reservation.getRaffle().getOrganizer());
        log.debug("Creating MP preference with token: {}...", accessToken.substring(0, Math.min(12, accessToken.length())));
        MPRequestOptions opts = MPRequestOptions.builder().accessToken(accessToken).build();

        try {
            List<PreferenceItemRequest> items = List.of(
                    PreferenceItemRequest.builder()
                            .title("Rifas: " + reservation.getRaffle().getTitle())
                            .quantity(1)
                            .unitPrice(reservation.getTotalAmount())
                            .currencyId("ARS")
                            .build()
            );

            String baseUrl = appProps.getBaseUrl();
            String frontUrl = appProps.getFrontUrl();
            boolean isLocalhost = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");

            String slug = reservation.getRaffle().getSlug();
            var reqBuilder = PreferenceRequest.builder()
                    .items(items)
                    .externalReference(reservation.getId().toString())
                    .backUrls(PreferenceBackUrlsRequest.builder()
                            .success(frontUrl + "/rifa/" + slug + "?pago=exitoso")
                            .failure(frontUrl + "/rifa/" + slug + "?pago=fallido")
                            .pending(frontUrl + "/rifa/" + slug + "?pago=pendiente")
                            .build());

            if (!isLocalhost) {
                reqBuilder
                    .autoReturn("approved")
                    .notificationUrl(baseUrl + "/api/payments/webhook/mercadopago");
            }

            PreferenceRequest req = reqBuilder.build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(req, opts);

            boolean sandbox = mpProps.isSandbox();
            String url = sandbox ? preference.getSandboxInitPoint() : preference.getInitPoint();

            return new PreferenceResult(preference.getId(), url);

        } catch (MPApiException ex) {
            String detail = ex.getApiResponse() != null ? ex.getApiResponse().getContent() : ex.getMessage();
            log.error("MP preference creation failed [{}]: {}", ex.getStatusCode(), detail);
            throw new BusinessException("Error al crear preferencia de pago con Mercado Pago. Verificá las credenciales del organizador.");
        } catch (MPException ex) {
            log.error("MP preference creation failed: {}", ex.getMessage());
            throw new BusinessException("Error de conexión con Mercado Pago. Intentá de nuevo.");
        }
    }

    public void processWebhook(String dataId, String mpUserId, String xSignature, String xRequestId) {
        if (mpProps.getWebhookSecret() != null && !mpProps.getWebhookSecret().isBlank()) {
            validateWebhookSignature(dataId, xSignature, xRequestId);
        }

        String accessToken = resolveWebhookToken(mpUserId);
        if (accessToken == null) {
            log.warn("No se encontró organizador para MP user_id={}, pago={}", mpUserId, dataId);
            return;
        }

        MPRequestOptions opts = MPRequestOptions.builder().accessToken(accessToken).build();
        try {
            PaymentClient client = new PaymentClient();
            Payment mpPayment = client.get(Long.parseLong(dataId), opts);
            PaymentStatus status = mapMpStatus(mpPayment.getStatus());
            paymentService.syncFromWebhook(dataId, status, mpPayment.getStatus());
        } catch (MPApiException | MPException ex) {
            log.error("Failed to process MP webhook for payment {}: {}", dataId, ex.getMessage());
        }
    }

    private String resolveWebhookToken(String mpUserId) {
        if (mpUserId == null) return null;
        var pm = paymentMethodRepo.findFirstActiveMpByMpUserId(mpUserId).orElse(null);
        if (pm == null || pm.getIntegrationMetadata() == null) return null;
        try {
            var meta = objectMapper.readValue(pm.getIntegrationMetadata(),
                    new TypeReference<java.util.Map<String, String>>() {});
            return meta.get("accessToken");
        } catch (Exception ex) {
            log.warn("Could not parse integrationMetadata for mp_user_id {}: {}", mpUserId, ex.getMessage());
            return null;
        }
    }

    private String resolveToken(OrganizerProfile organizer) {
        var pm = paymentMethodRepo
                .findFirstByOrganizerIdAndTypeAndActiveTrue(organizer.getId(), PaymentMethodType.MERCADO_PAGO)
                .orElseThrow(() -> new BusinessException(
                        "El organizador no tiene Mercado Pago configurado como método de pago"));

        String meta = pm.getIntegrationMetadata();
        if (meta == null || meta.isBlank()) {
            throw new BusinessException(
                    "El organizador no completó la configuración de Mercado Pago. Contactá al organizador.");
        }

        try {
            var metaMap = objectMapper.readValue(meta, new TypeReference<java.util.Map<String, String>>() {});
            String token = metaMap.get("accessToken");
            if (token != null && !token.isBlank()) return token;
        } catch (Exception ex) {
            log.warn("Could not parse integrationMetadata for PaymentMethod {}: {}", pm.getId(), ex.getMessage());
        }
        throw new BusinessException(
                "Error al leer las credenciales de Mercado Pago. Contactá al organizador.");
    }

    private void validateWebhookSignature(String dataId, String xSignature, String xRequestId) {
        if (xSignature == null || xRequestId == null) return;
        try {
            String ts = null;
            String v1 = null;
            for (String part : xSignature.split(",")) {
                String[] kv = part.strip().split("=", 2);
                if (kv.length == 2) {
                    if ("ts".equals(kv[0]))   ts = kv[1];
                    if ("v1".equals(kv[0]))   v1 = kv[1];
                }
            }
            if (ts == null || v1 == null) return;

            String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    mpProps.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(
                    mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

            if (!computed.equals(v1)) {
                throw new BusinessException("Firma de webhook inválida");
            }
        } catch (Exception ex) {
            throw new BusinessException("Error validando firma de webhook: " + ex.getMessage());
        }
    }

    private PaymentStatus mapMpStatus(String mpStatus) {
        return switch (mpStatus) {
            case "approved"      -> PaymentStatus.APPROVED;
            case "rejected"      -> PaymentStatus.REJECTED;
            case "cancelled"     -> PaymentStatus.CANCELLED;
            case "refunded"      -> PaymentStatus.REFUNDED;
            case "in_process",
                 "authorized"    -> PaymentStatus.UNDER_REVIEW;
            default              -> PaymentStatus.PENDING;
        };
    }
}
