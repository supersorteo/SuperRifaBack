package com.rifas.platform;

import com.rifas.platform.config.AdminSeedProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AdminSeedProperties.class)
public class RifasPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(RifasPlatformApplication.class, args);
    }
}
