package com.parceltracker.service;

import tools.jackson.databind.JsonNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AustralianCouncilLookup {
    private static final String QUERY = "https://geo.abs.gov.au/arcgis/rest/services/ASGS2025/LGA/MapServer/0/query";
    private static final List<Council> COUNCILS = loadCouncils();
    private final RestTemplate rest;

    public record Council(String code, String name, String state) {}

    public AustralianCouncilLookup(RestTemplate addressLookupRestTemplate) {
        this.rest = addressLookupRestTemplate;
    }

    public List<Council> all() {
        return COUNCILS;
    }

    public List<Council> at(double latitude, double longitude) {
        URI uri = base().queryParam("geometry", "{point}")
                .queryParam("geometryType", "esriGeometryPoint").queryParam("inSR", "4326")
                .queryParam("spatialRel", "esriSpatialRelIntersects")
                .buildAndExpand(longitude + "," + latitude).encode().toUri();
        return query(uri);
    }

    private UriComponentsBuilder base() {
        return UriComponentsBuilder.fromUriString(QUERY)
                .queryParam("outFields", "LGA_CODE_2025,LGA_NAME_2025,STATE_CODE_2021")
                .queryParam("returnGeometry", "false").queryParam("f", "json");
    }

    private static List<Council> loadCouncils() {
        InputStream input = AustralianCouncilLookup.class.getResourceAsStream("/data/abs-lga-2025.tsv");
        if (input == null) throw new IllegalStateException("Australian council catalogue is missing.");
        List<Council> councils = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 3 || !fields[0].matches("[0-9]{5}")
                        || fields[1].isBlank() || !fields[2].matches("[1-9]") || !codes.add(fields[0])) {
                    throw new IllegalStateException("Australian council catalogue is invalid.");
                }
                councils.add(new Council(fields[0], fields[1], fields[2]));
            }
        } catch (IOException error) {
            throw new IllegalStateException("Australian council catalogue could not be read.", error);
        }
        if (councils.isEmpty()) throw new IllegalStateException("Australian council catalogue is empty.");
        return List.copyOf(councils);
    }

    private List<Council> query(URI uri) {
        try {
            JsonNode result = rest.getForObject(uri, JsonNode.class);
            if (result == null || result.has("error") || result.path("exceededTransferLimit").asBoolean()
                    || !result.path("features").isArray()) throw unavailable();
            List<Council> councils = new ArrayList<>();
            for (JsonNode feature : result.path("features")) {
                JsonNode attributes = feature.path("attributes");
                String code = attributes.path("LGA_CODE_2025").asText("");
                String name = attributes.path("LGA_NAME_2025").asText("");
                String state = attributes.path("STATE_CODE_2021").asText("");
                if (!code.matches("[0-9]{5}") || name.isBlank() || !state.matches("[1-9]")) throw unavailable();
                councils.add(new Council(code, name, state));
            }
            return councils;
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private static ServiceAreaException unavailable() {
        return new ServiceAreaException(HttpStatus.SERVICE_UNAVAILABLE, "Australian council boundaries are unavailable.");
    }
}
