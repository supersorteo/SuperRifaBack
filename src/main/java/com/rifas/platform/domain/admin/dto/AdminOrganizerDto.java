package com.rifas.platform.domain.admin.dto;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminOrganizerDto(
        UUID id,
        String email,
        String fullName,
        String phone,
        String businessName,
        boolean active,
        LocalDateTime createdAt,
        long raffleCount
) {
    public static AdminOrganizerDto from(OrganizerProfile p, long raffleCount) {
        return new AdminOrganizerDto(
                p.getId(),
                p.getUser().getEmail(),
                p.getUser().getFullName(),
                p.getPhone(),
                p.getBusinessName(),
                p.getUser().isActive(),
                p.getCreatedAt(),
                raffleCount
        );
    }
}
