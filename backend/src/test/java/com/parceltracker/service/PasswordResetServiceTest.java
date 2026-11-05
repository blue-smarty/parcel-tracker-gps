package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parceltracker.model.CustomerAccount;
import com.parceltracker.model.PasswordResetToken;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.repository.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {
    private static final String EMAIL = "ada@example.com";
    private static final String RAW_TOKEN = "Y".repeat(43);

    private final CustomerAccountRepository customers = mock(CustomerAccountRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @SuppressWarnings("unchecked")
    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailProvider =
            mock(org.springframework.beans.factory.ObjectProvider.class);
    private final PasswordResetService service = new PasswordResetService(
            customers, tokens, passwordEncoder, mailProvider, "support@example.com",
            "https://parcel.example/forgot-password.html");

    {
        when(mailProvider.getIfAvailable()).thenReturn(mailSender);
    }

    @Test
    void emailsOnlyAHashedShortLivedToken() throws Exception {
        when(customers.findById(EMAIL)).thenReturn(Optional.of(
                new CustomerAccount(EMAIL, "Ada", passwordEncoder.encode("old password value"))));
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.requestReset(" Ada@Example.com ");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();
        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        String text = mailCaptor.getValue().getText();
        Matcher matcher = Pattern.compile("\\?token=([A-Za-z0-9_-]+)").matcher(text);
        assertTrue(matcher.find());
        String rawToken = matcher.group(1);
        assertTrue(rawToken.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(MessageDigest.isEqual(saved.getTokenHash().getBytes(StandardCharsets.UTF_8),
                hash(rawToken).getBytes(StandardCharsets.UTF_8)));
        assertFalse(saved.getTokenHash().equals(rawToken));
        assertEquals(Duration.ofMinutes(30), Duration.between(saved.getCreatedAt(), saved.getExpiresAt()));
    }

    @Test
    void appliesResendCooldown() {
        when(customers.findById(EMAIL)).thenReturn(Optional.of(
                new CustomerAccount(EMAIL, "Ada", passwordEncoder.encode("old password value"))));
        when(tokens.findFirstByCustomerEmailOrderByCreatedAtDesc(EMAIL)).thenReturn(Optional.of(
                new PasswordResetToken("old-hash", EMAIL, Instant.now().minusSeconds(10),
                        Instant.now().plusSeconds(1790))));

        service.requestReset(EMAIL);

        verify(tokens, never()).save(any(PasswordResetToken.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void removesUnsentTokenWhenMailDeliveryFails() {
        when(customers.findById(EMAIL)).thenReturn(Optional.of(
                new CustomerAccount(EMAIL, "Ada", passwordEncoder.encode("old password value"))));
        doThrow(new MailSendException("mail unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        service.requestReset(EMAIL);

        verify(tokens, times(2)).deleteByCustomerEmail(EMAIL);
    }

    @Test
    void resetChangesPasswordAndInvalidatesTokens() throws Exception {
        String tokenHash = hash(RAW_TOKEN);
        CustomerAccount customer = new CustomerAccount(
                EMAIL, "Ada", passwordEncoder.encode("old password value"));
        when(tokens.findById(tokenHash)).thenReturn(Optional.of(new PasswordResetToken(
                tokenHash, EMAIL, Instant.now().minusSeconds(10), Instant.now().plusSeconds(600))));
        when(customers.findById(EMAIL)).thenReturn(Optional.of(customer));

        assertTrue(service.resetPassword(RAW_TOKEN, "a new sufficiently long password"));

        assertTrue(passwordEncoder.matches("a new sufficiently long password", customer.getPasswordHash()));
        verify(customers).save(customer);
        verify(tokens).deleteByCustomerEmail(EMAIL);
    }

    @Test
    void rejectsExpiredAndMalformedTokens() throws Exception {
        String expiredHash = hash(RAW_TOKEN);
        when(tokens.findById(expiredHash)).thenReturn(Optional.of(new PasswordResetToken(
                expiredHash, EMAIL, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(1))));

        assertFalse(service.resetPassword(RAW_TOKEN, "a new sufficiently long password"));
        assertFalse(service.resetPassword("malformed", "a new sufficiently long password"));

        verify(tokens).delete(any(PasswordResetToken.class));
        verify(customers, never()).save(any(CustomerAccount.class));
    }

    private static String hash(String token) throws Exception {
        return Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
