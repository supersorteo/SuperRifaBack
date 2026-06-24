package com.rifas.platform.domain.vip.dto;

public record OrganizerQuotaSummaryDto(
        String planName,
        int freeGranted,
        int freeConsumed,
        int vipPurchased,
        int vipConsumed,
        int availableRaffles,
        boolean isVip,
        boolean canCreate
) {}
