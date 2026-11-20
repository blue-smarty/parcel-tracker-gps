package com.parceltracker.controller;

import com.parceltracker.model.ServiceLevelSettings;
import com.parceltracker.repository.ServiceLevelSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import com.parceltracker.service.ConfigurationHistoryService;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class ServiceLevelSettingsController {
    public record Request(Integer generalMinutes, Integer expressMinutes, String targetStart) { }

    private final ServiceLevelSettingsRepository repository;
    private final ConfigurationHistoryService history;

    public ServiceLevelSettingsController(ServiceLevelSettingsRepository repository, ConfigurationHistoryService history) {
        this.repository = repository;
        this.history = history;
    }

    @GetMapping("/api/service-levels")
    public ServiceLevelSettings get() {
        return repository.findById(ServiceLevelSettings.SINGLETON_ID).orElseGet(ServiceLevelSettings::new);
    }

    @PutMapping("/api/admin/service-levels")
    @Transactional
    public ServiceLevelSettings update(@RequestBody Request request, Authentication authentication) {
        if (request.generalMinutes() == null || request.expressMinutes() == null
                || request.generalMinutes() < 1 || request.generalMinutes() > 10080
                || request.expressMinutes() < 1 || request.expressMinutes() > request.generalMinutes()
                || !("BOOKING".equals(request.targetStart()) || "PICKUP".equals(request.targetStart()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Targets must be 1-10080 minutes, Express cannot exceed General, and the clock must start at booking or pickup.");
        }
        ServiceLevelSettings before = history.snapshot(
                repository.findById(ServiceLevelSettings.SINGLETON_ID).orElse(null), ServiceLevelSettings.class);
        ServiceLevelSettings settings = get();
        settings.setGeneralMinutes(request.generalMinutes());
        settings.setExpressMinutes(request.expressMinutes());
        settings.setTargetStart(request.targetStart());
        ServiceLevelSettings saved = repository.save(settings);
        history.record("SERVICE_LEVELS", before, saved, authentication.getName());
        return saved;
    }
}
