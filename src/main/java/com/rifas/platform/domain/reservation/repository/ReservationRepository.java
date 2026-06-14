package com.rifas.platform.domain.reservation.repository;

import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.shared.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    Page<Reservation> findByRaffleId(UUID raffleId, Pageable pageable);
    List<Reservation> findByRaffleId(UUID raffleId);
    List<Reservation> findByRaffleIdAndStatus(UUID raffleId, ReservationStatus status);
    List<Reservation> findByParticipantId(UUID participantId);

    @Query("SELECT COUNT(DISTINCT r.participant.id) FROM Reservation r WHERE r.raffle.id = :raffleId")
    long countDistinctParticipantsByRaffleId(@Param("raffleId") UUID raffleId);

    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.participant
        JOIN FETCH r.raffle rf
        WHERE rf.organizer.id = :organizerId
          AND (:raffleId IS NULL OR rf.id = :raffleId)
          AND (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
        """)
    Page<Reservation> findByOrganizerWithDetails(
            @Param("organizerId") UUID organizerId,
            @Param("raffleId") UUID raffleId,
            @Param("status") ReservationStatus status,
            Pageable pageable
    );

    @Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.participant p
        JOIN FETCH r.raffle rf
        WHERE p.phone = :phone AND rf.slug = :slug
        ORDER BY r.createdAt DESC
        """)
    List<Reservation> findByParticipantPhoneAndRaffleSlug(
            @Param("phone") String phone,
            @Param("slug") String slug
    );

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.status = 'PENDING' AND r.expiresAt < :now
        """)
    List<Reservation> findExpiredPending(@Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        UPDATE Reservation r SET r.status = 'CANCELLED'
        WHERE r.status = 'PENDING' AND r.expiresAt < :now
        """)
    int expirePendingReservations(@Param("now") LocalDateTime now);
}
