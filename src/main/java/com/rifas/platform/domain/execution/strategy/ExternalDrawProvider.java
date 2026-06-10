package com.rifas.platform.domain.execution.strategy;

import java.util.List;

public interface ExternalDrawProvider {
    String providerName();
    ExternalDrawResult requestDraw(String raffleId, List<Integer> eligibleNumbers, String config);
    ExternalDrawResult queryResult(String externalExecutionId);

    record ExternalDrawResult(
            String externalExecutionId,
            Integer drawnNumber,
            String rawResponse,
            boolean completed
    ) {}
}
