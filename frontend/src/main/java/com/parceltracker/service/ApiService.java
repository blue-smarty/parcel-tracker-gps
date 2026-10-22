package com.parceltracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.Pickup;
import com.parceltracker.model.PickupRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class ApiService {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final HttpClient client = HttpClient.newHttpClient();
    private final String baseUrl;

    public ApiService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public List<Parcel> getParcels() throws IOException, InterruptedException {
        return MAPPER.readValue(send(get("/api/parcels")), new TypeReference<List<Parcel>>() { });
    }

    public Parcel getParcel(String id) throws IOException, InterruptedException {
        return MAPPER.readValue(send(get("/api/parcels/" + id)), Parcel.class);
    }

    public List<Location> getHistory(String id) throws IOException, InterruptedException {
        return MAPPER.readValue(send(get("/api/parcels/" + id + "/history")), new TypeReference<List<Location>>() { });
    }

    public Parcel createParcel(Parcel parcel) throws IOException, InterruptedException {
        return MAPPER.readValue(send(post("/api/parcels", parcel)), Parcel.class);
    }

    public Pickup createPickup(PickupRequest request) throws IOException, InterruptedException {
        return MAPPER.readValue(send(post("/api/pickups", request)), Pickup.class);
    }

    public Parcel updateLocation(String id, Location location) throws IOException, InterruptedException {
        return MAPPER.readValue(send(post("/api/parcels/" + id + "/location", location)), Parcel.class);
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
    }

    private HttpRequest post(String path, Object body) throws IOException {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(response.statusCode(), response.body());
        }
        return response.body();
    }
}
