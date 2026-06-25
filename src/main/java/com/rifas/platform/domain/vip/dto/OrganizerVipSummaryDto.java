package com.rifas.platform.domain.vip.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrganizerVipSummaryDto(
        String planName,
        int availableRaffles,
        int freeGranted,
        int freeConsumed,
        int vipPurchased,
        int vipConsumed,
        boolean isVip,
        int totalPurchases,
        int approvedPurchases,
        BigDecimal totalSpent,
        int codesRedeemed,
        List<VipPurchaseDto> recentPurchases
) {}
