package com.parceltracker.service;

import com.parceltracker.model.PricingSettings;
import com.parceltracker.repository.PricingSettingsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class DemoPricingService {
    private final PricingSettingsRepository settingsRepository;

    public DemoPricingService(PricingSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public double estimate(double distanceKm, double weightKg, String deliverySpeed) {
        PricingSettings settings = settingsRepository.findById(PricingSettings.SINGLETON_ID)
                .orElseGet(PricingSettings::defaults);
        return estimate(settings, distanceKm, weightKg, deliverySpeed, null);
    }

    public double estimate(double distanceKm, double weightKg, String deliverySpeed, Double straightLineDistanceKm) {
        PricingSettings settings = settingsRepository.findById(PricingSettings.SINGLETON_ID)
                .orElseGet(PricingSettings::defaults);
        return estimate(settings, distanceKm, weightKg, deliverySpeed, straightLineDistanceKm);
    }

    private static double estimate(PricingSettings settings, double distanceKm, double weightKg,
            String deliverySpeed, Double straightLineDistanceKm) {
        double distanceCharge = distanceKm * settings.getPerKmRate();
        if ("RADIUS".equals(settings.getPricingMode())) {
            if (straightLineDistanceKm == null || !Double.isFinite(straightLineDistanceKm)
                    || straightLineDistanceKm < 0) {
                throw new IllegalArgumentException("Pickup and delivery coordinates are required for radius pricing.");
            }
            var band = (settings.getRadiusBands() == null ? java.util.stream.Stream.<PricingSettings.RadiusBand>empty()
                    : settings.getRadiusBands().stream())
                    .filter(candidate -> candidate.upToKm() == null || straightLineDistanceKm <= candidate.upToKm())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Radius pricing bands do not cover the trip."));
            distanceCharge = band.distanceFee();
        }
        BigDecimal subtotal = BigDecimal.valueOf(settings.getBaseFee())
                .add(BigDecimal.valueOf(distanceCharge))
                .add(BigDecimal.valueOf(weightKg).multiply(BigDecimal.valueOf(settings.getPerKgRate())));
        BigDecimal multiplier = "SAME_DAY".equals(deliverySpeed)
                ? BigDecimal.valueOf(settings.getSameDayMultiplier()) : BigDecimal.ONE;
        return subtotal.multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
