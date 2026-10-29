package com.parceltracker.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final LocationWebSocketHandler handler;
    private final String[] allowedOrigins;

    public WebSocketConfig(LocationWebSocketHandler handler,
                           @Value("${parcel.security.allowed-origins:http://localhost:*,http://127.0.0.1:*}") String[] allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/tracking/{parcelId}").setAllowedOriginPatterns(allowedOrigins);
    }
}
