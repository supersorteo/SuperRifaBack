package com.rifas.platform.domain.reservation.dto;

import com.rifas.platform.shared.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationCreatedDto(
        UUID id,
        ReservationStatus status,
        BigDecimal totalAmount,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {}
