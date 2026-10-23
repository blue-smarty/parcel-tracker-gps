package com.parceltracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.DeliveryProof;
import com.parceltracker.model.CourierEarnings;
import com.parceltracker.model.Pickup;
import com.parceltracker.model.PickupRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Base64;
import java.net.CookieManager;

public class ApiService {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .build();
    private final String baseUrl;
    private String courierAuthorization;
    private String csrfToken;

    public ApiService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void setCourierCredentials(String courierId, String password) {
        String credentials = courierId + ":" + password;
        courierAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
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

    public Parcel assignCourier(String parcelId, String courierId) throws IOException, InterruptedException {
        return MAPPER.readValue(send(put("/api/parcels/" + pathSegment(parcelId) + "/courier",
                java.util.Map.of("courierId", courierId))), Parcel.class);
    }

    public List<Parcel> getCourierDeliveries(String courierId) throws IOException, InterruptedException {
        return MAPPER.readValue(send(get("/api/couriers/" + pathSegment(courierId) + "/deliveries")),
                new TypeReference<List<Parcel>>() { });
    }

    public CourierEarnings getCourierEarnings(String courierId) throws IOException, InterruptedException {
        return MAPPER.readValue(send(get("/api/couriers/" + pathSegment(courierId) + "/earnings")),
                CourierEarnings.class);
    }

    public Parcel decideCourierDelivery(String courierId, String parcelId, boolean accepted)
            throws IOException, InterruptedException {
        return MAPPER.readValue(send(put("/api/couriers/" + pathSegment(courierId) + "/deliveries/"
                + pathSegment(parcelId) + "/decision", java.util.Map.of("accepted", accepted))), Parcel.class);
    }

    public Parcel completeCourierDelivery(String courierId, String parcelId, DeliveryProof proof)
            throws IOException, InterruptedException {
        return MAPPER.readValue(send(post("/api/couriers/" + pathSegment(courierId) + "/deliveries/"
                + pathSegment(parcelId) + "/proof", proof)), Parcel.class);
    }

    public Parcel updateCourierDeliveryStatus(String courierId, String parcelId, String status)
            throws IOException, InterruptedException {
        return MAPPER.readValue(send(put("/api/couriers/" + pathSegment(courierId) + "/deliveries/"
                + pathSegment(parcelId) + "/status", java.util.Map.of("status", status))), Parcel.class);
    }

    public Parcel updateCourierDeliveryLocation(String courierId, String parcelId, Location location)
            throws IOException, InterruptedException {
        return MAPPER.readValue(send(post("/api/couriers/" + pathSegment(courierId) + "/deliveries/"
                + pathSegment(parcelId) + "/location", location)), Parcel.class);
    }

    private HttpRequest get(String path) {
        return withCourierAuthorization(HttpRequest.newBuilder(URI.create(baseUrl + path))).GET().build();
    }

    private HttpRequest post(String path, Object body) throws IOException {
        return withCourierAuthorization(HttpRequest.newBuilder(URI.create(baseUrl + path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
    }

    private HttpRequest put(String path, Object body) throws IOException {
        return withCourierAuthorization(HttpRequest.newBuilder(URI.create(baseUrl + path)))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
    }

    private HttpRequest.Builder withCourierAuthorization(HttpRequest.Builder builder) {
        if (courierAuthorization != null) builder.header("Authorization", courierAuthorization);
        return builder;
    }

    private static String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        if (!"GET".equalsIgnoreCase(request.method())) {
            request = withHeader(request, "X-XSRF-TOKEN", getCsrfToken());
        }
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new ApiException(response.statusCode(), response.body());
        }
        return response.body();
    }

    private synchronized String getCsrfToken() throws IOException, InterruptedException {
        if (csrfToken == null) {
            HttpResponse<String> response = client.send(get("/api/csrf"), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ApiException(response.statusCode(), response.body());
            }
            csrfToken = MAPPER.readTree(response.body()).path("token").asText();
            if (csrfToken.isBlank()) throw new IOException("The server did not provide a CSRF token");
        }
        return csrfToken;
    }

    private static HttpRequest withHeader(HttpRequest request, String name, String value) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        request.headers().map().forEach((header, values) ->
                values.forEach(headerValue -> builder.header(header, headerValue)));
        return builder.header(name, value).build();
    }
}
