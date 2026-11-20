package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.parceltracker.model.PricingSettings;
import com.parceltracker.model.ParcelItem;
import com.parceltracker.repository.PricingSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DemoPricingServiceTest {
    private final PricingSettingsRepository settings = Mockito.mock(PricingSettingsRepository.class);
    private final DemoPricingService pricing = new DemoPricingService(settings);

    @Test
    void scheduledEstimateUsesBaseDistanceAndWeight() {
        when(settings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertEquals(18.0, pricing.estimate(10.0, 2.0, "SCHEDULED"));
    }

    @Test
    void sameDayEstimateAppliesExpediteMultiplier() {
        when(settings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        assertEquals(31.5, pricing.estimate(10.0, 2.0, "SAME_DAY"));
    }

    @Test
    void estimatesUseTheSavedAdminRates() {
        when(settings.findById(PricingSettings.SINGLETON_ID))
                .thenReturn(Optional.of(new PricingSettings(7, 2, 1.5, 2)));
        assertEquals(30.0, pricing.estimate(10, 2, "SCHEDULED"));
        assertEquals(60.0, pricing.estimate(10, 2, "SAME_DAY"));
    }

    @Test
    void radiusPricingUsesFirstMatchingBandAndRetainsWeightAndSpeedCharges() {
        PricingSettings radius = new PricingSettings(5, 99, 0.5, 1.75);
        radius.setPricingMode("RADIUS");
        radius.setRadiusBands(java.util.List.of(
                new PricingSettings.RadiusBand(5.0, 3.0),
                new PricingSettings.RadiusBand(null, 8.0)));
        when(settings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.of(radius));

        assertEquals(9.0, pricing.estimate(20, 2, "SCHEDULED", 4.5));
        assertEquals(14.0, pricing.estimate(20, 2, "SCHEDULED", 12.0));
        assertEquals(15.75, pricing.estimate(20, 2, "SAME_DAY", 4.5));
    }

    @Test
    void radiusPricingRequiresStraightLineDistance() {
        PricingSettings radius = new PricingSettings(5, 1.2, 0.5, 1.75);
        radius.setPricingMode("RADIUS");
        radius.setRadiusBands(java.util.List.of(new PricingSettings.RadiusBand(null, 3.0)));
        when(settings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.of(radius));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> pricing.estimate(10, 2, "SCHEDULED", null));
    }

    @Test
    void chargeableWeightIsSelectedForEachItemBeforeQuantityIsApplied() {
        PricingSettings configured = new PricingSettings(0, 0, 1, 1);
        configured.setCubicDivisor(5000);
        var items = java.util.List.of(new ParcelItem(1.0, 50.0, 40.0, 25.0, 2),
                new ParcelItem(12.0, 10.0, 10.0, 10.0, 1));
        when(settings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.of(configured));
        configured.setWeightMode("ACTUAL");
        assertEquals(14.0, pricing.estimateParcel(1.0, 14.0, "SCHEDULED", null, items));
        configured.setWeightMode("CUBIC");
        assertEquals(20.2, pricing.estimateParcel(1.0, 14.0, "SCHEDULED", null, items));
        configured.setWeightMode("GREATER");
        assertEquals(32.0, pricing.estimateParcel(1.0, 14.0, "SCHEDULED", null, items));
    }

    @Test
    void cubicPricingRejectsLegacyBookingsWithoutItems() {
        PricingSettings configured = PricingSettings.defaults();
        configured.setWeightMode("CUBIC");
        when(settings.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.of(configured));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> pricing.estimateParcel(10.0, 2.0, "SCHEDULED", null, null));
    }
}
