package com.rifas.platform.domain.execution.strategy;

import com.rifas.platform.domain.raffle.entity.Raffle;

import java.util.List;
import java.util.UUID;

public interface DrawStrategy {
    DrawResult execute(Raffle raffle, List<Integer> eligibleNumbers, UUID executedByUserId);
}
