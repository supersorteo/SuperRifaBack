package com.rifas.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RifasPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(RifasPlatformApplication.class, args);
    }
}
