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
        MPRequestOptions opts = MPRequestOptions.builder().accessToken(accessToken).build();

        try {
            List<PreferenceItemRequest> items = List.of(
                    PreferenceItemRequest.builder()
                            .title("Números de rifa: " + reservation.getRaffle().getTitle())
                            .description("Reserva #" + reservation.getId().toString().substring(0, 8))
                            .quantity(1)
                            .unitPrice(reservation.getTotalAmount())
                            .currencyId("ARS")
                            .build()
            );

            String baseUrl = appProps.getBaseUrl();
            PreferenceRequest req = PreferenceRequest.builder()
                    .items(items)
                    .externalReference(reservation.getId().toString())
                    .backUrls(PreferenceBackUrlsRequest.builder()
                            .success(baseUrl.replace(":8080", ":4200") + "/reserva/exitosa")
                            .failure(baseUrl.replace(":8080", ":4200") + "/reserva/fallida")
                            .pending(baseUrl.replace(":8080", ":4200") + "/reserva/pendiente")
                            .build())
                    .autoReturn("approved")
                    .notificationUrl(baseUrl.replace(":4200", ":8080")
                            + "/api/payments/webhook/mercadopago")
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(req, opts);

            boolean sandbox = mpProps.isSandbox();
            String url = sandbox ? preference.getSandboxInitPoint() : preference.getInitPoint();

            return new PreferenceResult(preference.getId(), url);

        } catch (MPApiException | MPException ex) {
            log.error("MP preference creation failed: {}", ex.getMessage());
            throw new BusinessException("Error al crear preferencia de pago: " + ex.getMessage());
        }
    }

    public void processWebhook(String dataId, String xSignature, String xRequestId) {
        if (mpProps.getWebhookSecret() != null && !mpProps.getWebhookSecret().isBlank()) {
            validateWebhookSignature(dataId, xSignature, xRequestId);
        }

        // Webhook usa el token de la plataforma para verificar el pago
        MPRequestOptions opts = MPRequestOptions.builder()
                .accessToken(mpProps.getAccessToken())
                .build();
        try {
            PaymentClient client = new PaymentClient();
            Payment mpPayment = client.get(Long.parseLong(dataId), opts);

            PaymentStatus status = mapMpStatus(mpPayment.getStatus());
            paymentService.syncFromWebhook(dataId, status, mpPayment.getStatus());

        } catch (MPApiException | MPException ex) {
            log.error("Failed to process MP webhook for payment {}: {}", dataId, ex.getMessage());
        }
    }

    /**
     * Resuelve el access_token a usar para el organizer:
     * - Si el organizer configuró un método MERCADO_PAGO con token en integrationMetadata → usa ese
     * - Sino → fallback al token sandbox de la plataforma (para pruebas)
     */
    private String resolveToken(OrganizerProfile organizer) {
        return paymentMethodRepo
                .findFirstByOrganizerIdAndTypeAndActiveTrue(organizer.getId(), PaymentMethodType.MERCADO_PAGO)
                .map(pm -> {
                    try {
                        var meta = objectMapper.readValue(pm.getIntegrationMetadata(),
                                new TypeReference<java.util.Map<String, String>>() {});
                        String token = meta.get("accessToken");
                        if (token != null && !token.isBlank()) return token;
                    } catch (Exception ex) {
                        log.warn("Could not parse integrationMetadata for PaymentMethod {}: {}", pm.getId(), ex.getMessage());
                    }
                    return mpProps.getAccessToken();
                })
                .orElseGet(() -> {
                    log.debug("Organizer {} has no active MERCADO_PAGO method — using platform sandbox token", organizer.getId());
                    return mpProps.getAccessToken();
                });
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
