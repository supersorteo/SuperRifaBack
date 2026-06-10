package com.rifas.platform.domain.reservation.repository;

import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.shared.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    Page<Reservation> findByRaffleId(UUID raffleId, Pageable pageable);
    List<Reservation> findByRaffleIdAndStatus(UUID raffleId, ReservationStatus status);
    List<Reservation> findByParticipantId(UUID participantId);
}
