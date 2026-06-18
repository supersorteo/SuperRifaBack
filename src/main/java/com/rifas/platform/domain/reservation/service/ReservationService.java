package com.rifas.platform.domain.reservation.service;

import com.rifas.platform.config.ReservationProperties;
import com.rifas.platform.domain.execution.service.RaffleExecutionService;
import com.rifas.platform.domain.notification.websocket.RaffleEventPublisher;
import com.rifas.platform.domain.participant.entity.Participant;
import com.rifas.platform.domain.participant.repository.ParticipantRepository;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.raffle.repository.RaffleRepository;
import com.rifas.platform.domain.reservation.dto.CreateReservationRequest;
import com.rifas.platform.domain.reservation.dto.ReservationCreatedDto;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.shared.audit.service.AuditService;
import com.rifas.platform.shared.enums.NumberStatus;
import com.rifas.platform.shared.enums.OperationalStatus;
import com.rifas.platform.shared.enums.PublicationStatus;
import com.rifas.platform.shared.enums.ReservationStatus;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.NumberNotAvailableException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final RaffleRepository raffleRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final ParticipantRepository participantRepository;
    private final ReservationRepository reservationRepository;
    private final RaffleEventPublisher eventPublisher;
    private final AuditService auditService;
    private final ReservationProperties reservationProps;
    private final RaffleExecutionService raffleExecutionService;

    @Transactional
    public ReservationCreatedDto createReservation(CreateReservationRequest req) {
        Raffle raffle = raffleRepository.findBySlugWithPessimisticLock(req.raffleSlug())
                .orElseThrow(() -> new ResourceNotFoundException("Rifa no encontrada"));

        validateRaffleAcceptsReservations(raffle);
        validateAccessCode(raffle, req.accessCode());

        Participant participant = findOrCreateParticipant(req.participant());

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusMinutes(reservationProps.getExpiryMinutes());

        BigDecimal total = raffle.getPricePerNumber()
                .multiply(BigDecimal.valueOf(req.numbers().size()));

        Reservation reservation = Reservation.builder()
                .raffle(raffle)
                .participant(participant)
                .status(ReservationStatus.PENDING)
                .totalAmount(total)
                .expiresAt(expiresAt)
                .build();

        reservationRepository.save(reservation);

        List<RaffleNumber> reserved = new ArrayList<>();
        for (Integer num : req.numbers()) {
            RaffleNumber rn = raffleNumberRepository
                    .findByRaffleAndNumberWithLock(raffle, num)
                    .orElseThrow(() -> new ResourceNotFoundException("Numero no existe: " + num));

            if (rn.getStatus() != NumberStatus.AVAILABLE) {
                throw new NumberNotAvailableException(num);
            }

            rn.setStatus(NumberStatus.RESERVED);
            rn.setReservation(reservation);
            rn.setReservedAt(LocalDateTime.now());
            rn.setExpiresAt(expiresAt);
            reserved.add(raffleNumberRepository.save(rn));
        }

        long available = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.AVAILABLE);
        long paid = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.PAID);
        long reservedCount = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.RESERVED);

        if (available == 0) {
            raffle.setOperationalStatus(OperationalStatus.SOLD_OUT);
            raffleRepository.save(raffle);
        }

        List<Integer> nums = reserved.stream().map(RaffleNumber::getNumber).toList();
        eventPublisher.publishNumbersReserved(raffle.getId(), nums);
        eventPublisher.publishNumbersUpdated(raffle.getId(), (int) available, (int) reservedCount, (int) paid);
        eventPublisher.publishNewReservation(raffle.getId(), raffle.getTitle(),
                reservation.getParticipant().getFullName(), nums, reservation.getTotalAmount());

        if (available == 0 && reservedCount > 0 && raffle.getWinnerNumber() == null) {
            raffleExecutionService.executeAutomaticDraw(raffle.getId());
        }

        auditService.log("RESERVATION_CREATED", "Reservation", reservation.getId(), null, nums.toString());

        return new ReservationCreatedDto(
                reservation.getId(),
                reservation.getStatus(),
                reservation.getTotalAmount(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt()
        );
    }

    private void validateRaffleAcceptsReservations(Raffle raffle) {
        if (raffle.getPublicationStatus() != PublicationStatus.PUBLISHED) {
            throw new BusinessException("La rifa no esta disponible");
        }
        if (raffle.getOperationalStatus() == OperationalStatus.FINISHED
                || raffle.getOperationalStatus() == OperationalStatus.CANCELLED) {
            throw new BusinessException("La rifa ya ha finalizado");
        }
        if (raffle.getOperationalStatus() == OperationalStatus.SOLD_OUT) {
            throw new BusinessException("La rifa esta agotada");
        }
    }

    private void validateAccessCode(Raffle raffle, String accessCode) {
        String expected = raffle.getInternalCode();
        String received = accessCode != null ? accessCode.trim() : "";

        if (expected == null || expected.isBlank()) {
            throw new BusinessException("La rifa no tiene un codigo de acceso configurado");
        }
        if (!expected.equalsIgnoreCase(received)) {
            throw new BusinessException("El codigo de acceso de la rifa no es valido");
        }
    }

    private Participant findOrCreateParticipant(CreateReservationRequest.ParticipantDataRequest data) {
        return participantRepository.findByEmailAndPhone(data.email(), data.phone())
                .orElseGet(() -> participantRepository.save(Participant.builder()
                        .fullName(data.fullName())
                        .email(data.email())
                        .phone(data.phone())
                        .dni(data.dni())
                        .build()));
    }
}
