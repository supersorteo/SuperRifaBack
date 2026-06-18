package com.rifas.platform.domain.notification.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@SuppressWarnings("NullableProblems")
public class RaffleEventPublisher {

    private final SimpMessagingTemplate messaging;

    public void publishNumbersReserved(UUID raffleId, List<Integer> numbers) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/numbers",
                Map.of("raffleId", raffleId, "reservedNumbers", numbers, "ts", Instant.now())
        );
    }

    public void publishDrawStarted(UUID raffleId) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/status",
                Map.of("raffleId", raffleId, "status", "EXECUTING", "ts", Instant.now())
        );
    }

    public void publishDrawCompleted(UUID raffleId, Integer winnerNumber, String winnerName, String winnerPhone) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/result",
                Map.of("raffleId", raffleId, "winnerNumber", winnerNumber,
                        "winnerName", winnerName != null ? winnerName : "",
                        "winnerPhone", winnerPhone != null ? winnerPhone : "",
                        "ts", Instant.now())
        );
    }

    public void publishDrawFailed(UUID raffleId) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/status",
                Map.of("raffleId", raffleId, "status", "FAILED", "ts", Instant.now())
        );
    }

    public void publishCountdown(UUID raffleId, long secondsRemaining) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/countdown",
                Map.of("raffleId", raffleId, "secondsRemaining", secondsRemaining, "ts", Instant.now())
        );
    }

    public void publishNumbersUpdated(UUID raffleId, int available, int reserved, int paid) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/progress",
                Map.of("raffleId", raffleId, "available", available,
                        "reserved", reserved, "paid", paid, "ts", Instant.now())
        );
    }

    public void publishNewReservation(UUID raffleId, String raffleTitle,
                                       String participantName, List<Integer> numbers,
                                       BigDecimal amount) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/new-reservation",
                Map.of("raffleId", raffleId, "raffleTitle", raffleTitle,
                        "participantName", participantName,
                        "numbers", numbers, "amount", amount, "ts", Instant.now())
        );
    }

    public void publishReservationConfirmed(UUID raffleId, String raffleTitle,
                                             String participantName, List<Integer> numbers,
                                             BigDecimal amount) {
        messaging.convertAndSend(
                "/topic/raffle/" + raffleId + "/reservation-confirmed",
                Map.of("raffleId", raffleId, "raffleTitle", raffleTitle,
                        "participantName", participantName,
                        "numbers", numbers, "amount", amount, "ts", Instant.now())
        );
    }
}
