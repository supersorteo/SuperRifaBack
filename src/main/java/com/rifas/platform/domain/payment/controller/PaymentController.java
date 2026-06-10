package com.rifas.platform.domain.payment.controller;

import com.rifas.platform.domain.payment.entity.Payment;
import com.rifas.platform.domain.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    public record ReviewRequest(String notes) {}

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<Payment> approve(@PathVariable UUID id,
                                           @RequestBody(required = false) ReviewRequest req) {
        String notes = req != null ? req.notes() : null;
        return ResponseEntity.ok(paymentService.approveManualPayment(id, notes));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<Payment> reject(@PathVariable UUID id,
                                          @RequestBody(required = false) ReviewRequest req) {
        String reason = req != null ? req.notes() : null;
        return ResponseEntity.ok(paymentService.rejectManualPayment(id, reason));
    }
}
