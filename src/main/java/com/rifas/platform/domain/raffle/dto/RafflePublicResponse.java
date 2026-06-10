package com.rifas.platform.domain.raffle.dto;

import com.rifas.platform.shared.enums.NumberStatus;
import com.rifas.platform.shared.enums.OperationalStatus;
import com.rifas.platform.shared.enums.PublicationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RafflePublicResponse(
        UUID id,
        String title,
        String slug,
        String description,
        PrizeInfo prize,
        List<ImageInfo> images,
        BigDecimal pricePerNumber,
        Integer totalNumbers,
        int availableNumbers,
        int reservedNumbers,
        int soldNumbers,
        LocalDateTime drawDateTime,
        String timezone,
        OperationalStatus operationalStatus,
        PublicationStatus publicationStatus,
        OrganizerPublicInfo organizer,
        Integer winnerNumber,
        String winnerName,
        LocalDateTime executedAt,
        List<PaymentMethodPublicInfo> paymentMethods
) {
    public record PrizeInfo(String name, String description, BigDecimal estimatedValue, String imageUrl) {}
    public record ImageInfo(String url, String altText, boolean coverImage, Integer displayOrder) {}
    public record OrganizerPublicInfo(String displayName, String avatarUrl, String whatsappNumber) {}
    public record PaymentMethodPublicInfo(String type, String displayName, String alias,
                                          String cbu, String accountHolder, String instructions) {}
    public record NumberInfo(Integer number, NumberStatus status) {}
}
