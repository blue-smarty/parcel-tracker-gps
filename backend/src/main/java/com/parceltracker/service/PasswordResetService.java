package com.parceltracker.service;

import com.parceltracker.model.CustomerAccount;
import com.parceltracker.model.PasswordResetToken;
import com.parceltracker.repository.CustomerAccountRepository;
import com.parceltracker.repository.PasswordResetTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final Duration REQUEST_COOLDOWN = Duration.ofMinutes(1);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern VALID_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final CustomerAccountRepository customers;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String resetUrl;

    public PasswordResetService(CustomerAccountRepository customers, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder, JavaMailSender mailSender,
                                @Value("${parcel.auth.password-reset.from:}") String fromAddress,
                                @Value("${parcel.auth.password-reset.url:http://localhost:8080/forgot-password.html}")
                                        String resetUrl) {
        this.customers = customers;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.resetUrl = resetUrl;
    }

    @Transactional
    public void requestReset(String submittedEmail) {
        String email = submittedEmail.trim().toLowerCase(Locale.ROOT);
        Optional<CustomerAccount> account = customers.findById(email);
        if (account.isEmpty()) return;

        Instant now = Instant.now();
        Optional<PasswordResetToken> previous = tokens.findFirstByCustomerEmailOrderByCreatedAtDesc(email);
        if (previous.isPresent() && previous.get().getCreatedAt().isAfter(now.minus(REQUEST_COOLDOWN))) return;

        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        tokens.deleteByCustomerEmail(email);
        tokens.save(new PasswordResetToken(hash(token), email, now, now.plus(TOKEN_LIFETIME)));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Reset your ParcelPilot password");
        message.setText("Use this link within 30 minutes to reset your customer account password:\n\n"
                + resetUrl + "?token=" + token
                + "\n\nIf you did not request a password reset, you can ignore this email.");
        try {
            mailSender.send(message);
        } catch (RuntimeException e) {
            tokens.deleteByCustomerEmail(email);
            log.error("Could not send customer password reset email", e);
        }
    }

    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || !VALID_TOKEN.matcher(rawToken).matches()) return false;
        String tokenHash = hash(rawToken);
        Optional<PasswordResetToken> token = tokens.findById(tokenHash);
        Instant now = Instant.now();
        if (token.isEmpty() || !token.get().getExpiresAt().isAfter(now)) {
            token.ifPresent(tokens::delete);
            return false;
        }

        Optional<CustomerAccount> account = customers.findById(token.get().getCustomerEmail());
        if (account.isEmpty()) {
            tokens.deleteByCustomerEmail(token.get().getCustomerEmail());
            return false;
        }
        CustomerAccount customer = account.get();
        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        customers.save(customer);
        tokens.deleteByCustomerEmail(customer.getEmail());
        return true;
    }

    private static String hash(String token) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
