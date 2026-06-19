package com.edushift.infrastructure.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket config (Sprint 10 / BE-10.4, ADR-10.3).
 *
 * <p>One endpoint: {@code /ws/notify} (with SockJS fallback).
 * Auth is via the {@code Authorization} header in the STOMP
 * CONNECT frame, validated by the same JWT filter that protects
 * the REST API. The client MUST send the bearer token on connect;
 * the handshake rejects unauthenticated connections.</p>
 *
 * <h3>Topic naming (matches {@code RealtimeService})</h3>
 * <ul>
 *   <li>{@code /topic/tenant/{tenantId}} — tenant-wide</li>
 *   <li>{@code /topic/tenant/{tenantId}/user/{userId}} — per-user</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class RealtimeConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker. For multi-instance prod, switch
        // to RabbitMQ / Redis pubsub — but for MVP the JVM broker
        // is enough.
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notify")
                .setAllowedOriginPatterns("*") // in prod, restrict to the FE origin
                .withSockJS(); // graceful fallback for old browsers / corporate proxies
        // Native WS (no SockJS) for clients that prefer it.
        registry.addEndpoint("/ws/notify")
                .setAllowedOriginPatterns("*");
    }
}
