package com.rifas.platform.domain.vip.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemVipCodeRequest(
        @NotBlank String code
) {}
