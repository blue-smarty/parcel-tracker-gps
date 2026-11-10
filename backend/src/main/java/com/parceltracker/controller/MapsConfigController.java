package com.parceltracker.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MapsConfigController {
    private final String apiKey;

    public MapsConfigController(@Value("${parcel.maps.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    /** Browser key for the Google Maps JavaScript SDK; empty when route lookup is not configured. */
    @GetMapping("/api/maps-config")
    public Map<String, String> config() {
        return Map.of("apiKey", apiKey);
    }
}
