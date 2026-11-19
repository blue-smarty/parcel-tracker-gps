package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_applications")
public class CreditApplication {
    @Id
    private UUID id;
    @Column(name = "customer_email", nullable = false)
    private String customerEmail;
    @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "business_name")
    private String businessName;
    @Column(name = "contact_name")
    private String contactName;
    @Column(name = "contact_email")
    private String contactEmail;
    @Column(name = "contact_phone", length = 40)
    private String contactPhone;
    @Column(name = "requested_limit", precision = 12, scale = 2)
    private BigDecimal requestedLimit;
    @Column(name = "submitted_at")
    private Instant submittedAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "reviewed_by")
    private String reviewedBy;

    protected CreditApplication() {}

    public CreditApplication(String email, String tokenHash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.customerEmail = email;
        this.tokenHash = tokenHash;
        this.status = "INVITED";
        this.invitedAt = now;
        this.expiresAt = expiresAt;
    }

    public void replaceInvitation() {
        status = "REPLACED";
        tokenHash = null;
    }

    public void submit(String business, String name, String email, String phone, BigDecimal limit, Instant now) {
        businessName = business;
        contactName = name;
        contactEmail = email;
        contactPhone = phone;
        requestedLimit = limit;
        submittedAt = now;
        status = "SUBMITTED";
        tokenHash = null;
    }

    public void review(boolean approve, String reviewer, Instant now) {
        status = approve ? "APPROVED" : "REJECTED";
        reviewedBy = reviewer;
        reviewedAt = now;
    }

    public UUID getId() { return id; }
    public String getCustomerEmail() { return customerEmail; }
    public String getStatus() { return status; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getBusinessName() { return businessName; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public BigDecimal getRequestedLimit() { return requestedLimit; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
}
