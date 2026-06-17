package com.rifas.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mercadopago")
@Getter @Setter
public class MercadoPagoProperties {
    /** Token sandbox de la plataforma — fallback cuando el organizer no configuró su método MP */
    private String accessToken;
    private String webhookSecret;
    private boolean sandbox = true;
}
