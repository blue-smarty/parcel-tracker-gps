package com.parceltracker.controller;

import com.parceltracker.model.ServiceAreaSettings;
import com.parceltracker.repository.ServiceAreaSettingsRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceAreaSettingsController {
    public record Request(@NotNull Boolean melbourneMetroOnly) {}

    private final ServiceAreaSettingsRepository repository;
    private final JdbcTemplate jdbc;

    public ServiceAreaSettingsController(ServiceAreaSettingsRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/admin/service-area")
    public ServiceAreaSettings get() {
        return repository.findById(ServiceAreaSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Service area settings are missing"));
    }

    @PutMapping("/api/admin/service-area")
    @Transactional
    public ServiceAreaSettings update(@Valid @RequestBody Request request, Authentication authentication) {
        ServiceAreaSettings settings = repository.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("Service area settings are missing"));
        if (settings.isMelbourneMetroOnly() != request.melbourneMetroOnly()) {
            settings.setMelbourneMetroOnly(request.melbourneMetroOnly());
            repository.save(settings);
            jdbc.update("INSERT INTO service_area_audit (changed_at, changed_by, melbourne_metro_only) VALUES (?, ?, ?)",
                    java.sql.Timestamp.from(Instant.now()), authentication.getName(), request.melbourneMetroOnly());
        }
        return settings;
    }
}
