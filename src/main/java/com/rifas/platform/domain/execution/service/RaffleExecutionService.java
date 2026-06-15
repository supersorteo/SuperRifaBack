package com.rifas.platform.domain.execution.service;

import com.rifas.platform.domain.execution.entity.RaffleExecution;
import com.rifas.platform.domain.execution.repository.RaffleExecutionRepository;
import com.rifas.platform.domain.execution.strategy.*;
import com.rifas.platform.domain.notification.websocket.RaffleEventPublisher;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.raffle.repository.RaffleRepository;
import com.rifas.platform.domain.reservation.entity.Reservation;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
import com.rifas.platform.shared.audit.service.AuditService;
import com.rifas.platform.shared.enums.*;
import com.rifas.platform.shared.exception.BusinessException;
import com.rifas.platform.shared.exception.ResourceNotFoundException;
import com.rifas.platform.shared.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RaffleExecutionService {

    private final RaffleRepository raffleRepository;
    private final RaffleNumberRepository raffleNumberRepository;
    private final ReservationRepository reservationRepository;
    private final RaffleExecutionRepository executionRepository;
    private final RaffleEventPublisher eventPublisher;
    private final AuditService auditService;

    private final ManualDrawStrategy manualStrategy;
    private final AutomaticDrawStrategy automaticStrategy;
    private final ExternalDrawStrategy externalStrategy;

    @Transactional
    public RaffleExecution executeManualDraw(UUID raffleId) {
        UserDetailsImpl ud = (UserDetailsImpl) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return performDraw(raffleId, DrawMethod.MANUAL, ud.getId());
    }

    @Transactional
    public RaffleExecution executeAutomaticDraw(UUID raffleId) {
        return performDraw(raffleId, DrawMethod.AUTOMATIC, null);
    }

    private RaffleExecution performDraw(UUID raffleId, DrawMethod method, UUID executedBy) {
        Raffle raffle = raffleRepository.findById(raffleId)
                .orElseThrow(() -> new ResourceNotFoundException("Rifa no encontrada"));

        if (raffle.getPublicationStatus() != PublicationStatus.PUBLISHED) {
            throw new BusinessException("La rifa debe estar publicada para ejecutar el sorteo");
        }
        if (raffle.getOperationalStatus() == OperationalStatus.FINISHED
                || raffle.getOperationalStatus() == OperationalStatus.CANCELLED
                || raffle.getWinnerNumber() != null) {
            throw new BusinessException("La rifa ya finalizo y ya tiene un ganador");
        }
        if (executionRepository.existsByRaffle(raffle)) {
            throw new BusinessException("La rifa ya tiene un sorteo registrado");
        }

        List<Integer> eligible = buildEligibleNumbers(raffle);

        raffle.setOperationalStatus(OperationalStatus.EXECUTING);
        raffleRepository.saveAndFlush(raffle);
        eventPublisher.publishDrawStarted(raffle.getId());

        DrawStrategy strategy = resolveStrategy(method);
        DrawResult result;
        try {
            publishLiveCountdown(raffle.getId());
            result = strategy.execute(raffle, eligible, executedBy);
        } catch (Exception ex) {
            raffle.setOperationalStatus(OperationalStatus.ACTIVE);
            raffleRepository.save(raffle);
            eventPublisher.publishDrawFailed(raffle.getId());
            log.error("Draw failed for raffle {}: {}", raffleId, ex.getMessage());
            throw new BusinessException("Error en el sorteo: " + ex.getMessage());
        }

        RaffleExecution execution = RaffleExecution.builder()
                .raffle(raffle)
                .method(method)
                .status(ExecutionStatus.COMPLETED)
                .drawnNumber(result.drawnNumber())
                .eligibleNumbersCount(result.eligibleCount())
                .eligibleNumbersSnapshot(result.eligibleSnapshot())
                .executedByUserId(executedBy)
                .executedAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .notes(result.evidenceJson())
                .build();

        executionRepository.save(execution);

        raffle.setWinnerNumber(result.drawnNumber());
        raffle.setExecutedAt(LocalDateTime.now());
        raffle.setOperationalStatus(OperationalStatus.FINISHED);

        WinnerContact winnerContact = resolveWinnerReservation(raffle, result.drawnNumber());
        closeLosingReservations(raffle);

        raffleRepository.save(raffle);
        publishProgressSnapshot(raffle);

        eventPublisher.publishDrawCompleted(
                raffle.getId(),
                result.drawnNumber(),
                winnerContact != null ? winnerContact.name() : null,
                winnerContact != null ? winnerContact.phone() : null
        );
        auditService.log("DRAW_COMPLETED", "Raffle", raffleId, null,
                "winner=" + result.drawnNumber() + " method=" + method);

        return execution;
    }

    private void publishLiveCountdown(UUID raffleId) {
        for (long seconds = 5; seconds >= 1; seconds--) {
            eventPublisher.publishCountdown(raffleId, seconds);
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException("La ejecucion del sorteo fue interrumpida");
            }
        }
    }

    private List<Integer> buildEligibleNumbers(Raffle raffle) {
        List<Integer> eligible = raffleNumberRepository.findByRaffleOrderByNumberAsc(raffle).stream()
                .filter(n -> n.getStatus() == NumberStatus.RESERVED)
                .map(RaffleNumber::getNumber)
                .toList();

        if (eligible.isEmpty()) {
            throw new BusinessException("La rifa debe tener al menos un numero reservado antes de ejecutar el sorteo");
        }
        return eligible;
    }

    private WinnerContact resolveWinnerReservation(Raffle raffle, int winnerNumber) {
        return raffleNumberRepository.findByRaffleOrderByNumberAsc(raffle).stream()
                .filter(n -> n.getNumber() == winnerNumber && n.getReservation() != null)
                .findFirst()
                .map(n -> {
                    n.setStatus(NumberStatus.WINNER);
                    Reservation res = n.getReservation();
                    raffle.setWinnerReservationId(res.getId());
                    res.setStatus(ReservationStatus.CONFIRMED);
                    res.setExpiresAt(null);
                    return res.getParticipant() != null
                            ? new WinnerContact(res.getParticipant().getFullName(), res.getParticipant().getPhone())
                            : null;
                })
                .orElse(null);
    }

    private void closeLosingReservations(Raffle raffle) {
        UUID winnerReservationId = raffle.getWinnerReservationId();
        List<Reservation> reservations = reservationRepository.findByRaffleId(raffle.getId());
        for (Reservation reservation : reservations) {
            if (winnerReservationId != null && winnerReservationId.equals(reservation.getId())) {
                continue;
            }
            if (reservation.getStatus() == ReservationStatus.PENDING) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setExpiresAt(null);
            }
        }
        reservationRepository.saveAll(reservations);

        List<RaffleNumber> numbers = raffleNumberRepository.findByRaffleOrderByNumberAsc(raffle);
        for (RaffleNumber number : numbers) {
            if (number.getReservation() == null) {
                continue;
            }
            if (winnerReservationId != null && winnerReservationId.equals(number.getReservation().getId())) {
                continue;
            }
            if (number.getStatus() == NumberStatus.RESERVED || number.getStatus() == NumberStatus.PENDING_PAYMENT) {
                number.setStatus(NumberStatus.CANCELLED);
                number.setExpiresAt(null);
            }
        }
        raffleNumberRepository.saveAll(numbers);
    }

    private void publishProgressSnapshot(Raffle raffle) {
        long available = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.AVAILABLE);
        long reserved = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.RESERVED);
        long paid = raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.PAID)
                + raffleNumberRepository.countByRaffleAndStatus(raffle, NumberStatus.WINNER);
        eventPublisher.publishNumbersUpdated(raffle.getId(), (int) available, (int) reserved, (int) paid);
    }

    private record WinnerContact(String name, String phone) {}

    private DrawStrategy resolveStrategy(DrawMethod method) {
        return switch (method) {
            case MANUAL -> manualStrategy;
            case AUTOMATIC -> automaticStrategy;
            case EXTERNAL -> externalStrategy;
        };
    }
}
