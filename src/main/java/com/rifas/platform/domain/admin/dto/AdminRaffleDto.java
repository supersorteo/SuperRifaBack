package com.rifas.platform.domain.admin.dto;

import com.rifas.platform.domain.raffle.entity.Raffle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminRaffleDto(
        UUID id,
        String title,
        String slug,
        String publicationStatus,
        String operationalStatus,
        int totalNumbers,
        BigDecimal pricePerNumber,
        LocalDateTime drawDateTime,
        LocalDateTime createdAt,
        long participantCount,
        String firstImageUrl
) {
    public static AdminRaffleDto from(Raffle r, long participantCount) {
        String imgUrl = r.getImages().isEmpty() ? null : r.getImages().get(0).getUrl();
        return new AdminRaffleDto(
                r.getId(),
                r.getTitle(),
                r.getSlug(),
                r.getPublicationStatus().name(),
                r.getOperationalStatus().name(),
                r.getTotalNumbers(),
                r.getPricePerNumber(),
                r.getDrawDateTime(),
                r.getCreatedAt(),
                participantCount,
                imgUrl
        );
    }
}
