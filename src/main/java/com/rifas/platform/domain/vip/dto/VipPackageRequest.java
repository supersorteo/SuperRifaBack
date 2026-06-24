package com.rifas.platform.domain.vip.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VipPackageRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) Integer raffleQuantity,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @Size(max = 10) String currency,
        Integer displayOrder
) {}
