package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.CreditAccountEvent;
import com.parceltracker.model.CreditApplication;
import com.parceltracker.model.CustomerAccount;
import com.parceltracker.repository.CreditAccountEventRepository;
import com.parceltracker.repository.CreditApplicationRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class CreditApplicationServiceTest {
    private static final String EMAIL = "client@example.com";
    private static final String TOKEN = "A".repeat(43);
    private final CustomerAccountRepository customers = mock(CustomerAccountRepository.class);
    private final CreditApplicationRepository applications = mock(CreditApplicationRepository.class);
    private final CreditAccountEventRepository events = mock(CreditAccountEventRepository.class);
    private final CreditApplicationService service = new CreditApplicationService(customers, applications, events);
    private final CustomerAccount customer = new CustomerAccount(EMAIL, "Client", "hash");
    private final CreditApplicationService.Details details = new CreditApplicationService.Details(
            " Business ", " Contact ", "contact@example.com", " 123 ", new BigDecimal("1000.00"));

    @Test
    void invitationIsBoundToCustomerHashedAndReplacesPreviousLink() throws Exception {
        when(customers.lockByEmail(EMAIL)).thenReturn(Optional.of(customer));
        CreditApplication previous = application(Instant.now().plusSeconds(100));
        when(applications.findByCustomerEmailAndStatus(EMAIL, "INVITED")).thenReturn(List.of(previous));
        CreditApplicationService.Invitation invitation = service.invite(" CLIENT@EXAMPLE.COM ");
        ArgumentCaptor<CreditApplication> captor = ArgumentCaptor.forClass(CreditApplication.class);
        verify(applications).save(captor.capture());
        CreditApplication saved = captor.getValue();
        assertEquals(hash(invitation.token()), ReflectionTestUtils.getField(saved, "tokenHash"));
        assertEquals(Duration.ofDays(7), Duration.between(saved.getInvitedAt(), saved.getExpiresAt()));
        assertEquals("REPLACED", previous.getStatus());
        assertNull(ReflectionTestUtils.getField(previous, "tokenHash"));
    }

    @Test
    void submissionRequiresCorrectCustomerAndValidUnusedInvitation() throws Exception {
        when(customers.lockByEmail(EMAIL)).thenReturn(Optional.of(customer));
        CreditApplication application = application(Instant.now().plusSeconds(100));
        when(applications.findByTokenHashAndCustomerEmail(hash(TOKEN), EMAIL)).thenReturn(Optional.of(application));
        when(applications.lockById(application.getId())).thenReturn(Optional.of(application));
        service.submit(EMAIL, TOKEN, details);
        assertEquals("SUBMITTED", application.getStatus());
        assertEquals("Business", application.getBusinessName());
        assertNull(ReflectionTestUtils.getField(application, "tokenHash"));
        assertFalse(customer.isCreditApproved());
        assertThrows(ResponseStatusException.class, () -> service.submit(EMAIL, TOKEN, details));

        CustomerAccount other = new CustomerAccount("other@example.com", "Other", "hash");
        when(customers.lockByEmail(other.getEmail())).thenReturn(Optional.of(other));
        assertThrows(ResponseStatusException.class, () -> service.submit(other.getEmail(), TOKEN, details));
        assertThrows(ResponseStatusException.class, () -> service.submit(EMAIL, "invalid", details));
    }

    @Test
    void expiredInvitationCannotBeSubmitted() throws Exception {
        when(customers.lockByEmail(EMAIL)).thenReturn(Optional.of(customer));
        CreditApplication application = application(Instant.now().minusSeconds(1));
        when(applications.findByTokenHashAndCustomerEmail(hash(TOKEN), EMAIL)).thenReturn(Optional.of(application));
        when(applications.lockById(application.getId())).thenReturn(Optional.of(application));
        assertThrows(ResponseStatusException.class, () -> service.submit(EMAIL, TOKEN, details));
        assertEquals("INVITED", application.getStatus());
    }

    @Test
    void reviewAndRevocationRequireValidStateAndRecordEvents() {
        when(customers.lockByEmail(EMAIL)).thenReturn(Optional.of(customer));
        CreditApplication application = application(Instant.now().plusSeconds(100));
        application.submit("Business", "Contact", "contact@example.com", "123", BigDecimal.ONE, Instant.now());
        when(applications.findCustomerEmailById(application.getId())).thenReturn(Optional.of(EMAIL));
        when(applications.lockById(application.getId())).thenReturn(Optional.of(application));
        service.review(application.getId(), true, "admin");
        assertTrue(customer.isCreditApproved());
        assertEquals("APPROVED", application.getStatus());
        assertThrows(ResponseStatusException.class, () -> service.review(application.getId(), true, "admin"));
        service.revoke(EMAIL, "admin");
        assertFalse(customer.isCreditApproved());
        assertThrows(ResponseStatusException.class, () -> service.revoke(EMAIL, "admin"));
        ArgumentCaptor<CreditAccountEvent> captor = ArgumentCaptor.forClass(CreditAccountEvent.class);
        verify(events, times(2)).save(captor.capture());
        assertEquals(List.of("APPROVED", "REVOKED"), captor.getAllValues().stream()
                .map(event -> ReflectionTestUtils.getField(event, "action")).toList());
        assertEquals("admin", ReflectionTestUtils.getField(captor.getAllValues().get(1), "actor"));
    }

    @Test
    void pendingAndApprovedCustomersCannotBeInvited() {
        when(customers.lockByEmail(EMAIL)).thenReturn(Optional.of(customer));
        when(applications.findByCustomerEmailAndStatus(EMAIL, "SUBMITTED"))
                .thenReturn(List.of(application(Instant.now().plusSeconds(10))));
        assertThrows(ResponseStatusException.class, () -> service.invite(EMAIL));
        customer.setCreditApproved(true);
        assertThrows(ResponseStatusException.class, () -> service.invite(EMAIL));
        verify(applications, never()).save(any());
    }

    @Test
    void rejectionDoesNotGrantCreditAndAllowsAnotherInvitation() {
        when(customers.lockByEmail(EMAIL)).thenReturn(Optional.of(customer));
        CreditApplication application = application(Instant.now().plusSeconds(100));
        application.submit("Business", "Contact", "contact@example.com", "123", BigDecimal.ONE, Instant.now());
        when(applications.findCustomerEmailById(application.getId())).thenReturn(Optional.of(EMAIL));
        when(applications.lockById(application.getId())).thenReturn(Optional.of(application));
        service.review(application.getId(), false, "admin");
        assertEquals("REJECTED", application.getStatus());
        assertFalse(customer.isCreditApproved());
        verify(events, never()).save(any());
        service.invite(EMAIL);
        verify(applications).save(any(CreditApplication.class));
    }

    private CreditApplication application(Instant expiry) {
        return new CreditApplication(EMAIL, "old-hash", Instant.now().minusSeconds(10), expiry);
    }

    private static String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
