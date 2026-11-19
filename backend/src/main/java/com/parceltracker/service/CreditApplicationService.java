package com.parceltracker.service;

import com.parceltracker.model.CreditApplication;
import com.parceltracker.model.CreditAccountEvent;
import com.parceltracker.model.CustomerAccount;
import com.parceltracker.repository.CreditApplicationRepository;
import com.parceltracker.repository.CreditAccountEventRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CreditApplicationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private final CustomerAccountRepository customers;
    private final CreditApplicationRepository applications;
    private final CreditAccountEventRepository events;

    public record Invitation(String token, Instant expiresAt) {}
    public record Details(String businessName, String contactName, String contactEmail,
                          String contactPhone, BigDecimal requestedLimit) {}

    public CreditApplicationService(CustomerAccountRepository customers, CreditApplicationRepository applications,
                                    CreditAccountEventRepository events) {
        this.customers = customers;
        this.applications = applications;
        this.events = events;
    }

    @Transactional
    public Invitation invite(String suppliedEmail) {
        String email = suppliedEmail.trim().toLowerCase(Locale.ROOT);
        CustomerAccount customer = lockCustomer(email);
        if (customer.isCreditApproved() || !applications.findByCustomerEmailAndStatus(email, "SUBMITTED").isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer already has credit or a pending application.");
        }
        applications.findByCustomerEmailAndStatus(email, "INVITED").forEach(CreditApplication::replaceInvitation);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofDays(7));
        applications.save(new CreditApplication(email, hash(rawToken), now, expiry));
        return new Invitation(rawToken, expiry);
    }

    @Transactional
    public void submit(String email, String rawToken, Details details) {
        if (rawToken == null || !TOKEN.matcher(rawToken).matches()) invalidInvitation();
        CustomerAccount customer = lockCustomer(email);
        CreditApplication found = applications.findByTokenHashAndCustomerEmail(hash(rawToken), email)
                .orElseThrow(CreditApplicationService::invalidInvitationException);
        CreditApplication application = applications.lockById(found.getId())
                .orElseThrow(CreditApplicationService::invalidInvitationException);
        Instant now = Instant.now();
        if (!"INVITED".equals(application.getStatus()) || !application.getExpiresAt().isAfter(now)
                || customer.isCreditApproved()) {
            invalidInvitation();
        }
        application.submit(details.businessName().trim(), details.contactName().trim(),
                details.contactEmail().trim(), details.contactPhone().trim(), details.requestedLimit(), now);
    }

    @Transactional(readOnly = true)
    public List<CreditApplication> list() {
        return applications.findAllByOrderByInvitedAtDesc();
    }

    @Transactional
    public void review(UUID id, boolean approve, String reviewer) {
        String customerEmail = applications.findCustomerEmailById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));
        CustomerAccount customer = lockCustomer(customerEmail);
        CreditApplication application = applications.lockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));
        if (!"SUBMITTED".equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application has already been reviewed or is not submitted.");
        }
        Instant now = Instant.now();
        application.review(approve, reviewer, now);
        if (approve) {
            customer.setCreditApproved(true);
            events.save(new CreditAccountEvent(customer.getEmail(), "APPROVED", reviewer, now));
        }
    }

    @Transactional(readOnly = true)
    public List<String> approvedAccounts() {
        return customers.findByCreditApprovedTrueOrderByEmailAsc().stream().map(CustomerAccount::getEmail).toList();
    }

    @Transactional
    public void revoke(String suppliedEmail, String reviewer) {
        String email = suppliedEmail.trim().toLowerCase(Locale.ROOT);
        CustomerAccount customer = lockCustomer(email);
        if (!customer.isCreditApproved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer does not have approved credit.");
        }
        customer.setCreditApproved(false);
        events.save(new CreditAccountEvent(email, "REVOKED", reviewer, Instant.now()));
    }

    private CustomerAccount lockCustomer(String email) {
        return customers.lockByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer account not found."));
    }

    private static void invalidInvitation() {
        throw invalidInvitationException();
    }

    private static ResponseStatusException invalidInvitationException() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation is invalid, expired or already used.");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
