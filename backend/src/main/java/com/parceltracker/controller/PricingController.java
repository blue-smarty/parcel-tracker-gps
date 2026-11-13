package com.parceltracker.controller;

import com.parceltracker.model.PricingSettings;
import com.parceltracker.repository.PricingSettingsRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PricingController {
    private static final double MAX_RATE = 100_000.0;

    public record PricingRequest(Double baseFee, Double perKmRate, Double perKgRate, Double sameDayMultiplier,
            String pricingMode, List<PricingSettings.RadiusBand> radiusBands) { }

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
        String pricingMode = request.pricingMode() == null ? "ROUTE" : request.pricingMode();
        if (!"ROUTE".equals(pricingMode) && !"RADIUS".equals(pricingMode)) {
            throw invalid("Pricing mode must be ROUTE or RADIUS.");
        }
        List<PricingSettings.RadiusBand> radiusBands = request.radiusBands() == null
                ? List.of() : request.radiusBands();
        validateRadiusBands(pricingMode, radiusBands);
        PricingSettings settings = repository.findById(PricingSettings.SINGLETON_ID)
                .orElseGet(PricingSettings::defaults);
        settings.setBaseFee(request.baseFee());
        settings.setPerKmRate(request.perKmRate());
        settings.setPerKgRate(request.perKgRate());
        settings.setSameDayMultiplier(request.sameDayMultiplier());
        settings.setPricingMode(pricingMode);
        settings.setRadiusBands(radiusBands);
        return repository.save(settings);
    }

    private static void validateRadiusBands(String pricingMode, List<PricingSettings.RadiusBand> bands) {
        if (bands.size() > 20 || ("RADIUS".equals(pricingMode) && bands.isEmpty())) {
            throw invalid("Radius pricing requires 1-20 distance bands.");
        }
        Double previous = 0.0;
        for (int i = 0; i < bands.size(); i++) {
            PricingSettings.RadiusBand band = bands.get(i);
            if (band == null || band.distanceFee() == null || !Double.isFinite(band.distanceFee())
                    || band.distanceFee() < 0 || band.distanceFee() > MAX_RATE) {
                throw invalid("Each distance band requires a valid distance charge.");
            }
            if (band.upToKm() == null) {
                if (i != bands.size() - 1) throw invalid("Only the last distance band can be open-ended.");
            } else {
                validate("Band maximum distance", band.upToKm(), 0.001, 20_000);
                if (band.upToKm() <= previous) throw invalid("Distance band limits must be strictly increasing.");
                previous = band.upToKm();
            }
        }
        if ("RADIUS".equals(pricingMode) && bands.get(bands.size() - 1).upToKm() != null) {
            throw invalid("The last distance band must be open-ended.");
        }
    }

    private static void validate(String field, Double value, double minimum, double maximum) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            throw invalid(field + " must be between " + minimum + " and " + maximum + ".");
        }
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
