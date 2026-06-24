package com.rifas.platform.domain.vip.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VipPurchaseDto(
        UUID id,
        String packageName,
        int raffleQuantity,
        BigDecimal amount,
        String currency,
        String status,
        boolean credited,
        LocalDateTime creditedAt,
        LocalDateTime createdAt,
        String organizerEmail,
        String organizerName
) {}
