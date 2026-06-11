package com.rifas.platform.domain.raffle.repository;

import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.shared.enums.DrawMethod;
import com.rifas.platform.shared.enums.OperationalStatus;
import com.rifas.platform.shared.enums.PublicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaffleRepository extends JpaRepository<Raffle, UUID> {

    Optional<Raffle> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Raffle> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Raffle r WHERE r.slug = :slug")
    Optional<Raffle> findBySlugWithPessimisticLock(@Param("slug") String slug);

    @Query("""
        SELECT r FROM Raffle r
        WHERE r.drawMethod = :method
          AND r.publicationStatus = :pubStatus
          AND r.operationalStatus = :opStatus
          AND r.drawDateTime <= :now
        """)
    List<Raffle> findRafflesReadyForAutomaticDraw(
            @Param("method")    DrawMethod method,
            @Param("pubStatus") PublicationStatus pubStatus,
            @Param("opStatus")  OperationalStatus opStatus,
            @Param("now")       LocalDateTime now
    );

    long countByOrganizerId(UUID organizerId);

    @Query("SELECT COUNT(r) FROM Raffle r WHERE r.organizer.id = :orgId AND r.publicationStatus = 'PUBLISHED' AND r.operationalStatus NOT IN ('FINISHED','CANCELLED')")
    long countActiveByOrganizerId(@Param("orgId") UUID orgId);

    @Query("SELECT COUNT(r) FROM Raffle r WHERE r.organizer.id = :orgId AND r.createdAt >= :from")
    long countByOrganizerIdAndCreatedAtAfter(@Param("orgId") UUID orgId, @Param("from") LocalDateTime from);
}
