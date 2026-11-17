package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_cards")
public class CustomerCard {
    @Id
    @Column(name = "email")
    private String email;

    @Column(name = "encrypted_card", nullable = false)
    private String encryptedCard;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public CustomerCard() {
    }

    public CustomerCard(String email, String encryptedCard) {
        this.email = email;
        this.encryptedCard = encryptedCard;
    }

    public String getEmail() {
        return email;
    }

    public String getEncryptedCard() {
        return encryptedCard;
    }
}
