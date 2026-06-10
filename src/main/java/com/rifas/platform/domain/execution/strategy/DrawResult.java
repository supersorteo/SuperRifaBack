package com.rifas.platform.domain.execution.strategy;

public record DrawResult(
        int drawnNumber,
        int eligibleCount,
        String eligibleSnapshot,
        String providerName,
        String evidenceJson
) {}
