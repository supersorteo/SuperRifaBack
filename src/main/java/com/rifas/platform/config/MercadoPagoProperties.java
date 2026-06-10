package com.rifas.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mercadopago")
@Getter @Setter
public class MercadoPagoProperties {
    private String accessToken;
    private String webhookSecret;
    private boolean sandbox = true;
}
