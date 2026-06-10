package com.rifas.platform.domain.raffle.repository;

import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.domain.raffle.entity.RaffleNumber;
import com.rifas.platform.shared.enums.NumberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaffleNumberRepository extends JpaRepository<RaffleNumber, UUID> {

    List<RaffleNumber> findByRaffleOrderByNumberAsc(Raffle raffle);

    List<RaffleNumber> findByRaffleAndStatus(Raffle raffle, NumberStatus status);

    long countByRaffleAndStatus(Raffle raffle, NumberStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM RaffleNumber n WHERE n.raffle = :raffle AND n.number = :number")
    Optional<RaffleNumber> findByRaffleAndNumberWithLock(
            @Param("raffle") Raffle raffle,
            @Param("number") Integer number
    );

    @Modifying
    @Query("""
        UPDATE RaffleNumber n SET n.status = 'AVAILABLE', n.reservation = null,
               n.reservedAt = null, n.expiresAt = null
        WHERE n.status = :status AND n.expiresAt < :now
        """)
    int expireNumbersWithExpiredReservations(
            @Param("now") LocalDateTime now,
            @Param("status") NumberStatus status
    );

    boolean existsByRaffleAndNumber(Raffle raffle, Integer number);
}
