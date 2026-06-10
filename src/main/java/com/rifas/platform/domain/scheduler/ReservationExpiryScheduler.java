package com.rifas.platform.domain.scheduler;

import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.shared.enums.NumberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {

    private final RaffleNumberRepository raffleNumberRepository;

    @Scheduled(fixedDelayString = "${scheduler.expiry-check-ms:30000}")
    @Transactional
    public void expireStaleReservations() {
        int expired = raffleNumberRepository.expireNumbersWithExpiredReservations(
                LocalDateTime.now(), NumberStatus.RESERVED);
        if (expired > 0) {
            log.info("Expired {} reserved numbers back to AVAILABLE", expired);
        }
    }
}
