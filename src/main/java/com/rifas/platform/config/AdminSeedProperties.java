package com.rifas.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin.seed")
public record AdminSeedProperties(AdminUser user1, AdminUser user2) {
    public record AdminUser(String email, String name, String password) {}
}
