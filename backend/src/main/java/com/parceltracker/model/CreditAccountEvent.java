package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_account_events")
public class CreditAccountEvent {
    @Id
    private UUID id;
    @Column(name = "customer_email", nullable = false)
    private String customerEmail;
    @Column(nullable = false, length = 16)
    private String action;
    @Column(nullable = false)
    private String actor;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected CreditAccountEvent() {}

    public CreditAccountEvent(String email, String action, String actor, Instant now) {
        this.id = UUID.randomUUID();
        this.customerEmail = email;
        this.action = action;
        this.actor = actor;
        this.occurredAt = now;
    }
}
