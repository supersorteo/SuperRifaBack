package com.rifas.platform.domain.plan.repository;

import com.rifas.platform.domain.plan.entity.Subscription;
import com.rifas.platform.shared.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByOrganizerIdAndStatus(UUID organizerId, SubscriptionStatus status);
    Optional<Subscription> findByOrganizerId(UUID organizerId);
    void deleteByOrganizerId(UUID organizerId);
}
