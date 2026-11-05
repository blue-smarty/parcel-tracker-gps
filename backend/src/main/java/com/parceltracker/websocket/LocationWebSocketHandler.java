package com.parceltracker.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.LocationService;
import com.parceltracker.service.ParcelService;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Clients can subscribe to owned or assigned parcels; only admins can subscribe to every parcel.
 */
@Component
public class LocationWebSocketHandler extends TextWebSocketHandler {
    public static final String ALL = "all";
    private static final Logger log = LoggerFactory.getLogger(LocationWebSocketHandler.class);

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final LocationService locationService;
    private final ParcelService parcelService;

    public LocationWebSocketHandler(ObjectMapper mapper, LocationService locationService, ParcelService parcelService) {
        this.mapper = mapper;
        this.locationService = locationService;
        this.parcelService = parcelService;
    }

    @PostConstruct
    void register() {
        locationService.addListener(this::broadcast);
        locationService.addStatusListener(this::broadcastStatus);
    }

    static String parcelIdOf(WebSocketSession session) {
        if (session.getUri() == null) return ALL;
        String path = session.getUri().getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (!isAuthorized(session)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
        session.getAttributes().put("safe", safe);
        sessions.computeIfAbsent(parcelIdOf(session), k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    private boolean isAuthorized(WebSocketSession session) {
        if (!(session.getPrincipal() instanceof Authentication authentication) || !authentication.isAuthenticated()) {
            return false;
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        String parcelId = parcelIdOf(session);
        if (ALL.equals(parcelId)) return admin;
        if (admin) return true;
        try {
            Parcel parcel = parcelService.get(parcelId);
            boolean customer = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
            if (customer) {
                return authentication.getName().equalsIgnoreCase(String.valueOf(parcel.getCustomerEmail()));
            }
            boolean courier = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_COURIER".equals(authority.getAuthority()));
            return courier && authentication.getName().equals(parcel.getCourierId());
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Set<WebSocketSession> set = sessions.get(parcelIdOf(session));
        if (set != null) set.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    public void broadcast(Parcel parcel, Location location) {
        sendUpdate(parcel, location);
    }

    private void broadcastStatus(Parcel parcel) {
        sendUpdate(parcel, null);
    }

    private void sendUpdate(Parcel parcel, Location location) {
        String payload;
        try {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("parcelId", parcel.getId());
            update.put("trackingNumber", String.valueOf(parcel.getTrackingNumber()));
            update.put("status", String.valueOf(parcel.getStatus()));
            update.put("location", location);
            payload = mapper.writeValueAsString(update);
        } catch (JsonProcessingException e) {
            log.error("Cannot serialize location update", e);
            return;
        }
        send(parcel.getId(), payload);
        send(ALL, payload);
    }

    private void send(String key, String payload) {
        Set<WebSocketSession> set = sessions.get(key);
        if (set == null) return;
        for (WebSocketSession s : set) {
            try {
                WebSocketSession safe = (WebSocketSession) s.getAttributes().getOrDefault("safe", s);
                if (s.isOpen()) safe.sendMessage(new TextMessage(payload));
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to send to session {}", s.getId(), e);
                set.remove(s);
            }
        }
    }
}
