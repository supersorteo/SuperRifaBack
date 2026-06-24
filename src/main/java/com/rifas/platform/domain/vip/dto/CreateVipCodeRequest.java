package com.rifas.platform.domain.vip.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVipCodeRequest(
        @NotNull UUID packageId,
        UUID organizerId       // null = código genérico sin pre-asignar
) {}
