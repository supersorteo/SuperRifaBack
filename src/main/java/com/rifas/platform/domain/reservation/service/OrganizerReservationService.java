package com.rifas.platform.domain.reservation.service;

import com.rifas.platform.domain.notification.websocket.RaffleEventPublisher;
import com.rifas.platform.domain.organizer.entity.OrganizerProfile;
import com.rifas.platform.domain.organizer.repository.OrganizerProfileRepository;
import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.reservation.dto.OrganizerReservationDto;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.shared.enums.NumberStatus;
import com.rifas.platform.shared.enums.ReservationStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizerReservationService {

    private final ReservationRepository reservationRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final RaffleEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<OrganizerReservationDto> listReservations(UUID raffleId, String phone, ReservationStatus status, Pageable pageable) {
        UUID organizerId = currentOrganizer().getId();
        return reservationRepository
                .findByOrganizerWithDetails(
                        organizerId,
                        raffleId,
                        phone != null && !phone.isBlank() ? phone.trim() : "",
                        status,
                        pageable
                )
                .map(this::toDto);
    }

    @Transactional
    public OrganizerReservationDto confirmReservation(UUID reservationId) {
        Reservation reservation = findOwnedReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessException("Solo se pueden confirmar reservas pendientes");
        }

        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservationRepository.save(reservation);

        List<RaffleNumber> numbers = raffleNumberRepository.findByReservationId(reservationId);
        numbers.forEach(n -> {
            n.setStatus(NumberStatus.PAID);
            n.setPaidAt(LocalDateTime.now());
        });
        raffleNumberRepository.saveAll(numbers);

        publishProgress(reservation, numbers);

        return toDto(reservation);
    }

    @Transactional
    public OrganizerReservationDto cancelReservation(UUID reservationId) {
        Reservation reservation = findOwnedReservation(reservationId);
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new BusinessException("No se puede cancelar una reserva ya confirmada");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException("La reserva ya está cancelada");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);

        List<RaffleNumber> numbers = raffleNumberRepository.findByReservationId(reservationId);
        numbers.forEach(n -> {
            n.setStatus(NumberStatus.AVAILABLE);
            n.setReservation(null);
            n.setReservedAt(null);
            n.setExpiresAt(null);
        });
        raffleNumberRepository.saveAll(numbers);

        publishProgress(reservation, numbers);

        return toDto(reservation);
    }

    private void publishProgress(Reservation reservation, List<RaffleNumber> numbers) {
        UUID raffleId = reservation.getRaffle().getId();
        long available = raffleNumberRepository.countByRaffleAndStatus(reservation.getRaffle(), NumberStatus.AVAILABLE);
        long reserved  = raffleNumberRepository.countByRaffleAndStatus(reservation.getRaffle(), NumberStatus.RESERVED);
        long paid      = raffleNumberRepository.countByRaffleAndStatus(reservation.getRaffle(), NumberStatus.PAID);

        List<Integer> nums = numbers.stream().map(RaffleNumber::getNumber).toList();
        eventPublisher.publishNumbersUpdated(raffleId, (int) available, (int) reserved, (int) paid);
        eventPublisher.publishNumbersReserved(raffleId, nums);
    }

    @Transactional
    public void deleteReservation(UUID reservationId) {
        Reservation reservation = findOwnedReservation(reservationId);
        List<RaffleNumber> numbers = raffleNumberRepository.findByReservationId(reservationId);
        if (!numbers.isEmpty()) {
            numbers.forEach(n -> {
                n.setStatus(NumberStatus.AVAILABLE);
                n.setReservation(null);
                n.setReservedAt(null);
                n.setExpiresAt(null);
                n.setPaidAt(null);
            });
            raffleNumberRepository.saveAll(numbers);
            publishProgress(reservation, numbers);
        }
        reservationRepository.delete(reservation);
    }

    private Reservation findOwnedReservation(UUID reservationId) {
        if (reservationId == null) throw new ResourceNotFoundException("Reserva no encontrada");
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
        UUID organizerId = currentOrganizer().getId();
        if (!reservation.getRaffle().getOrganizer().getId().equals(organizerId)) {
            throw new BusinessException("No tiene permisos sobre esta reserva");
        }
        return reservation;
    }

    private OrganizerProfile currentOrganizer() {
        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return organizerProfileRepository.findByUserId(ud.getId())
                .orElseThrow(() -> new BusinessException("Perfil de organizador no encontrado"));
    }

    private OrganizerReservationDto toDto(Reservation r) {
        List<Integer> numbers = raffleNumberRepository.findByReservationId(r.getId())
                .stream().map(RaffleNumber::getNumber).toList();
        return new OrganizerReservationDto(
                r.getId(),
                r.getParticipant().getFullName(),
                r.getParticipant().getPhone(),
                r.getParticipant().getEmail(),
                r.getParticipant().getDni(),
                numbers,
                r.getTotalAmount(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getExpiresAt(),
                r.getRaffle().getId(),
                r.getRaffle().getTitle(),
                r.getRaffle().getSlug()
        );
    }
}
