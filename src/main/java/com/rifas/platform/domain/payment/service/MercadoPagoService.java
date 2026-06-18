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
import com.rifas.platform.domain.payment.entity.PaymentMethod;
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
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
        boolean secretConfigured = mpProps.getWebhookSecret() != null && !mpProps.getWebhookSecret().isBlank();
        log.info("[WEBHOOK] Recibido: mpPaymentId={}, mpUserId={}, firmaHeader={}, secretConfigurado={}",
                dataId, mpUserId, xSignature != null ? "SI" : "NO", secretConfigured);

        if (secretConfigured) {
            if (!isSignatureValid(dataId, xSignature, xRequestId)) {
                log.error("[WEBHOOK] BLOQUEADO — firma inválida para mpPaymentId={}. Borrá MP_WEBHOOK_SECRET en Railway para deshabilitar la validación.", dataId);
                return;
            }
            log.info("[WEBHOOK] Firma válida para mpPaymentId={}", dataId);
        } else {
            log.info("[WEBHOOK] Sin secret configurado — omitiendo validación de firma para mpPaymentId={}", dataId);
        }

        // Intento 1: token del organizador via mpUserId almacenado en integrationMetadata
        String accessToken = resolveWebhookToken(mpUserId);

        // Intento 2: platform token configurado en Railway (MP_ACCESS_TOKEN)
        if (accessToken == null) {
            String platformToken = mpProps.getAccessToken();
            if (platformToken != null && !platformToken.isBlank()) {
                log.warn("[WEBHOOK] mpUserId={} sin token en BD — usando platform token como fallback", mpUserId);
                accessToken = platformToken;
            }
        }

        // Intento 3: last-resort — probar todos los tokens MP activos. No persiste nada.
        if (accessToken == null) {
            accessToken = resolveTokenByBruteForce(dataId);
        }

        if (accessToken == null) {
            log.error("[WEBHOOK] Sin token resolvible para mpUserId={}, mpPaymentId={}. Guardá las credenciales MP en el perfil del organizador.", mpUserId, dataId);
            return;
        }

        MPRequestOptions opts = MPRequestOptions.builder().accessToken(accessToken).build();
        try {
            PaymentClient client = new PaymentClient();
            Payment mpPayment = client.get(Long.parseLong(dataId), opts);
            String mpStatus = mpPayment.getStatus();
            String externalReference = mpPayment.getExternalReference();
            PaymentStatus status = mapMpStatus(mpStatus);
            log.info("[WEBHOOK] mpPaymentId={} status={} externalReference={}", dataId, mpStatus, externalReference);
            paymentService.syncFromWebhook(dataId, externalReference, status, mpStatus);
        } catch (MPApiException ex) {
            log.error("[WEBHOOK] Error MP API para mpPaymentId={} [{}]: {}", dataId, ex.getStatusCode(), ex.getMessage());
        } catch (MPException ex) {
            log.error("[WEBHOOK] Error MP para mpPaymentId={}: {}", dataId, ex.getMessage());
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

    // Fallback de último recurso: prueba cada token MP activo hasta encontrar el que
    // puede leer el pago. Solo aplica cuando mpUserId no está guardado en BD (organizer
    // guardó credenciales antes de que fetchMpUserId existiera). No persiste nada —
    // el organizer debe re-guardar credenciales para que quede el mapeo indexado.
    private String resolveTokenByBruteForce(String dataId) {
        List<PaymentMethod> methods = paymentMethodRepo.findAllActiveMercadoPago();
        log.info("[WEBHOOK] Last-resort fallback: probando {} tokens MP para mpPaymentId={}", methods.size(), dataId);
        for (PaymentMethod pm : methods) {
            if (pm.getIntegrationMetadata() == null) continue;
            try {
                Map<String, String> meta = objectMapper.readValue(pm.getIntegrationMetadata(),
                        new TypeReference<>() {});
                String token = meta.get("accessToken");
                if (token == null || token.isBlank()) continue;
                MPRequestOptions testOpts = MPRequestOptions.builder().accessToken(token).build();
                Payment testPayment = new PaymentClient().get(Long.parseLong(dataId), testOpts);
                if (testPayment != null && testPayment.getId() != null) {
                    log.warn("[WEBHOOK] Token encontrado via fallback para mpPaymentId={}. Re-guarda las credenciales MP en el perfil del organizador para evitar este path.", dataId);
                    return token;
                }
            } catch (MPApiException ex) {
                if (ex.getStatusCode() == 401 || ex.getStatusCode() == 403) continue;
                log.warn("[WEBHOOK] Error MP fallback PM {}: [{}] {}", pm.getId(), ex.getStatusCode(), ex.getMessage());
            } catch (Exception ex) {
                log.warn("[WEBHOOK] Error fallback PM {}: {}", pm.getId(), ex.getMessage());
            }
        }
        log.error("[WEBHOOK] Fallback fallido — ningún token activo pudo leer mpPaymentId={}. Re-guarda credenciales MP.", dataId);
        return null;
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

    private boolean isSignatureValid(String dataId, String xSignature, String xRequestId) {
        if (xSignature == null || xRequestId == null) return false;
        try {
            String ts = null, v1 = null;
            for (String part : xSignature.split(",")) {
                String[] kv = part.strip().split("=", 2);
                if (kv.length == 2) {
                    if ("ts".equals(kv[0])) ts = kv[1];
                    if ("v1".equals(kv[0])) v1 = kv[1];
                }
            }
            if (ts == null || v1 == null) return false;
            String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    mpProps.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            byte[] expected = HexFormat.of().parseHex(v1);
            return MessageDigest.isEqual(computed, expected);
        } catch (Exception ex) {
            log.warn("[WEBHOOK] Error evaluando firma: {}", ex.getMessage());
            return false;
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
