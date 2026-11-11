package com.parceltracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.parceltracker.model.ServiceAreaSettings;
import com.parceltracker.repository.ServiceAreaSettingsRepository;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ServiceAreaService {
    private static final Set<String> METRO = Set.of(
            "BANYULE", "BAYSIDE", "BOROONDARA", "BRIMBANK", "CARDINIA", "CASEY",
            "DAREBIN", "FRANKSTON", "GLEN EIRA", "GREATER DANDENONG", "HOBSONS BAY",
            "HUME", "KINGSTON", "KNOX", "MANNINGHAM", "MARIBYRNONG", "MAROONDAH",
            "MELBOURNE", "MELTON", "MERRI-BEK", "MONASH", "MOONEE VALLEY",
            "MORNINGTON PENINSULA", "NILLUMBIK", "PORT PHILLIP", "STONNINGTON",
            "WHITEHORSE", "WHITTLESEA", "WYNDHAM", "YARRA", "YARRA RANGES");
    private static final String VICMAP = "https://services-ap1.arcgis.com/P744lA0wf4LlBZ84/ArcGIS/rest/services/Vicmap_Admin/FeatureServer/9/query";

    private final ServiceAreaSettingsRepository settings;
    private final RestTemplate rest;
    private final String apiKey;

    public ServiceAreaService(ServiceAreaSettingsRepository settings, RestTemplate addressLookupRestTemplate,
            @Value("${parcel.tomtom.api-key:}") String apiKey) {
        this.settings = settings;
        this.rest = addressLookupRestTemplate;
        this.apiKey = apiKey.trim();
    }

    public void requireDelivery(String pickup, String destination) {
        if (!enabled()) return;
        requireAddress(pickup, "Pickup");
        requireAddress(destination, "Delivery");
    }

    public void requirePickup(String address) {
        if (enabled()) requireAddress(address, "Pickup");
    }

    public void requireDestination(String address) {
        if (enabled()) requireAddress(address, "Delivery");
    }

    private boolean enabled() {
        return settings.findById(ServiceAreaSettings.SINGLETON_ID)
                .orElseThrow(() -> new ServiceAreaException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Delivery service area settings are unavailable."))
                .isMelbourneMetroOnly();
    }

    private void requireAddress(String address, String label) {
        if (address == null || address.isBlank() || address.trim().length() > 255) {
            throw new ServiceAreaException(HttpStatus.BAD_REQUEST, label + " address is required (up to 255 characters).");
        }
        if (apiKey.isEmpty()) throw unavailable();
        try {
            URI geocode = UriComponentsBuilder.fromHttpUrl("https://api.tomtom.com")
                    .pathSegment("search", "2", "geocode", "{address}.json")
                    .queryParam("key", "{key}").queryParam("limit", 1)
                    .buildAndExpand(address.trim(), apiKey).encode().toUri();
            JsonNode result = rest.getForObject(geocode, JsonNode.class);
            JsonNode results = result == null ? null : result.path("results");
            if (results == null || !results.isArray()) throw unavailable();
            if (results.isEmpty()) throw invalidAddress(label);
            JsonNode match = results.get(0);
            JsonNode position = match.path("position");
            JsonNode location = match.path("address");
            if (!"Point Address".equals(match.path("type").asText())
                    || !"AU".equalsIgnoreCase(location.path("countryCode").asText())
                    || !position.path("lat").isNumber() || !position.path("lon").isNumber()) {
                throw invalidAddress(label);
            }
            double latitude = position.path("lat").asDouble();
            double longitude = position.path("lon").asDouble();
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw invalidAddress(label);
            URI query = UriComponentsBuilder.fromHttpUrl(VICMAP)
                    .queryParam("geometry", "{point}").queryParam("geometryType", "esriGeometryPoint")
                    .queryParam("inSR", "4326").queryParam("spatialRel", "esriSpatialRelIntersects")
                    .queryParam("outFields", "lga_name").queryParam("returnGeometry", "false")
                    .queryParam("f", "json").buildAndExpand(longitude + "," + latitude).encode().toUri();
            JsonNode answer = rest.getForObject(query, JsonNode.class);
            JsonNode features = answer == null ? null : answer.path("features");
            if (answer == null || answer.has("error") || features == null || !features.isArray()) throw unavailable();
            if (features.isEmpty()) throw outside(label);
            for (JsonNode feature : features) {
                JsonNode attributes = feature.path("attributes");
                JsonNode name = attributes.has("lga_name") ? attributes.path("lga_name") : attributes.path("LGA_NAME");
                if (!name.isTextual() || name.asText().isBlank()) throw unavailable();
                if (!METRO.contains(normalize(name.asText()))) throw outside(label);
            }
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT)
                .replaceFirst("^(CITY OF |SHIRE OF )", "")
                .replaceFirst(" (CITY|SHIRE|\\(C\\)|\\(S\\))$", "").trim();
    }

    private static ServiceAreaException invalidAddress(String label) {
        return new ServiceAreaException(HttpStatus.UNPROCESSABLE_ENTITY,
                label + " address could not be verified as a street address. Check it and try again.");
    }

    private static ServiceAreaException outside(String label) {
        return new ServiceAreaException(HttpStatus.UNPROCESSABLE_ENTITY,
                label + " address is outside the Melbourne metropolitan service area.");
    }

    private static ServiceAreaException unavailable() {
        return new ServiceAreaException(HttpStatus.SERVICE_UNAVAILABLE,
                "Service area verification is unavailable. Please try again later.");
    }
}
