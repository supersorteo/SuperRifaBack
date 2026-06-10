package com.rifas.platform.domain.execution.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rifas.platform.domain.raffle.entity.Raffle;
import com.rifas.platform.shared.enums.DrawMethod;
import com.rifas.platform.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExternalDrawStrategy implements DrawStrategy {

    private final List<ExternalDrawProvider> providers;
    private final ObjectMapper mapper;

    @Override
    @SneakyThrows
    public DrawResult execute(Raffle raffle, List<Integer> eligibleNumbers, UUID executedByUserId) {
        String config = raffle.getExternalProviderConfig();
        ExternalDrawProvider provider = providers.stream().findFirst()
                .orElseThrow(() -> new BusinessException("No hay proveedor externo configurado"));

        ExternalDrawProvider.ExternalDrawResult result =
                provider.requestDraw(raffle.getId().toString(), eligibleNumbers, config);

        if (!result.completed() || result.drawnNumber() == null) {
            throw new BusinessException("El proveedor externo no completó el sorteo");
        }

        String snapshot = mapper.writeValueAsString(eligibleNumbers);
        String evidence = mapper.writeValueAsString(Map.of(
                "method", DrawMethod.EXTERNAL.name(),
                "provider", provider.providerName(),
                "externalId", result.externalExecutionId(),
                "raw", result.rawResponse()
        ));
        return new DrawResult(result.drawnNumber(), eligibleNumbers.size(), snapshot,
                provider.providerName(), evidence);
    }
}
