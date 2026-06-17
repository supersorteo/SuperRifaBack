package com.rifas.platform.domain.payment.repository;

import com.rifas.platform.domain.payment.entity.PaymentMethod;
import com.rifas.platform.shared.enums.PaymentMethodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(value = """
        SELECT * FROM payment_methods
        WHERE type = 'MERCADO_PAGO' AND active = true
          AND integration_metadata::jsonb ->> 'mpUserId' = :mpUserId
        LIMIT 1
        """, nativeQuery = true)
    Optional<PaymentMethod> findFirstActiveMpByMpUserId(@Param("mpUserId") String mpUserId);
}
