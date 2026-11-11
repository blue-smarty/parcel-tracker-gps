package com.parceltracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AustralianCouncilLookup {
    private static final String QUERY = "https://geo.abs.gov.au/arcgis/rest/services/ASGS2025/LGA/MapServer/0/query";
    private final RestTemplate rest;

    public record Council(String code, String name, String state) {}

    public AustralianCouncilLookup(RestTemplate addressLookupRestTemplate) {
        this.rest = addressLookupRestTemplate;
    }

    public List<Council> all() {
        URI uri = base().queryParam("where", "1=1").queryParam("resultRecordCount", 1000)
                .build().encode().toUri();
        return query(uri);
    }

    public List<Council> at(double latitude, double longitude) {
        URI uri = base().queryParam("geometry", "{point}")
                .queryParam("geometryType", "esriGeometryPoint").queryParam("inSR", "4326")
                .queryParam("spatialRel", "esriSpatialRelIntersects")
                .buildAndExpand(longitude + "," + latitude).encode().toUri();
        return query(uri);
    }

    private UriComponentsBuilder base() {
        return UriComponentsBuilder.fromHttpUrl(QUERY)
                .queryParam("outFields", "LGA_CODE_2025,LGA_NAME_2025,STATE_CODE_2021")
                .queryParam("returnGeometry", "false").queryParam("f", "json");
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
