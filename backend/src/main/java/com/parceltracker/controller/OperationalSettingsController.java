package com.parceltracker.controller;

import com.parceltracker.model.OperationalSettings;
import com.parceltracker.service.ConfigurationHistoryService;
import com.parceltracker.service.OperationalSettingsService;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class OperationalSettingsController {
    public record Request(Integer maxItemsPerBooking, Integer maxPackageLines, Integer maxQuantityPerItem,
            Double maxItemWeightKg, Double maxDimensionCm, LocalTime bookingCutoff, String timeZone,
            Integer maxScheduleDaysAhead, List<String> deliveryWindows, Boolean staleAssignmentEnabled,
            Integer staleAssignmentMinutes, Boolean unassignStaleAssignments) { }

    private final OperationalSettingsService settings;
    private final ConfigurationHistoryService history;

    public OperationalSettingsController(OperationalSettingsService settings, ConfigurationHistoryService history) {
        this.settings = settings;
        this.history = history;
    }

    @GetMapping("/api/operational-settings")
    public OperationalSettings publicSettings() {
        return settings.get();
    }

    @GetMapping("/api/admin/operational-settings")
    public OperationalSettings adminSettings() {
        return settings.get();
    }

    @PutMapping("/api/admin/operational-settings")
    @Transactional
    public OperationalSettings update(@RequestBody Request request, Authentication authentication) {
        OperationalSettings before = history.snapshot(settings.get(), OperationalSettings.class);
        OperationalSettings updated = new OperationalSettings();
        updated.setMaxItemsPerBooking(request.maxItemsPerBooking() == null ? before.getMaxItemsPerBooking() : request.maxItemsPerBooking());
        updated.setMaxPackageLines(request.maxPackageLines() == null ? before.getMaxPackageLines() : request.maxPackageLines());
        updated.setMaxQuantityPerItem(request.maxQuantityPerItem() == null ? before.getMaxQuantityPerItem() : request.maxQuantityPerItem());
        updated.setMaxItemWeightKg(request.maxItemWeightKg() == null ? before.getMaxItemWeightKg() : request.maxItemWeightKg());
        updated.setMaxDimensionCm(request.maxDimensionCm() == null ? before.getMaxDimensionCm() : request.maxDimensionCm());
        updated.setBookingCutoff(request.bookingCutoff() == null ? before.getBookingCutoff() : request.bookingCutoff());
        updated.setTimeZone(request.timeZone() == null ? before.getTimeZone() : request.timeZone().trim());
        updated.setMaxScheduleDaysAhead(request.maxScheduleDaysAhead() == null ? before.getMaxScheduleDaysAhead() : request.maxScheduleDaysAhead());
        updated.setDeliveryWindows(request.deliveryWindows() == null ? before.getDeliveryWindows() : request.deliveryWindows());
        updated.setStaleAssignmentEnabled(request.staleAssignmentEnabled() == null
                ? before.isStaleAssignmentEnabled() : request.staleAssignmentEnabled());
        updated.setStaleAssignmentMinutes(request.staleAssignmentMinutes() == null
                ? before.getStaleAssignmentMinutes() : request.staleAssignmentMinutes());
        updated.setUnassignStaleAssignments(request.unassignStaleAssignments() == null
                ? before.isUnassignStaleAssignments() : request.unassignStaleAssignments());
        try {
            OperationalSettingsService.validateSettings(updated);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
        OperationalSettings saved = settings.save(updated);
        history.record("OPERATIONAL_LIMITS", before, saved, authentication.getName());
        return saved;
    }
}
