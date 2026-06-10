package com.rifas.platform.domain.scheduler;

import com.rifas.platform.domain.execution.service.RaffleExecutionService;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.raffle.repository.RaffleRepository;
import com.rifas.platform.shared.enums.DrawMethod;
import com.rifas.platform.shared.enums.OperationalStatus;
import com.rifas.platform.shared.enums.PublicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutomaticDrawScheduler {

    private final RaffleRepository raffleRepository;
    private final RaffleExecutionService executionService;

    @Scheduled(fixedDelayString = "${scheduler.draw-check-ms:60000}")
    public void checkAndExecuteAutomaticDraws() {
        List<Raffle> ready = raffleRepository.findRafflesReadyForAutomaticDraw(
                DrawMethod.AUTOMATIC,
                PublicationStatus.PUBLISHED,
                OperationalStatus.ACTIVE,
                LocalDateTime.now()
        );

        for (Raffle raffle : ready) {
            try {
                log.info("Auto-draw starting for raffle: {} ({})", raffle.getTitle(), raffle.getId());
                executionService.executeAutomaticDraw(raffle.getId());
            } catch (Exception ex) {
                log.error("Auto-draw failed for raffle {}: {}", raffle.getId(), ex.getMessage());
            }
        }
    }
}
