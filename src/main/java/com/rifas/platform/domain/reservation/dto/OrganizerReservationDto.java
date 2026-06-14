package com.rifas.platform.domain.reservation.dto;

import com.rifas.platform.shared.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrganizerReservationDto(
        UUID id,
        String participantName,
        String participantPhone,
        String participantEmail,
        String participantDni,
        List<Integer> numbers,
        BigDecimal totalAmount,
        ReservationStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        UUID raffleId,
        String raffleTitle,
        String raffleSlug
) {}
