package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class AustralianCouncilLookupTest {
    private final RestTemplate rest = mock(RestTemplate.class);
    private final AustralianCouncilLookup lookup = new AustralianCouncilLookup(rest);

    @Test
    void catalogueContainsSpatialCouncilsWithoutCallingTheProvider() {
        var councils = lookup.all();
        assertTrue(councils.contains(new AustralianCouncilLookup.Council("24600", "Melbourne", "2")));
        assertTrue(councils.contains(new AustralianCouncilLookup.Council("10050", "Albury", "1")));
        assertTrue(councils.contains(new AustralianCouncilLookup.Council("89399", "Unincorporated ACT", "8")));
        assertFalse(councils.stream().anyMatch(c -> c.code().equals("ZZZZZ") || c.code().equals("29499")));
        verifyNoInteractions(rest);
    }

    @Test
    void spatialLookupRemainsLiveAndFailsClosed() throws Exception {
        JsonNode response = new ObjectMapper().readTree("""
                {"features":[{"attributes":{"LGA_CODE_2025":"24600","LGA_NAME_2025":"Melbourne",
                "STATE_CODE_2021":"2"}}]}
                """);
        when(rest.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(response)
                .thenThrow(new ResourceAccessException("offline"));
        assertEquals(lookup.all().stream().filter(c -> c.code().equals("24600")).toList(),
                lookup.at(-37.8136, 144.9631));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ServiceAreaException.class, () -> lookup.at(-37.8136, 144.9631)).getStatus());
    }

    @Test
    void spatialLookupReadsProviderJsonWithConfiguredRestTemplate() {
        RestTemplate template = new RestTemplateBuilder().build();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("""
                        {"features":[{"attributes":{"LGA_CODE_2025":"24600","LGA_NAME_2025":"Melbourne",
                        "STATE_CODE_2021":"2"}}]}
                        """, MediaType.APPLICATION_JSON));
        assertEquals(java.util.List.of(new AustralianCouncilLookup.Council("24600", "Melbourne", "2")),
                new AustralianCouncilLookup(template).at(-37.8136, 144.9631));
        server.verify();
    }

    @Test
    void spatialLookupRejectsMalformedProviderRecords() throws Exception {
        JsonNode response = new ObjectMapper().readTree("""
                {"features":[{"attributes":{"LGA_CODE_2025":"ZZZZZ","LGA_NAME_2025":"Outside Australia",
                "STATE_CODE_2021":"Z"}}]}
                """);
        when(rest.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ServiceAreaException.class, () -> lookup.at(-37.8136, 144.9631)).getStatus());
    }
}
