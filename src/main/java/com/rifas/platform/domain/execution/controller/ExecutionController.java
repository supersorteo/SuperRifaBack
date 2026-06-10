package com.rifas.platform.domain.execution.controller;

import com.rifas.platform.domain.execution.entity.RaffleExecution;
import com.rifas.platform.domain.execution.service.RaffleExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizer/raffles/{raffleId}/draw")
@RequiredArgsConstructor
public class ExecutionController {

    private final RaffleExecutionService executionService;

    @PostMapping("/execute")
    @PreAuthorize("hasAnyRole('ORGANIZER','ADMIN')")
    public ResponseEntity<RaffleExecution> executeManual(@PathVariable UUID raffleId) {
        return ResponseEntity.ok(executionService.executeManualDraw(raffleId));
    }
}
