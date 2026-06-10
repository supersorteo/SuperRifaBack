package com.rifas.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter @Setter
public class AppProperties {

    private String baseUrl = "http://localhost:4200";
    private Cors cors = new Cors();

    @Getter @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:4200";
    }
}
