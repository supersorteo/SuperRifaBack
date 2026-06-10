package com.rifas.platform.domain.payment.service;

import com.rifas.platform.domain.payment.entity.Payment;
import com.rifas.platform.domain.payment.entity.PaymentAttempt;
import com.rifas.platform.domain.payment.repository.PaymentRepository;
import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.shared.audit.service.AuditService;
import com.rifas.platform.shared.enums.NumberStatus;
import com.rifas.platform.shared.enums.PaymentStatus;
import com.rifas.platform.shared.enums.ReservationStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final AuditService auditService;

    @Transactional
    public Payment approveManualPayment(UUID paymentId, String notes) {
        Payment payment = findPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.UNDER_REVIEW) {
            throw new BusinessException("El pago no está en estado pendiente");
        }

        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedByUserId(ud.getId());
        payment.setReviewNotes(notes);

        confirmReservation(payment.getReservation());
        auditService.log("PAYMENT_APPROVED", "Payment", paymentId, null, notes);
        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment rejectManualPayment(UUID paymentId, String reason) {
        Payment payment = findPayment(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.UNDER_REVIEW) {
            throw new BusinessException("El pago no está en estado pendiente");
        }

        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();

        payment.setStatus(PaymentStatus.REJECTED);
        payment.setReviewedAt(LocalDateTime.now());
        payment.setReviewedByUserId(ud.getId());
        payment.setReviewNotes(reason);

        auditService.log("PAYMENT_REJECTED", "Payment", paymentId, null, reason);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void syncFromWebhook(String externalPaymentId, PaymentStatus newStatus,
                                 String providerResponse) {
        paymentRepository.findByExternalPaymentId(externalPaymentId).ifPresent(payment -> {
            PaymentAttempt attempt = PaymentAttempt.builder()
                    .payment(payment)
                    .status(newStatus)
                    .providerResponse(providerResponse)
                    .build();
            payment.getAttempts().add(attempt);
            payment.setStatus(newStatus);

            if (newStatus == PaymentStatus.APPROVED) {
                confirmReservation(payment.getReservation());
            }
            paymentRepository.save(payment);
            auditService.log("PAYMENT_WEBHOOK_SYNC", "Payment", payment.getId(), null,
                    "status=" + newStatus + " externalId=" + externalPaymentId);
        });
    }

    private void confirmReservation(Reservation reservation) {
        if (reservation == null) return;
        reservation.setStatus(ReservationStatus.CONFIRMED);

        List<RaffleNumber> numbers = raffleNumberRepository
                .findByRaffleOrderByNumberAsc(reservation.getRaffle());
        numbers.stream()
                .filter(n -> reservation.equals(n.getReservation()))
                .forEach(n -> {
                    n.setStatus(NumberStatus.PAID);
                    n.setPaidAt(LocalDateTime.now());
                    raffleNumberRepository.save(n);
                });
    }

    private Payment findPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
    }
}
