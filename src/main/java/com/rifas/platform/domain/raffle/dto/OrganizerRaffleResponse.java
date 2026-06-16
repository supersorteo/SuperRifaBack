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
        String reservationAccessCode,
        String prizeName,
        long participantCount,
        long reservedCount,
        PublicationStatus publicationStatus,
        OperationalStatus operationalStatus,
        Integer winnerNumber,
        String winnerName,
        String winnerPhone,
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
