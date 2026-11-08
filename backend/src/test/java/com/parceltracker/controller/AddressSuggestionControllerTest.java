package com.parceltracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parceltracker.config.SecurityConfig;
import com.parceltracker.repository.CourierAccountRepository;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

@WebMvcTest(AddressSuggestionController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "CUSTOMER")
class AddressSuggestionControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CourierAccountRepository accounts;
    @MockBean private RestTemplate addressLookupRestTemplate;

    @Test
    void returnsFormattedSuggestionsToAuthenticatedUsers() throws Exception {
        JsonNode response = objectMapper.readTree("""
                {"features":[
                  {"properties":{"housenumber":"10","street":"Downing Street","city":"London","postcode":"SW1A 2AA","country":"United Kingdom"}},
                  {"properties":{"name":"Downing Street","city":"London","country":"United Kingdom"}}
                ]}
                """);
        when(addressLookupRestTemplate.getForObject(any(URI.class), eq(JsonNode.class))).thenReturn(response);

        mvc.perform(get("/api/address-suggestions").param("q", "10 Downing Street"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("10 Downing Street, London, SW1A 2AA, United Kingdom"))
                .andExpect(jsonPath("$[1]").value("Downing Street, London, United Kingdom"));
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
}
