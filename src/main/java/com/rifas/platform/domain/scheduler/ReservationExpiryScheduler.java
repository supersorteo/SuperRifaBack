package com.rifas.platform.domain.scheduler;

import com.rifas.platform.domain.raffle.repository.RaffleNumberRepository;
import com.rifas.platform.domain.reservation.repository.ReservationRepository;
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
    private final ReservationRepository reservationRepository;

    @Scheduled(fixedDelayString = "${scheduler.expiry-check-ms:30000}")
    @Transactional
    public void expireStaleReservations() {
        LocalDateTime now = LocalDateTime.now();

        int cancelledReservations = reservationRepository.expirePendingReservations(now);
        int expiredNumbers = raffleNumberRepository.expireNumbersWithExpiredReservations(
                now, NumberStatus.RESERVED);

        if (cancelledReservations > 0 || expiredNumbers > 0) {
            log.info("Expiry job: {} reservations cancelled, {} numbers freed", cancelledReservations, expiredNumbers);
        }
    }
}
