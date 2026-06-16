package com.rifas.platform.domain.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
        @NotBlank String raffleSlug,
        @NotEmpty @Size(min = 1, max = 10) List<@Positive Integer> numbers,
        @NotNull @Valid ParticipantDataRequest participant,
        @NotBlank @Size(max = 30) String accessCode,
        UUID paymentMethodId
) {
    public record ParticipantDataRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Email String email,
            @NotBlank @Size(max = 30) String phone,
            String dni
    ) {}
}
