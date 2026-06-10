package com.rifas.platform.domain.raffle.dto;

import com.rifas.platform.shared.enums.DrawMethod;
import com.rifas.platform.shared.enums.DrawPolicy;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateRaffleRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull @Min(2) @Max(10000) Integer totalNumbers,
        @NotNull @Min(0) Integer rangeStart,
        @NotNull @Min(1) Integer rangeEnd,
        @NotNull @DecimalMin("0.01") BigDecimal pricePerNumber,
        LocalDateTime drawDateTime,
        String timezone,
        DrawMethod drawMethod,
        DrawPolicy drawPolicy,
        String termsAndConditions,
        String prizeName,
        String prizeDescription,
        BigDecimal prizeEstimatedValue
) {}
