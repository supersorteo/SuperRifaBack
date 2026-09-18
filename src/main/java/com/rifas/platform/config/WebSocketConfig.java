package com.rifas.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    @SuppressWarnings("NullableProblems")
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Fix #4A: server heartbeat keeps Railway proxy alive; TaskScheduler required by SimpleBroker
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        config.enableSimpleBroker("/topic", "/queue")
              .setHeartbeatValue(new long[]{10000, 10000})
              .setTaskScheduler(scheduler);
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint — used by the frontend (no SockJS import needed)
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*");
        // SockJS fallback kept for backward compatibility
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setHeartbeatTime(10000)
                .setDisconnectDelay(30000);
    }
}
