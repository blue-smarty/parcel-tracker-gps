package com.parceltracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class AddressSuggestionController {
    private static final String TOMTOM_SEARCH_URL = "https://api.tomtom.com/search/2/search/";
    private static final int MAX_QUERY_LENGTH = 120;
    private static final int MAX_SUGGESTIONS = 5;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public AddressSuggestionController(
            RestTemplate addressLookupRestTemplate,
            @Value("${parcel.address-lookup.tomtom-api-key:}") String apiKey) {
        this.restTemplate = addressLookupRestTemplate;
        this.apiKey = apiKey;
    }

    @GetMapping("/api/address-suggestions")
    public List<String> suggest(@RequestParam(name = "q", defaultValue = "") String query) {
        String normalized = query.trim();
        if (normalized.length() < 3) return List.of();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 120 characters or fewer.");
        }
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address suggestions are not configured.");
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(TOMTOM_SEARCH_URL)
                .pathSegment("{query}.json")
                .queryParam("key", apiKey)
                .queryParam("limit", MAX_SUGGESTIONS)
                .buildAndExpand(normalized)
                .encode()
                .toUri();
        JsonNode response;
        try {
            response = restTemplate.getForObject(uri, JsonNode.class);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address suggestions are temporarily unavailable.");
        }
        if (response == null || !response.path("results").isArray()) return List.of();

        Set<String> suggestions = new LinkedHashSet<>();
        for (JsonNode result : response.path("results")) {
            JsonNode address = result.path("address").path("freeformAddress");
            String suggestion = address.isTextual() ? address.asText().trim() : "";
            if (!suggestion.isBlank()) suggestions.add(suggestion);
            if (suggestions.size() == MAX_SUGGESTIONS) break;
        }
        return new ArrayList<>(suggestions);
    }
}
