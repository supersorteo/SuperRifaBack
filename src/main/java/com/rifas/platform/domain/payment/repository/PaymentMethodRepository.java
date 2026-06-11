package com.rifas.platform.domain.payment.repository;

import com.rifas.platform.domain.payment.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {
    List<PaymentMethod> findByOrganizerIdAndActiveTrueOrderByDisplayOrderAsc(UUID organizerId);
    List<PaymentMethod> findByOrganizerIdAndActiveAndPublicVisibleTrueOrderByDisplayOrderAsc(UUID organizerId, boolean active);
    void deleteByOrganizerId(UUID organizerId);
}
