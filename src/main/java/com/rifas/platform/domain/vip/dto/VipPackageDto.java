package com.rifas.platform.domain.vip.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VipPackageDto(
        UUID id,
        String name,
        int raffleQuantity,
        BigDecimal price,
        String currency,
        int displayOrder,
        boolean active
) {}
