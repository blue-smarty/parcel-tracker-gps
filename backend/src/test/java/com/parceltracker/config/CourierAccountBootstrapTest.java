package com.parceltracker.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parceltracker.model.CourierAccount;
import com.parceltracker.repository.CourierAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CourierAccountBootstrapTest {
    private final CourierAccountRepository accounts = org.mockito.Mockito.mock(CourierAccountRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void bootstrapsHashedPasswordOnlyWhenAccountDoesNotExist() throws Exception {
        when(accounts.existsById("courier-1")).thenReturn(false);

        new CourierAccountBootstrap(accounts, encoder, "courier-1", "strong-password-123")
                .run(new DefaultApplicationArguments());

        var account = org.mockito.ArgumentCaptor.forClass(CourierAccount.class);
        verify(accounts).save(account.capture());
        assertNotEquals("strong-password-123", account.getValue().getPasswordHash());
        assertTrue(encoder.matches("strong-password-123", account.getValue().getPasswordHash()));
    }

    @Test
    void doesNotOverwriteExistingBootstrapAccount() throws Exception {
        when(accounts.existsById("courier-1")).thenReturn(true);

        new CourierAccountBootstrap(accounts, encoder, "courier-1", "strong-password-123")
                .run(new DefaultApplicationArguments());

        verify(accounts, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
