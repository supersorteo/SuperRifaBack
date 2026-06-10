package com.rifas.platform.domain.raffle.repository;

import com.rifas.platform.domain.raffle.entity.RaffleImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RaffleImageRepository extends JpaRepository<RaffleImage, UUID> {
    List<RaffleImage> findByRaffleIdOrderByDisplayOrder(UUID raffleId);
    long countByRaffleId(UUID raffleId);
}
