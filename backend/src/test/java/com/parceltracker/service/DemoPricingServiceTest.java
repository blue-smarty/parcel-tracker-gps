package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.parceltracker.model.PricingSettings;
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
}
