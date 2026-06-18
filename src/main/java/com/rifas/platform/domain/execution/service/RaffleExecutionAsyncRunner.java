package com.rifas.platform.domain.execution.service;

import com.rifas.platform.shared.enums.DrawMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RaffleExecutionAsyncRunner {

    private final TaskExecutor raffleDrawExecutor;
    private final RaffleExecutionService executionService;

    public void run(UUID raffleId, DrawMethod method, UUID executedBy) {
        raffleDrawExecutor.execute(() -> executionService.completeQueuedDraw(raffleId, method, executedBy));
    }
}
