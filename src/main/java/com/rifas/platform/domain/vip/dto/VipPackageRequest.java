package com.rifas.platform.domain.vip.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VipPackageRequest(
        @NotBlank(message = "El nombre del paquete es obligatorio")
        @Size(max = 100, message = "El nombre del paquete no puede superar los 100 caracteres")
        String name,
        @NotNull(message = "La cantidad de rifas es obligatoria")
        @Min(value = 1, message = "La cantidad de rifas debe ser mayor que cero")
        Integer raffleQuantity,
        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio debe ser mayor que cero")
        BigDecimal price,
        @Size(max = 10, message = "La moneda no puede superar los 10 caracteres")
        String currency,
        @Min(value = 0, message = "El orden no puede ser negativo")
        Integer displayOrder
) {}
