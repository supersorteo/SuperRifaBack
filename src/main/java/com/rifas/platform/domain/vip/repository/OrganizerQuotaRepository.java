package com.rifas.platform.domain.vip.repository;

import com.rifas.platform.domain.vip.entity.OrganizerQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizerQuotaRepository extends JpaRepository<OrganizerQuota, UUID> {

    Optional<OrganizerQuota> findByOrganizerId(UUID organizerId);

    /** Bloqueo pesimista para operaciones de consumo/crédito concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM OrganizerQuota q WHERE q.organizer.id = :organizerId")
    Optional<OrganizerQuota> findByOrganizerIdForUpdate(@Param("organizerId") UUID organizerId);

    void deleteByOrganizerId(UUID organizerId);
}
