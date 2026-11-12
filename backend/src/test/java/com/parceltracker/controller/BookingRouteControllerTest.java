package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.PublicQuoteRateLimiter;
import com.parceltracker.service.TomTomSettingsService;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(value = BookingRouteController.class, properties = "parcel.tomtom.api-key=test-key")
@Import(SecurityConfig.class)
@WithMockUser(roles = "CUSTOMER")
class BookingRouteControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockBean private CourierAccountRepository accounts;
    @MockBean private RestTemplate addressLookupRestTemplate;
    @MockBean private PublicQuoteRateLimiter rateLimiter;
    @MockBean private TomTomSettingsService settings;
    private static final String REQUEST = """
            {"origin":"10 Main Street","destination":"20 Main Street"}
            """;

    @Test
    void geocodesAddressesAndReturnsRoute() throws Exception {
        when(settings.routeKey()).thenReturn("test-key");
        JsonNode start = mapper.readTree("""
                {"results":[{"position":{"lat":51.5,"lon":-0.1}}]}
                """);
        JsonNode end = mapper.readTree("""
                {"results":[{"position":{"lat":51.6,"lon":-0.2}}]}
                """);
        JsonNode route = mapper.readTree("""
                {"routes":[{"summary":{"lengthInMeters":12345,"travelTimeInSeconds":1500},
                  "legs":[{"points":[{"latitude":51.5,"longitude":-0.1},
                    {"latitude":51.6,"longitude":-0.2}]}]}]}
                """);
        when(addressLookupRestTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenReturn(start, end, route);
        mvc.perform(post("/api/booking-route").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceMeters").value(12345))
                .andExpect(jsonPath("$.durationSeconds").value(1500))
                .andExpect(jsonPath("$.points[1].latitude").value(51.6));
    }

    @Test
    void reportsMissingAddressAndProviderErrors() throws Exception {
        when(settings.routeKey()).thenReturn("test-key");
        when(addressLookupRestTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenReturn(mapper.readTree("{\"results\":[]}"))
                .thenThrow(new ResourceAccessException("offline"));
        mvc.perform(post("/api/booking-route").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/booking-route").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void rejectsBlankInputWithoutCallingProvider() throws Exception {
        mvc.perform(post("/api/booking-route").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\":\" \",\"destination\":\"B\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(addressLookupRestTemplate);
    }

    @Test
    void reportsUnconfiguredProviderWithoutCallingIt() {
        BookingRouteController controller = new BookingRouteController(
                addressLookupRestTemplate, settings, new PublicQuoteRateLimiter());
        when(settings.routeKey()).thenReturn("");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.route(new BookingRouteController.RouteRequest("A", "B"), new MockHttpServletRequest()));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(addressLookupRestTemplate);
    }

    @Test
    @WithAnonymousUser
    void allowsAnonymousQuoteRequests() throws Exception {
        when(settings.routeKey()).thenReturn("test-key");
        JsonNode start = mapper.readTree("""
                {"results":[{"position":{"lat":51.5,"lon":-0.1}}]}
                """);
        JsonNode end = mapper.readTree("""
                {"results":[{"position":{"lat":51.6,"lon":-0.2}}]}
                """);
        JsonNode route = mapper.readTree("""
                {"routes":[{"summary":{"lengthInMeters":12345,"travelTimeInSeconds":1500},
                  "legs":[{"points":[{"latitude":51.5,"longitude":-0.1},
                    {"latitude":51.6,"longitude":-0.2}]}]}]}
                """);
        when(addressLookupRestTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenReturn(start, end, route);
        mvc.perform(post("/api/booking-route").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceMeters").value(12345));
    }

    @Test
    void usesCurrentKeyForEveryRouteRequest() throws Exception {
        when(settings.routeKey()).thenReturn("first-key", "updated-key");
        when(addressLookupRestTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenReturn(mapper.readTree("{\"results\":[]}"));
        mvc.perform(post("/api/booking-route").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/booking-route").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnprocessableEntity());
        ArgumentCaptor<URI> uris = ArgumentCaptor.forClass(URI.class);
        verify(addressLookupRestTemplate, org.mockito.Mockito.times(2)).getForObject(uris.capture(), eq(JsonNode.class));
        org.junit.jupiter.api.Assertions.assertTrue(uris.getAllValues().get(0).getQuery().contains("first-key"));
        org.junit.jupiter.api.Assertions.assertTrue(uris.getAllValues().get(1).getQuery().contains("updated-key"));
    }
}
