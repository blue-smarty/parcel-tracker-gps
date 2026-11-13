package com.parceltracker.controller;

import com.parceltracker.model.ServiceAreaSettings;
import com.parceltracker.repository.ServiceAreaSettingsRepository;
import com.parceltracker.service.AustralianCouncilLookup;
import com.parceltracker.service.CoveragePolygon;
import com.parceltracker.service.ServiceAreaException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServiceAreaSettingsController {
    public record Request(Boolean melbourneMetroOnly, String coverageMode, List<String> states,
            List<String> councils, List<List<List<Double>>> polygons) {}

    private final ServiceAreaSettingsRepository repository;
    private final JdbcTemplate jdbc;
    private final AustralianCouncilLookup councilLookup;
    private final ObjectMapper mapper;

    public ServiceAreaSettingsController(ServiceAreaSettingsRepository repository, JdbcTemplate jdbc,
            AustralianCouncilLookup councilLookup, ObjectMapper mapper) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.councilLookup = councilLookup;
        this.mapper = mapper;
    }

    @GetMapping("/api/admin/service-area")
    public ServiceAreaSettings get() {
        return repository.findById(ServiceAreaSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Service area settings are missing"));
    }

    @GetMapping("/api/admin/service-area/councils")
    public List<AustralianCouncilLookup.Council> councils() {
        return councilLookup.all();
    }

    @PutMapping("/api/admin/service-area")
    @Transactional
    public ServiceAreaSettings update(@RequestBody Request request, Authentication authentication) {
        ServiceAreaSettings settings = repository.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("Service area settings are missing"));
        String mode = request.coverageMode();
        if (mode == null && request.melbourneMetroOnly() != null) {
            mode = request.melbourneMetroOnly() ? "MELBOURNE" : "UNRESTRICTED";
        }
        if (mode == null || !Set.of("UNRESTRICTED", "MELBOURNE", "CUSTOM").contains(mode)) throw invalid();
        List<String> states = request.states() == null ? List.of() : request.states();
        List<String> councils = request.councils() == null ? List.of() : request.councils();
        List<List<List<Double>>> polygons = request.polygons() == null ? List.of() : request.polygons();
        if (states.size() > 8 || councils.size() > 600 || polygons.size() > 20
                || states.stream().anyMatch(value -> value == null || !value.matches("[1-8]"))
                || councils.stream().anyMatch(value -> value == null || !value.matches("[0-9]{5}"))
                || states.stream().distinct().count() != states.size()
                || councils.stream().distinct().count() != councils.size()) throw invalid();
        polygons.forEach(CoveragePolygon::validate);
        if ("CUSTOM".equals(mode) && states.isEmpty() && councils.isEmpty() && polygons.isEmpty()) throw invalid();
        if ("CUSTOM".equals(mode) && !councils.isEmpty()) {
            Set<String> valid = councilLookup.all().stream().map(AustralianCouncilLookup.Council::code)
                    .collect(Collectors.toSet());
            if (!valid.containsAll(councils)) throw invalid();
        }
        boolean changed = !mode.equals(settings.getCoverageMode()) || !states.equals(settings.getStates())
                || !councils.equals(settings.getCouncils()) || !polygons.equals(settings.getPolygons());
        if (changed) {
            settings.setCoverageMode(mode);
            settings.setMelbourneMetroOnly("MELBOURNE".equals(mode));
            settings.setStates(states);
            settings.setCouncils(councils);
            settings.setPolygons(polygons);
            repository.save(settings);
            try {
                jdbc.update("INSERT INTO service_area_audit (changed_at, changed_by, melbourne_metro_only, coverage_settings) "
                                + "VALUES (?, ?, ?, CAST(? AS jsonb))",
                        java.sql.Timestamp.from(Instant.now()), authentication.getName(),
                        settings.isMelbourneMetroOnly(), mapper.writeValueAsString(settings));
            } catch (JacksonException error) {
                throw new IllegalStateException("Could not audit service area settings", error);
            }
        }
        return settings;
    }

    private static ServiceAreaException invalid() {
        return new ServiceAreaException(HttpStatus.BAD_REQUEST, "Invalid or empty service area selection.");
    }
}
