package com.rifas.platform.domain.payment.repository;

import com.rifas.platform.domain.payment.entity.PaymentMethod;
import com.rifas.platform.shared.enums.PaymentMethodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    List<PaymentMethod> findByOrganizerIdAndActiveTrueOrderByDisplayOrderAsc(UUID organizerId);
    List<PaymentMethod> findByOrganizerIdAndActiveAndPublicVisibleTrueOrderByDisplayOrderAsc(UUID organizerId, boolean active);
    Optional<PaymentMethod> findFirstByOrganizerIdAndTypeAndActiveTrue(UUID organizerId, PaymentMethodType type);
    void deleteByOrganizerId(UUID organizerId);
}
