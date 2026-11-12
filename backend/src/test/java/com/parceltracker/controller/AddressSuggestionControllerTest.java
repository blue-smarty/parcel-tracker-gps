package com.parceltracker.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.TomTomSettingsService;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AddressSuggestionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "parcel.address-lookup.tomtom-api-key=test-tomtom-key")
@WithMockUser(roles = "CUSTOMER")
class AddressSuggestionControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CourierAccountRepository accounts;
    @MockBean private RestTemplate addressLookupRestTemplate;
    @MockBean private TomTomSettingsService settings;

    @BeforeEach
    void setUp() {
        when(settings.searchKey()).thenReturn("test-tomtom-key");
    }

    @Test
    void returnsFormattedSuggestionsToAuthenticatedUsers() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"results":[
                  {"address":{"freeformAddress":"10 Downing Street, London, SW1A 2AA, United Kingdom"}},
                  {"address":{"freeformAddress":"Downing Street, London, United Kingdom"}}
                ]}
                """);
        when(addressLookupRestTemplate.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(response);

        mvc.perform(get("/api/address-suggestions").param("q", "10 Downing Street"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("10 Downing Street, London, SW1A 2AA, United Kingdom"))
                .andExpect(jsonPath("$[1]").value("Downing Street, London, United Kingdom"));

        ArgumentCaptor<URI> requestUri = ArgumentCaptor.forClass(URI.class);
        verify(addressLookupRestTemplate).getForObject(requestUri.capture(), eq(JsonNode.class));
        assertTrue(requestUri.getValue().toString().contains("/search/2/search/10%20Downing%20Street.json"));
        assertTrue(requestUri.getValue().getQuery().contains("key=test-tomtom-key"));
        assertTrue(requestUri.getValue().getQuery().contains("limit=5"));
    }

    @Test
    void doesNotCallProviderForShortQueries() throws Exception {
        mvc.perform(get("/api/address-suggestions").param("q", "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        verifyNoInteractions(addressLookupRestTemplate);
    }

    @Test
    void rejectsQueriesLongerThanTheSupportedLimit() throws Exception {
        mvc.perform(get("/api/address-suggestions").param("q", "a".repeat(121)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(addressLookupRestTemplate);
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/address-suggestions").param("q", "10 Downing Street"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(addressLookupRestTemplate);
    }

    @Test
    void reportsProviderOutagesAsServiceUnavailable() throws Exception {
        when(addressLookupRestTemplate.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("Provider timeout"));

        mvc.perform(get("/api/address-suggestions").param("q", "10 Downing Street"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void reportsMissingTomTomApiKeyAsServiceUnavailable() {
        when(settings.searchKey()).thenReturn("");
        AddressSuggestionController controller = new AddressSuggestionController(addressLookupRestTemplate, settings);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> controller.suggest("10 Downing Street"));

        assertEquals(503, exception.getStatusCode().value());
        verifyNoInteractions(addressLookupRestTemplate);
    }
}
