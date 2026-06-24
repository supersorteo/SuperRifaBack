package com.rifas.platform.domain.vip.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVipPreferenceRequest(@NotNull UUID vipPackageId) {}
