package com.parceltracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class AddressSuggestionController {
    private static final String PHOTON_SEARCH_URL = "https://photon.komoot.io/api/";
    private static final int MAX_QUERY_LENGTH = 120;
    private static final int MAX_SUGGESTIONS = 5;

    private final RestTemplate restTemplate;

    public AddressSuggestionController(RestTemplate addressLookupRestTemplate) {
        this.restTemplate = addressLookupRestTemplate;
    }

    @GetMapping("/api/address-suggestions")
    public List<String> suggest(@RequestParam(name = "q", defaultValue = "") String query) {
        String normalized = query.trim();
        if (normalized.length() < 3) return List.of();
        if (normalized.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search text must be 120 characters or fewer.");
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(PHOTON_SEARCH_URL)
                .queryParam("q", normalized)
                .queryParam("limit", MAX_SUGGESTIONS)
                .build()
                .encode()
                .toUri();
        JsonNode response;
        try {
            response = restTemplate.getForObject(uri, JsonNode.class);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address suggestions are temporarily unavailable.");
        }
        if (response == null || !response.path("features").isArray()) return List.of();

        Set<String> suggestions = new LinkedHashSet<>();
        for (JsonNode feature : response.path("features")) {
            String suggestion = formatAddress(feature.path("properties"));
            if (!suggestion.isBlank()) suggestions.add(suggestion);
            if (suggestions.size() == MAX_SUGGESTIONS) break;
        }
        return new ArrayList<>(suggestions);
    }

    private static String formatAddress(JsonNode properties) {
        List<String> parts = new ArrayList<>();
        String houseNumber = value(properties, "housenumber");
        String street = value(properties, "street");
        String streetAddress = String.join(" ", List.of(houseNumber, street).stream()
                .filter(part -> !part.isBlank()).toList());
        addIfPresent(parts, streetAddress.isBlank() ? value(properties, "name") : streetAddress);
        addIfPresent(parts, firstValue(properties, "city", "town", "village", "municipality"));
        addIfPresent(parts, value(properties, "postcode"));
        addIfPresent(parts, value(properties, "state"));
        addIfPresent(parts, value(properties, "country"));
        return String.join(", ", parts);
    }

    private static String firstValue(JsonNode properties, String... names) {
        for (String name : names) {
            String value = value(properties, name);
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private static String value(JsonNode properties, String name) {
        JsonNode node = properties.path(name);
        return node.isTextual() ? node.asText().trim() : "";
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (!value.isBlank() && !parts.contains(value)) parts.add(value);
    }
}
