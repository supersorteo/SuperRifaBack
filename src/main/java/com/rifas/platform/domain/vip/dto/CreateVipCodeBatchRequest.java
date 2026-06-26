package com.rifas.platform.domain.vip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVipCodeBatchRequest(
        @NotNull UUID packageId,
        UUID organizerId,
        @NotNull @Min(1) @Max(500) Integer quantity
) {}
