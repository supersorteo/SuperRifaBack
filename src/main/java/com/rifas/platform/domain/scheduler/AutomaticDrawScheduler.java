package com.rifas.platform.domain.scheduler;

import com.rifas.platform.domain.execution.service.RaffleExecutionService;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.raffle.repository.RaffleRepository;
import com.rifas.platform.shared.enums.PublicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutomaticDrawScheduler {

    private final RaffleRepository raffleRepository;
    private final RaffleExecutionService executionService;

    @Scheduled(fixedDelayString = "${scheduler.draw-check-ms:10000}")
    public void checkAndExecuteAutomaticDraws() {
        // Ampliar ventana +26h para cubrir todos los timezones del mundo (UTC-12 a UTC+14)
        // y filtrar con precisión de timezone en Java
        List<Raffle> candidates = raffleRepository.findRafflesReadyForScheduledDraw(
                PublicationStatus.PUBLISHED,
                LocalDateTime.now(ZoneOffset.UTC).plusHours(26)
        );

        Instant nowInstant = Instant.now();

        for (Raffle raffle : candidates) {
            if (!isDueNow(raffle, nowInstant)) continue;
            try {
                log.info("Auto-draw starting for raffle: {} ({})", raffle.getTitle(), raffle.getId());
                executionService.executeAutomaticDraw(raffle.getId());
            } catch (Exception ex) {
                log.error("Auto-draw failed for raffle {}: {}", raffle.getId(), ex.getMessage());
            }
        }
    }

    private boolean isDueNow(Raffle raffle, Instant nowInstant) {
        if (raffle.getDrawDateTime() == null) return false;
        String tz = raffle.getTimezone() != null ? raffle.getTimezone() : "UTC";
        try {
            Instant drawInstant = raffle.getDrawDateTime().atZone(ZoneId.of(tz)).toInstant();
            return !drawInstant.isAfter(nowInstant);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}' for raffle {}, skipping timezone adjustment", tz, raffle.getId());
            // Fallback: comparar sin ajuste de timezone si el tz es inválido
            return !raffle.getDrawDateTime().toInstant(ZoneOffset.UTC).isAfter(nowInstant);
        }
    }
}
