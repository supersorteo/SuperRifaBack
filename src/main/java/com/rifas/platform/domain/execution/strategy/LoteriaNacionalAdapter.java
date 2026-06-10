package com.rifas.platform.domain.execution.strategy;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stub para integración futura con Lotería Nacional Argentina.
 * Activar con @Profile("loteria") cuando se implemente la API real.
 */
@Component
@Profile("loteria")
public class LoteriaNacionalAdapter implements ExternalDrawProvider {

    @Override
    public String providerName() {
        return "LOTERIA_NACIONAL";
    }

    @Override
    public ExternalDrawResult requestDraw(String raffleId, List<Integer> eligibleNumbers, String config) {
        throw new UnsupportedOperationException("Lotería Nacional: integración pendiente de implementar");
    }

    @Override
    public ExternalDrawResult queryResult(String externalExecutionId) {
        throw new UnsupportedOperationException("Lotería Nacional: integración pendiente de implementar");
    }
}
