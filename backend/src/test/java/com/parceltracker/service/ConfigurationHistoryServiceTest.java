package com.parceltracker.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.parceltracker.model.OperationalSettings;
import com.parceltracker.repository.BrandingRepository;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.OperationalSettingsRepository;
import com.parceltracker.repository.PricingSettingsRepository;
import com.parceltracker.repository.ServiceAreaSettingsRepository;
import com.parceltracker.repository.ServiceLevelSettingsRepository;
import com.parceltracker.repository.SmtpSettingsRepository;
import com.parceltracker.repository.WebsiteThemeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class ConfigurationHistoryServiceTest {
    @Test
    void recordsChangedOperationalSettingsAsJsonSnapshots() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ConfigurationHistoryService history = new ConfigurationHistoryService(jdbc, new ObjectMapper(),
                mock(PricingSettingsRepository.class), mock(SmtpSettingsRepository.class),
                mock(ServiceLevelSettingsRepository.class), mock(OperationalSettingsRepository.class),
                mock(CourierAccountRepository.class), mock(ServiceAreaSettingsRepository.class),
                mock(BrandingRepository.class), mock(WebsiteThemeRepository.class));
        OperationalSettings managedBefore = OperationalSettings.defaults();
        OperationalSettings before = history.snapshot(managedBefore, OperationalSettings.class);
        managedBefore.setMaxItemsPerBooking(150);

        history.record("OPERATIONAL_LIMITS", before, managedBefore, "admin");

        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
