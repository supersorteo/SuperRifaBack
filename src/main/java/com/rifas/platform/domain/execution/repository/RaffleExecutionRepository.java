package com.rifas.platform.domain.execution.repository;

import com.rifas.platform.domain.execution.entity.RaffleExecution;
import com.rifas.platform.domain.raffle.entity.Raffle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RaffleExecutionRepository extends JpaRepository<RaffleExecution, UUID> {
    Optional<RaffleExecution> findByRaffle(Raffle raffle);
    boolean existsByRaffle(Raffle raffle);
}
