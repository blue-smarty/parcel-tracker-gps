package com.parceltracker.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.LocationService;
import com.parceltracker.service.ParcelService;
import java.net.URI;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class LocationWebSocketHandlerTest {
    private final LocationService locationService = mock(LocationService.class);
    private final ParcelService parcelService = mock(ParcelService.class);
    private final LocationWebSocketHandler handler =
            new LocationWebSocketHandler(new ObjectMapper(), locationService, parcelService);

    @Test
    void rejectsAnonymousAllParcelSubscription() throws Exception {
        WebSocketSession session = session("/ws/tracking/all", null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void rejectsCustomerAllParcelSubscription() throws Exception {
        WebSocketSession session = session("/ws/tracking/all",
                new TestingAuthenticationToken("customer@example.com", "password", "ROLE_CUSTOMER"));

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void permitsCustomerSubscriptionToOwnedParcel() throws Exception {
        Parcel parcel = new Parcel();
        parcel.setCustomerEmail("customer@example.com");
        when(parcelService.get("parcel-1")).thenReturn(parcel);
        WebSocketSession session = session("/ws/tracking/parcel-1",
                new TestingAuthenticationToken("customer@example.com", "password", "ROLE_CUSTOMER"));

        handler.afterConnectionEstablished(session);

        verify(session, never()).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void permitsAdminAllParcelSubscription() throws Exception {
        WebSocketSession session = session("/ws/tracking/all",
                new TestingAuthenticationToken("admin", "password", "ROLE_ADMIN"));

        handler.afterConnectionEstablished(session);

        verify(session, never()).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void permitsDispatcherAllParcelSubscription() throws Exception {
        WebSocketSession session = session("/ws/tracking/all",
                new TestingAuthenticationToken("dispatcher", "password", "ROLE_DISPATCHER"));

        handler.afterConnectionEstablished(session);

        verify(session, never()).close(CloseStatus.POLICY_VIOLATION);
    }

    private static WebSocketSession session(String path, java.security.Principal principal) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost" + path));
        when(session.getPrincipal()).thenReturn(principal);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("session");
        return session;
    }
}
