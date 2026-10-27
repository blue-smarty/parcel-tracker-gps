package com.parceltracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AddressLookupService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public List<Suggestion> suggest(String query) throws IOException, InterruptedException {
        String url = "https://photon.komoot.io/api/?q=" + encode(query) + "&limit=5&lang=en";
        JsonNode features = requestJson(url).path("features");
        List<Suggestion> suggestions = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode geometry = feature.path("geometry").path("coordinates");
            JsonNode properties = feature.path("properties");
            String label = formatAddress(properties);
            if (!label.isBlank() && geometry.size() >= 2
                    && geometry.get(0).isNumber() && geometry.get(1).isNumber()) {
                suggestions.add(new Suggestion(label, geometry.get(1).asDouble(), geometry.get(0).asDouble()));
            }
        }
        return suggestions;
    }

    public Suggestion approximateCurrentLocation() throws IOException, InterruptedException {
        JsonNode location = requestJson("https://ipapi.co/json/");
        JsonNode latitude = location.path("latitude");
        JsonNode longitude = location.path("longitude");
        if (!latitude.isNumber() || !longitude.isNumber()) {
            throw new IOException("The network location service did not return coordinates.");
        }
        return reverse(latitude.asDouble(), longitude.asDouble());
    }

    private Suggestion reverse(double latitude, double longitude) throws IOException, InterruptedException {
        JsonNode features = requestJson("https://photon.komoot.io/reverse?lat=" + latitude
                + "&lon=" + longitude + "&lang=en").path("features");
        if (features.isEmpty()) throw new IOException("No address was found for this location.");
        JsonNode feature = features.get(0);
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        double resultLatitude = coordinates.size() >= 2 && coordinates.get(1).isNumber()
                ? coordinates.get(1).asDouble() : latitude;
        double resultLongitude = coordinates.size() >= 2 && coordinates.get(0).isNumber()
                ? coordinates.get(0).asDouble() : longitude;
        return new Suggestion(formatAddress(feature.path("properties")), resultLatitude, resultLongitude);
    }

    private JsonNode requestJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", "ParcelTrackerGPS/1.0")
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Address service returned HTTP " + response.statusCode() + ".");
        }
        return MAPPER.readTree(response.body());
    }

    private static String formatAddress(JsonNode properties) {
        List<String> parts = new ArrayList<>();
        String name = properties.path("name").asText("");
        String street = properties.path("street").asText("");
        String number = properties.path("housenumber").asText("");
        addUnique(parts, name);
        addUnique(parts, (number + " " + street).trim());
        addUnique(parts, properties.path("postcode").asText(""));
        addUnique(parts, properties.path("city").asText(""));
        addUnique(parts, properties.path("state").asText(""));
        addUnique(parts, properties.path("country").asText(""));
        return String.join(", ", parts);
    }

    private static void addUnique(List<String> parts, String value) {
        if (!value.isBlank() && !parts.contains(value)) parts.add(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Suggestion(String address, double latitude, double longitude) { }
}
