package com.rifas.platform.domain.payment.repository;

import com.rifas.platform.domain.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByExternalPaymentId(String externalPaymentId);
    List<Payment> findByReservationId(UUID reservationId);
    Page<Payment> findByReservationRaffleOrganizerId(UUID organizerId, Pageable pageable);
    boolean existsByExternalPaymentId(String externalPaymentId);
}
