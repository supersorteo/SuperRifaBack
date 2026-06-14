package com.rifas.platform.domain.reservation.dto;

import com.rifas.platform.shared.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ParticipantLookupDto(
        String participantName,
        String raffleTitle,
        String raffleSlug,
        List<ReservationSummary> reservations
) {
    public record ReservationSummary(
            UUID id,
            List<Integer> numbers,
            BigDecimal totalAmount,
            ReservationStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {}
}
