package com.parceltracker.controller;

import com.parceltracker.service.TomTomSettingsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminIntegrationController {
    private final TomTomSettingsService settings;

    public AdminIntegrationController(TomTomSettingsService settings) { this.settings = settings; }

    public record KeyRequest(String apiKey) {}

    @GetMapping("/api/admin/integrations/tomtom")
    public ResponseEntity<TomTomSettingsService.Status> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settings.status());
    }

    @PutMapping("/api/admin/integrations/tomtom")
    public ResponseEntity<TomTomSettingsService.Status> save(@RequestBody KeyRequest request) {
        settings.save(request.apiKey());
        return get();
    }

    @DeleteMapping("/api/admin/integrations/tomtom")
    public ResponseEntity<TomTomSettingsService.Status> remove() {
        settings.remove();
        return get();
    }
}
