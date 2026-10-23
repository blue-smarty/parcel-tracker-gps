package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DemoPricingServiceTest {
    private final DemoPricingService pricing = new DemoPricingService();

    @Test
    void scheduledEstimateUsesBaseDistanceAndWeight() {
        assertEquals(18.0, pricing.estimate(10.0, 2.0, "SCHEDULED"));
    }

    @Test
    void sameDayEstimateAppliesExpediteMultiplier() {
        assertEquals(31.5, pricing.estimate(10.0, 2.0, "SAME_DAY"));
    }
}
