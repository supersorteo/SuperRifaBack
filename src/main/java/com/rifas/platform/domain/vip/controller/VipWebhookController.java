package com.rifas.platform.domain.vip.controller;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.rifas.platform.domain.vip.service.VipMercadoPagoService;
import com.rifas.platform.domain.vip.service.VipPurchaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook VIP de MP — completamente separado del webhook de reservas.
 * Mapeado en /api/payments/webhook/vip/mercadopago (cubierto por el permitAll de /api/payments/webhook/**).
 */
@RestController
@RequestMapping("/api/payments/webhook/vip")
@RequiredArgsConstructor
@Slf4j
public class VipWebhookController {

    private final VipMercadoPagoService mpService;
    private final VipPurchaseService    purchaseService;

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> handle(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "x-signature",   required = false) String xSignature,
            @RequestHeader(value = "x-request-id",  required = false) String xRequestId) {

        String type = (String) payload.get("type");
        log.info("[VIP-WEBHOOK] type={}", type);

        if (!"payment".equals(type)) {
            return ResponseEntity.ok().build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data == null || data.get("id") == null) return ResponseEntity.ok().build();

            String dataId = data.get("id").toString();

            if (!mpService.isSignatureValidOrUnset(dataId, xSignature, xRequestId)) {
                log.warn("[VIP-WEBHOOK] Firma inválida para paymentId={} — descartando", dataId);
                return ResponseEntity.ok().build();
            }

            Payment mpPayment = mpService.fetchPayment(dataId);
            String mpStatus        = mpPayment.getStatus();
            String externalReference = mpPayment.getExternalReference();

            log.info("[VIP-WEBHOOK] paymentId={} status={} ref={}", dataId, mpStatus, externalReference);

            if ("approved".equals(mpStatus)) {
                purchaseService.processApproval(dataId, externalReference);
            }

        } catch (MPApiException ex) {
            log.error("[VIP-WEBHOOK] Error MP API [{}]: {}", ex.getStatusCode(), ex.getMessage());
        } catch (MPException ex) {
            log.error("[VIP-WEBHOOK] Error MP: {}", ex.getMessage());
        } catch (Exception ex) {
            log.error("[VIP-WEBHOOK] Error inesperado: {}", ex.getMessage(), ex);
        }

        // Siempre 200 — evita que MP reintente indefinidamente
        return ResponseEntity.ok().build();
    }
}
