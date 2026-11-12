package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parceltracker.model.ServiceAreaSettings;
import com.parceltracker.repository.ServiceAreaSettingsRepository;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class ServiceAreaServiceTest {
    private final ServiceAreaSettingsRepository repository = mock(ServiceAreaSettingsRepository.class);
    private final RestTemplate rest = mock(RestTemplate.class);
    private final AustralianCouncilLookup councilLookup = mock(AustralianCouncilLookup.class);
    private final TomTomSettingsService tomTomSettings = mock(TomTomSettingsService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ServiceAreaSettings settings = new ServiceAreaSettings();
    private final ServiceAreaService service = new ServiceAreaService(repository, rest, tomTomSettings, councilLookup);

    @BeforeEach
    void setUp() {
        when(repository.findById("default")).thenReturn(Optional.of(settings));
        lenient().when(tomTomSettings.routeKey()).thenReturn("test-key");
    }

    private JsonNode json(String text) throws Exception { return json.readTree(text); }

    @Test
    void unrestrictedSkipsVerification() {
        service.requireDelivery(null, null);
        verifyNoInteractions(rest);
    }

    @Test
    void verifiesBothEndpointsAgainstMetroCouncils() throws Exception {
        settings.setMelbourneMetroOnly(true);
        settings.setCoverageMode("MELBOURNE");
        JsonNode point = json("""
                {"results":[{"type":"Point Address","address":{"countryCode":"AU"},
                 "position":{"lat":-37.8136,"lon":144.9631}}]}
                """);
        JsonNode council = json("{\"features\":[{\"attributes\":{\"lga_name\":\"Melbourne\"}}]}");
        when(rest.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(point, council, point, council);
        service.requireDelivery("1 Main St", "2 Main St");
        verify(rest, times(4)).getForObject(any(URI.class), eq(JsonNode.class));
    }

    @Test
    void rejectsOutsideCouncilAndNeverAcceptsProviderFailure() throws Exception {
        settings.setMelbourneMetroOnly(true);
        settings.setCoverageMode("MELBOURNE");
        JsonNode point = json("""
                {"results":[{"type":"Point Address","address":{"countryCode":"AU"},
                 "position":{"lat":-37.0,"lon":144.0}}]}
                """);
        when(rest.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenReturn(point, json("{\"features\":[{\"attributes\":{\"lga_name\":\"Ballarat\"}}]}"))
                .thenThrow(new ResourceAccessException("offline"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ServiceAreaException.class, () -> service.requirePickup("address")).getStatus());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ServiceAreaException.class, () -> service.requirePickup("address")).getStatus());
    }

    @Test
    void rejectsDeliveryWhenDestinationIsOutsideMetro() throws Exception {
        settings.setMelbourneMetroOnly(true);
        settings.setCoverageMode("MELBOURNE");
        JsonNode point = json("""
                {"results":[{"type":"Point Address","address":{"countryCode":"AU"},
                 "position":{"lat":-37.8,"lon":144.9}}]}
                """);
        when(rest.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(
                point, json("{\"features\":[{\"attributes\":{\"lga_name\":\"Melbourne City\"}}]}"),
                point, json("{\"features\":[{\"attributes\":{\"lga_name\":\"Ballarat\"}}]}"));
        ServiceAreaException error = assertThrows(ServiceAreaException.class,
                () -> service.requireDelivery("pickup", "destination"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        assertTrue(error.getMessage().contains("Delivery"));
    }

    @Test
    void rejectsUnverifiedAddressesAndMissingSettings() throws Exception {
        settings.setMelbourneMetroOnly(true);
        settings.setCoverageMode("MELBOURNE");
        when(rest.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenReturn(json("{\"results\":[{\"type\":\"Geography\"}]}"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY,
                assertThrows(ServiceAreaException.class, () -> service.requirePickup("Melbourne")).getStatus());
        when(repository.findById("default")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ServiceAreaException.class, () -> service.requirePickup("address")).getStatus());
    }

    @Test
    void customCoverageAcceptsSelectedStatesCouncilsOrDrawnArea() throws Exception {
        settings.setCoverageMode("CUSTOM");
        settings.setStates(java.util.List.of("1"));
        settings.setCouncils(java.util.List.of("25150"));
        settings.setPolygons(java.util.List.of(java.util.List.of(
                java.util.List.of(-38.0, 144.0), java.util.List.of(-38.0, 145.0),
                java.util.List.of(-37.0, 145.0), java.util.List.of(-37.0, 144.0))));
        JsonNode point = json("""
                {"results":[{"type":"Point Address","address":{"countryCode":"AU"},
                 "position":{"lat":-37.5,"lon":144.5}}]}
                """);
        when(rest.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(point);
        service.requirePickup("inside polygon");
        verifyNoInteractions(councilLookup);
        settings.setPolygons(java.util.List.of());
        when(councilLookup.at(-37.5, 144.5)).thenReturn(java.util.List.of(
                new AustralianCouncilLookup.Council("25150", "Melbourne", "2")));
        service.requirePickup("selected council");
        when(councilLookup.at(-37.5, 144.5)).thenReturn(java.util.List.of(
                new AustralianCouncilLookup.Council("10050", "Sydney", "1")));
        service.requirePickup("selected state");
        when(councilLookup.at(-37.5, 144.5)).thenReturn(java.util.List.of(
                new AustralianCouncilLookup.Council("20050", "Outside", "2")));
        assertThrows(ServiceAreaException.class, () -> service.requirePickup("outside"));
    }

    @Test
    void rejectsSelfIntersectingCustomAreaAndIncludesBoundary() {
        var polygon = java.util.List.of(java.util.List.of(-38.0, 144.0), java.util.List.of(-38.0, 145.0),
                java.util.List.of(-37.0, 145.0), java.util.List.of(-37.0, 144.0));
        CoveragePolygon.validate(polygon);
        assertTrue(CoveragePolygon.contains(polygon, -38.0, 144.5));
        assertFalse(CoveragePolygon.contains(polygon, -39.0, 144.5));
        assertThrows(ServiceAreaException.class, () -> CoveragePolygon.validate(java.util.List.of(
                polygon.get(0), polygon.get(2), polygon.get(1), polygon.get(3))));
    }
}
