package com.rifas.platform.domain.payment.controller;

import com.rifas.platform.domain.payment.service.MercadoPagoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final MercadoPagoService mercadoPagoService;

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> mercadoPago(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId) {

        String type = (String) payload.get("type");
        if (!"payment".equals(type)) {
            return ResponseEntity.ok().build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) payload.get("data");
            if (data != null && data.get("id") != null) {
                String dataId = data.get("id").toString();
                mercadoPagoService.processWebhook(dataId, xSignature, xRequestId);
            }
        } catch (Exception ex) {
            log.error("Webhook processing error: {}", ex.getMessage());
        }
        // Siempre 200 para que MP no reintente indefinidamente
        return ResponseEntity.ok().build();
    }
}
