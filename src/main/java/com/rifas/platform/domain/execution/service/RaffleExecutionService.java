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
                || raffle.getOperationalStatus() == OperationalStatus.CANCELLED) {
            throw new BusinessException("La rifa ya finalizó");
        }
        if (executionRepository.existsByRaffle(raffle)) {
            throw new BusinessException("La rifa ya tiene un sorteo registrado");
        }

        List<Integer> eligible = buildEligibleNumbers(raffle);

        raffle.setOperationalStatus(OperationalStatus.EXECUTING);
        raffleRepository.save(raffle);
        eventPublisher.publishDrawStarted(raffle.getId());

        DrawStrategy strategy = resolveStrategy(raffle.getDrawMethod());
        DrawResult result;
        try {
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

        resolveWinnerReservation(raffle, result.drawnNumber());

        raffleRepository.save(raffle);

        eventPublisher.publishDrawCompleted(raffle.getId(), result.drawnNumber(), null);
        auditService.log("DRAW_COMPLETED", "Raffle", raffleId, null,
                "winner=" + result.drawnNumber() + " method=" + method);

        return execution;
    }

    private List<Integer> buildEligibleNumbers(Raffle raffle) {
        List<NumberStatus> eligibleStatuses = raffle.getDrawPolicy() == DrawPolicy.PAID_ONLY
                ? List.of(NumberStatus.PAID)
                : List.of(NumberStatus.PAID, NumberStatus.RESERVED, NumberStatus.PENDING_PAYMENT);

        List<RaffleNumber> numbers = raffleNumberRepository.findByRaffleOrderByNumberAsc(raffle);
        List<Integer> eligible = numbers.stream()
                .filter(n -> eligibleStatuses.contains(n.getStatus()))
                .map(RaffleNumber::getNumber)
                .toList();

        if (eligible.isEmpty()) {
            throw new BusinessException("No hay números elegibles para el sorteo según la política configurada");
        }
        return eligible;
    }

    private void resolveWinnerReservation(Raffle raffle, int winnerNumber) {
        raffleNumberRepository.findByRaffleOrderByNumberAsc(raffle).stream()
                .filter(n -> n.getNumber() == winnerNumber && n.getReservation() != null)
                .findFirst()
                .ifPresent(n -> {
                    Reservation res = n.getReservation();
                    raffle.setWinnerReservationId(res.getId());
                });
    }

    private DrawStrategy resolveStrategy(DrawMethod method) {
        return switch (method) {
            case MANUAL    -> manualStrategy;
            case AUTOMATIC -> automaticStrategy;
            case EXTERNAL  -> externalStrategy;
        };
    }
}
