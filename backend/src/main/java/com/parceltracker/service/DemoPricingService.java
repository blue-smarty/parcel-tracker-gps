package com.parceltracker.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class DemoPricingService {
    public double estimate(double distanceKm, double weightKg, String deliverySpeed) {
        double speedMultiplier = "SAME_DAY".equals(deliverySpeed) ? 1.75 : 1.0;
        return BigDecimal.valueOf((5.0 + distanceKm * 1.2 + weightKg * 0.5) * speedMultiplier)
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
