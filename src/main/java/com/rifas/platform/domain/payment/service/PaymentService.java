package com.rifas.platform.domain.payment.service;

import com.rifas.platform.domain.notification.websocket.RaffleEventPublisher;
import com.rifas.platform.domain.payment.entity.Payment;
import com.rifas.platform.domain.payment.entity.PaymentAttempt;
import com.rifas.platform.domain.payment.repository.PaymentRepository;
import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.shared.audit.service.AuditService;
import com.rifas.platform.shared.enums.NumberStatus;
import com.rifas.platform.shared.enums.PaymentStatus;
import com.rifas.platform.shared.enums.ReservationStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final ReservationRepository reservationRepository;
    private final RaffleEventPublisher eventPublisher;
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
    public void syncFromWebhook(String externalPaymentId, String externalReference,
                                 PaymentStatus newStatus, String providerResponse) {
        // Caso 1: ya existe un registro Payment con ese ID de MP
        Optional<Payment> existing = paymentRepository.findByExternalPaymentId(externalPaymentId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
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
            return;
        }

        // Caso 2: no hay registro Payment — buscar la reserva por externalReference (UUID)
        if (externalReference == null || externalReference.isBlank()) {
            log.warn("Webhook sin externalReference y sin Payment existente: mpId={}", externalPaymentId);
            return;
        }

        UUID reservationId;
        try {
            reservationId = UUID.fromString(externalReference);
        } catch (IllegalArgumentException ex) {
            log.warn("externalReference inválido en webhook: {}", externalReference);
            return;
        }

        if (newStatus != PaymentStatus.APPROVED) return;

        reservationRepository.findByIdWithRaffleAndParticipant(reservationId).ifPresentOrElse(reservation -> {
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                log.info("Reserva {} ya confirmada, webhook ignorado (mpPaymentId={})", reservationId, externalPaymentId);
                return;
            }
            confirmReservation(reservation);
            auditService.log("PAYMENT_WEBHOOK_CONFIRMED", "Reservation", reservationId, null,
                    "mpPaymentId=" + externalPaymentId);
            log.info("Reserva {} confirmada via webhook MP (mpPaymentId={})", reservationId, externalPaymentId);
        }, () -> log.warn("Reserva {} no encontrada para webhook mpPaymentId={}", reservationId, externalPaymentId));
    }

    private void confirmReservation(Reservation reservation) {
        if (reservation == null) return;
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        List<RaffleNumber> numbers = raffleNumberRepository.findByReservationId(reservation.getId());
        numbers.forEach(n -> {
            n.setStatus(NumberStatus.PAID);
            n.setPaidAt(LocalDateTime.now());
        });
        raffleNumberRepository.saveAll(numbers);

        UUID raffleId = reservation.getRaffle().getId();
        String raffleTitle = reservation.getRaffle().getTitle();
        String participantName = reservation.getParticipant().getFullName();
        List<Integer> nums = numbers.stream().map(RaffleNumber::getNumber).toList();
        BigDecimal amount = reservation.getTotalAmount();

        long available = raffleNumberRepository.countByRaffleAndStatus(reservation.getRaffle(), NumberStatus.AVAILABLE);
        long reserved  = raffleNumberRepository.countByRaffleAndStatus(reservation.getRaffle(), NumberStatus.RESERVED);
        long paid      = raffleNumberRepository.countByRaffleAndStatus(reservation.getRaffle(), NumberStatus.PAID);
        eventPublisher.publishNumbersUpdated(raffleId, (int) available, (int) reserved, (int) paid);
        eventPublisher.publishReservationConfirmed(raffleId, raffleTitle, participantName, nums, amount);
    }

    private Payment findPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
    }
}
