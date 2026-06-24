package com.rifas.platform.domain.vip.dto;

import java.util.UUID;

public record VipPreferenceResponse(UUID purchaseId, String preferenceId, String initPoint) {}
