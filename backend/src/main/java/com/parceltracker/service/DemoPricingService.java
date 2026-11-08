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
        BigDecimal subtotal = BigDecimal.valueOf(settings.getBaseFee())
                .add(BigDecimal.valueOf(distanceKm).multiply(BigDecimal.valueOf(settings.getPerKmRate())))
                .add(BigDecimal.valueOf(weightKg).multiply(BigDecimal.valueOf(settings.getPerKgRate())));
        BigDecimal multiplier = "SAME_DAY".equals(deliverySpeed)
                ? BigDecimal.valueOf(settings.getSameDayMultiplier()) : BigDecimal.ONE;
        return subtotal.multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
