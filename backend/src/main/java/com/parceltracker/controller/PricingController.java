package com.parceltracker.controller;

import com.parceltracker.model.PricingSettings;
import com.parceltracker.repository.PricingSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PricingController {
    private static final double MAX_RATE = 100_000.0;

    public record PricingRequest(Double baseFee, Double perKmRate, Double perKgRate, Double sameDayMultiplier) { }

    private final PricingSettingsRepository repository;

    public PricingController(PricingSettingsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/pricing")
    public PricingSettings get() {
        return repository.findById(PricingSettings.SINGLETON_ID).orElseGet(PricingSettings::defaults);
    }

    @PutMapping("/api/admin/pricing")
    public PricingSettings update(@RequestBody PricingRequest request) {
        validate("Base fee", request.baseFee(), 0, MAX_RATE);
        validate("Per-kilometer rate", request.perKmRate(), 0, MAX_RATE);
        validate("Per-kilogram rate", request.perKgRate(), 0, MAX_RATE);
        validate("Same-day multiplier", request.sameDayMultiplier(), 1, 10);
        PricingSettings settings = repository.findById(PricingSettings.SINGLETON_ID)
                .orElseGet(PricingSettings::defaults);
        settings.setBaseFee(request.baseFee());
        settings.setPerKmRate(request.perKmRate());
        settings.setPerKgRate(request.perKgRate());
        settings.setSameDayMultiplier(request.sameDayMultiplier());
        return repository.save(settings);
    }

    private static void validate(String field, Double value, double minimum, double maximum) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " must be between " + minimum + " and " + maximum + ".");
        }
    }
}
