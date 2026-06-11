package com.rifas.platform.domain.admin.dto;

import com.rifas.platform.domain.organizer.entity.OrganizerProfile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AdminOrganizerDetailDto(
        UUID id,
        String email,
        String fullName,
        String phone,
        String businessName,
        String bio,
        String instagramHandle,
        String whatsappNumber,
        boolean active,
        LocalDateTime createdAt,
        long totalParticipants,
        List<AdminRaffleDto> raffles
) {
    public static AdminOrganizerDetailDto from(OrganizerProfile p,
                                               List<AdminRaffleDto> raffles) {
        long totalPart = raffles.stream().mapToLong(AdminRaffleDto::participantCount).sum();
        return new AdminOrganizerDetailDto(
                p.getId(),
                p.getUser().getEmail(),
                p.getUser().getFullName(),
                p.getPhone(),
                p.getBusinessName(),
                p.getBio(),
                p.getInstagramHandle(),
                p.getWhatsappNumber(),
                p.getUser().isActive(),
                p.getCreatedAt(),
                totalPart,
                raffles
        );
    }
}
