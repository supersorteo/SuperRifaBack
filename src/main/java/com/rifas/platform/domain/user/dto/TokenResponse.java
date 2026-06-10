package com.rifas.platform.domain.user.dto;

import java.util.Set;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String email,
        String fullName,
        Set<String> roles
) {
    public static TokenResponse of(String access, String refresh, long expiresIn,
                                   String email, String fullName, Set<String> roles) {
        return new TokenResponse(access, refresh, "Bearer", expiresIn, email, fullName, roles);
    }
}
