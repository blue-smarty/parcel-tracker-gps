package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.parceltracker.model.OperationalSettings;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelItem;
import com.parceltracker.repository.OperationalSettingsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OperationalSettingsServiceTest {
    @Test
    void validatesLocalBookingWindowsCutoffAndTimezone() {
        OperationalSettings settings = OperationalSettings.defaults();
        OperationalSettingsService service = new OperationalSettingsService(mock(OperationalSettingsRepository.class));
        Instant now = Instant.parse("2026-11-20T00:00:00Z");
        LocalDate melbourneToday = LocalDate.of(2026, 11, 20);
        assertDoesNotThrow(() -> service.validateSchedule(settings, melbourneToday, "12:00-14:00", now));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateSchedule(settings, melbourneToday, "08:00-10:00", now));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateSchedule(settings, melbourneToday.plusDays(31), "12:00-14:00", now));
    }

    @Test
    void validatesConfiguredLimitsAgainstEveryItemAndQuantity() {
        OperationalSettings settings = OperationalSettings.defaults();
        settings.setMaxItemsPerBooking(2);
        OperationalSettingsRepository repository = mock(OperationalSettingsRepository.class);
        when(repository.findById(OperationalSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        OperationalSettingsService service = new OperationalSettingsService(repository);
        Parcel parcel = new Parcel();
        parcel.setItems(List.of(new ParcelItem(1.0, 10.0, 10.0, 10.0, 2)));
        assertDoesNotThrow(() -> service.validateParcel(parcel));
        parcel.setItems(List.of(new ParcelItem(1.0, 10.0, 10.0, 10.0, 3)));
        assertThrows(IllegalArgumentException.class, () -> service.validateParcel(parcel));
    }

    @Test
    void rejectsOverlappingWindowsAndUnknownTimezones() {
        OperationalSettings settings = OperationalSettings.defaults();
        settings.setDeliveryWindows(List.of("08:00-10:00", "09:30-11:00"));
        assertThrows(IllegalArgumentException.class, () -> OperationalSettingsService.validateSettings(settings));
        settings.setDeliveryWindows(List.of("08:00-10:00"));
        settings.setTimeZone("Invalid/Timezone");
        assertThrows(IllegalArgumentException.class, () -> OperationalSettingsService.validateSettings(settings));
    }
}
