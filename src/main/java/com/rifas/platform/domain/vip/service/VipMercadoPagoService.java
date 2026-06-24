package com.rifas.platform.domain.vip.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.*;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.rifas.platform.config.AppProperties;
import com.rifas.platform.config.MercadoPagoProperties;
import com.rifas.platform.domain.vip.entity.VipPurchase;
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

/**
 * Integración MP para compras VIP de la PLATAFORMA.
 * Siempre usa mpProps.getAccessToken() — nunca el token del organizer.
 * Completamente independiente de MercadoPagoService (pagos de reservas).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VipMercadoPagoService {

    private final MercadoPagoProperties mpProps;
    private final AppProperties appProps;

    public record PreferenceResult(String preferenceId, String initPoint) {}

    public PreferenceResult createPreference(VipPurchase purchase) {
        String accessToken = mpProps.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(
                    "La plataforma no tiene Mercado Pago configurado. Contactá al administrador.");
        }

        MPRequestOptions opts = MPRequestOptions.builder().accessToken(accessToken).build();

        try {
            List<PreferenceItemRequest> items = List.of(
                    PreferenceItemRequest.builder()
                            .title("Cupos VIP: " + purchase.getVipPackage().getName())
                            .quantity(1)
                            .unitPrice(purchase.getAmount())
                            .currencyId(purchase.getCurrency())
                            .build()
            );

            String baseUrl  = appProps.getBaseUrl();
            String frontUrl = appProps.getFrontUrl();
            boolean isLocalhost = baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");

            var reqBuilder = PreferenceRequest.builder()
                    .items(items)
                    .externalReference(purchase.getId().toString())
                    .backUrls(PreferenceBackUrlsRequest.builder()
                            .success(frontUrl  + "/dashboard?vip=exitoso")
                            .failure(frontUrl  + "/dashboard?vip=fallido")
                            .pending(frontUrl  + "/dashboard?vip=pendiente")
                            .build());

            if (!isLocalhost) {
                reqBuilder
                        .autoReturn("approved")
                        .notificationUrl(baseUrl + "/api/payments/webhook/vip/mercadopago");
            }

            Preference preference = new PreferenceClient().create(reqBuilder.build(), opts);

            String url = mpProps.isSandbox()
                    ? preference.getSandboxInitPoint()
                    : preference.getInitPoint();

            return new PreferenceResult(preference.getId(), url);

        } catch (MPApiException ex) {
            String detail = ex.getApiResponse() != null ? ex.getApiResponse().getContent() : ex.getMessage();
            log.error("[VIP-MP] Preference creation failed [{}]: {}", ex.getStatusCode(), detail);
            throw new BusinessException("Error al crear preferencia VIP en Mercado Pago.");
        } catch (MPException ex) {
            log.error("[VIP-MP] Preference creation failed: {}", ex.getMessage());
            throw new BusinessException("Error de conexión con Mercado Pago.");
        }
    }

    public Payment fetchPayment(String paymentId) throws MPException, MPApiException {
        String accessToken = mpProps.getAccessToken();
        MPRequestOptions opts = MPRequestOptions.builder().accessToken(accessToken).build();
        return new PaymentClient().get(Long.parseLong(paymentId), opts);
    }

    /**
     * Valida la firma HMAC-SHA256 del webhook de MP.
     * Devuelve true si la firma es válida o si no hay secret configurado (pasa por default).
     */
    public boolean isSignatureValidOrUnset(String dataId, String xSignature, String xRequestId) {
        String secret = mpProps.getWebhookSecret();
        if (secret == null || secret.isBlank()) return true;
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
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            byte[] expected = HexFormat.of().parseHex(v1);
            return MessageDigest.isEqual(computed, expected);
        } catch (Exception ex) {
            log.warn("[VIP-MP] Error validando firma: {}", ex.getMessage());
            return false;
        }
    }
}
