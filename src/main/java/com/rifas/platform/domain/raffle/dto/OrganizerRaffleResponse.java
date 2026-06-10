package com.rifas.platform.domain.raffle.dto;

import com.rifas.platform.shared.enums.OperationalStatus;
import com.rifas.platform.shared.enums.PublicationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrganizerRaffleResponse(
        UUID id,
        String title,
        String slug,
        String prizeName,
        long participantCount,
        PublicationStatus publicationStatus,
        OperationalStatus operationalStatus,
        Integer totalNumbers,
        BigDecimal pricePerNumber,
        LocalDateTime drawDateTime,
        LocalDateTime createdAt,
        List<ImageInfo> images
) {
    public record ImageInfo(
            String url,
            String altText,
            boolean coverImage,
            Integer displayOrder
    ) {}
}
