package com.rifas.platform.domain.vip.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VipCodeResponse(
        UUID id,
        String code,
        String packageName,
        int raffleQuantity,
        BigDecimal price,
        String source,
        String status,
        String assignedOrganizerEmail,
        String assignedOrganizerName,
        String redeemedByEmail,
        String redeemedByName,
        LocalDateTime redeemedAt,
        LocalDateTime createdAt
) {}
