package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.IntegrationSettings;
import com.parceltracker.repository.IntegrationSettingsRepository;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class TomTomSettingsServiceTest {
    private static final String MASTER = Base64.getEncoder().encodeToString(new byte[32]);
    private final IntegrationSettingsRepository repository = mock(IntegrationSettingsRepository.class);
    private final TomTomSettingsService service = new TomTomSettingsService(repository, MASTER, "route-env", "search-env");

    @Test
    void encryptsKeyAndUsesItInsteadOfEnvironmentForBothConsumers() {
        service.save("private-api-key");
        ArgumentCaptor<IntegrationSettings> saved = ArgumentCaptor.forClass(IntegrationSettings.class);
        verify(repository).save(saved.capture());
        assertFalse(saved.getValue().getEncryptedKey().contains("private-api-key"));
        when(repository.findById("tomtom")).thenReturn(Optional.of(saved.getValue()));
        assertEquals("private-api-key", service.routeKey());
        assertEquals("private-api-key", service.searchKey());

        service.save("private-api-key");
        verify(repository, times(2)).save(saved.capture());
        assertNotEquals(saved.getAllValues().get(0).getEncryptedKey(), saved.getValue().getEncryptedKey());
    }

    @Test
    void usesEnvironmentWhenNoKeyIsSavedAndRemovesSavedOverride() {
        when(repository.findById("tomtom")).thenReturn(Optional.empty());
        assertEquals("route-env", service.routeKey());
        assertEquals("search-env", service.searchKey());
        service.remove();
        verify(repository).deleteById("tomtom");
    }

    @Test
    void failsClosedForMissingOrWrongEncryptionKey() {
        TomTomSettingsService missing = new TomTomSettingsService(repository, "", "fallback", "fallback");
        assertThrows(ResponseStatusException.class, () -> missing.save("key"));
        verify(repository, never()).save(any());

        service.save("key");
        ArgumentCaptor<IntegrationSettings> saved = ArgumentCaptor.forClass(IntegrationSettings.class);
        verify(repository).save(saved.capture());
        when(repository.findById("tomtom")).thenReturn(Optional.of(saved.getValue()));
        byte[] other = new byte[32];
        other[0] = 1;
        TomTomSettingsService wrong = new TomTomSettingsService(repository,
                Base64.getEncoder().encodeToString(other), "fallback", "fallback");
        assertThrows(ResponseStatusException.class, wrong::routeKey);
        assertTrue(wrong.status().saved());
        assertFalse(wrong.status().savedUsable());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(ResponseStatusException.class, () -> service.save(" "));
        assertThrows(ResponseStatusException.class, () -> service.save("key with spaces"));
        assertThrows(ResponseStatusException.class, () -> service.save("x".repeat(513)));
        verify(repository, never()).save(any());
    }
}
