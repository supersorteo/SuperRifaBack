package com.rifas.platform.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scheduler")
@Getter @Setter
public class SchedulerProperties {
    private long drawCheckMs    = 60_000L;
    private long expiryCheckMs  = 30_000L;
}
