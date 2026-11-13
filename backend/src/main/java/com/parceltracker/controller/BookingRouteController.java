package com.parceltracker.controller;

import tools.jackson.databind.JsonNode;
import com.parceltracker.service.PublicQuoteRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class BookingRouteController {
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final PublicQuoteRateLimiter rateLimiter;

    public BookingRouteController(RestTemplate addressLookupRestTemplate,
            @Value("${parcel.tomtom.api-key:}") String apiKey,
            PublicQuoteRateLimiter rateLimiter) {
        this.restTemplate = addressLookupRestTemplate;
        this.apiKey = apiKey.trim();
        this.rateLimiter = rateLimiter;
    }

    record RouteRequest(String origin, String destination) {}
    record Point(double latitude, double longitude) {}
    record RouteResult(double distanceMeters, double durationSeconds, List<Point> points) {}

    @PostMapping("/api/booking-route")
    public RouteResult route(@RequestBody RouteRequest request, HttpServletRequest servletRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            rateLimiter.check(servletRequest.getRemoteAddr());
        }
        String origin = address(request.origin());
        String destination = address(request.destination());
        if (apiKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Route lookup is not configured.");
        }
        try {
            Point start = geocode(origin);
            Point end = geocode(destination);
            String locations = start.latitude() + "," + start.longitude() + ":"
                    + end.latitude() + "," + end.longitude();
            URI uri = UriComponentsBuilder.fromUriString("https://api.tomtom.com")
                    .pathSegment("routing", "1", "calculateRoute", "{locations}", "json")
                    .queryParam("key", "{key}").queryParam("travelMode", "car")
                    .buildAndExpand(locations, apiKey).encode().toUri();
            JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
            JsonNode route = response == null ? null : response.path("routes").path(0);
            JsonNode summary = route == null ? null : route.path("summary");
            if (summary == null || !summary.path("lengthInMeters").isNumber()
                    || !summary.path("travelTimeInSeconds").isNumber()
                    || summary.path("lengthInMeters").asDouble() < 0
                    || summary.path("travelTimeInSeconds").asDouble() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Route provider returned an invalid response.");
            }
            List<Point> points = new ArrayList<>();
            for (JsonNode leg : route.path("legs")) {
                for (JsonNode point : leg.path("points")) {
                    points.add(point(point, "latitude", "longitude"));
                }
            }
            if (points.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Route provider returned an invalid route.");
            }
            return new RouteResult(summary.path("lengthInMeters").asDouble(),
                    summary.path("travelTimeInSeconds").asDouble(), points);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Route lookup is temporarily unavailable.");
        }
    }

    private Point geocode(String address) {
        URI uri = UriComponentsBuilder.fromUriString("https://api.tomtom.com")
                .pathSegment("search", "2", "geocode", "{address}.json")
                .queryParam("key", "{key}").queryParam("limit", 1)
                .buildAndExpand(address, apiKey).encode().toUri();
        JsonNode response = restTemplate.getForObject(uri, JsonNode.class);
        JsonNode results = response == null ? null : response.path("results");
        if (results == null || !results.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Address provider returned an invalid response.");
        }
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "An address could not be found.");
        }
        return point(results.path(0).path("position"), "lat", "lon");
    }

    private static Point point(JsonNode value, String latitudeName, String longitudeName) {
        JsonNode latitude = value.path(latitudeName);
        JsonNode longitude = value.path(longitudeName);
        if (!latitude.isNumber() || !longitude.isNumber()
                || !Double.isFinite(latitude.asDouble()) || !Double.isFinite(longitude.asDouble())
                || Math.abs(latitude.asDouble()) > 90 || Math.abs(longitude.asDouble()) > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Route provider returned invalid coordinates.");
        }
        return new Point(latitude.asDouble(), longitude.asDouble());
    }

    private static String address(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter addresses of up to 255 characters.");
        }
        return value.trim();
    }
}
