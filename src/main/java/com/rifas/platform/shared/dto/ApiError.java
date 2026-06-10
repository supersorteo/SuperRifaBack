package com.rifas.platform.shared.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final String code;
    private final String message;
    private final List<FieldError> errors;

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
        this.errors = null;
        this.timestamp = LocalDateTime.now();
    }

    @Getter
    @AllArgsConstructor
    public static class FieldError {
        private final String field;
        private final String message;
    }
}
