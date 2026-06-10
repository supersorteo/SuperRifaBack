package com.rifas.platform.domain.execution.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.shared.enums.DrawMethod;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ManualDrawStrategy implements DrawStrategy {

    private static final SecureRandom RNG = new SecureRandom();
    private final ObjectMapper mapper;

    @Override
    @SneakyThrows
    public DrawResult execute(Raffle raffle, List<Integer> eligibleNumbers, UUID executedByUserId) {
        if (eligibleNumbers.isEmpty()) {
            throw new IllegalStateException("No hay números elegibles para el sorteo");
        }
        int drawn = eligibleNumbers.get(RNG.nextInt(eligibleNumbers.size()));
        String snapshot = mapper.writeValueAsString(eligibleNumbers);
        String evidence = mapper.writeValueAsString(Map.of(
                "method", DrawMethod.MANUAL.name(),
                "drawnBy", executedByUserId.toString(),
                "totalEligible", eligibleNumbers.size()
        ));
        return new DrawResult(drawn, eligibleNumbers.size(), snapshot, "MANUAL", evidence);
    }
}
