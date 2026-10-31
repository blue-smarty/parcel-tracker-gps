package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courier_accounts")
public class CourierAccount {
    @Id
    @Column(name = "courier_id")
    private String courierId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    public CourierAccount() {
    }

    public CourierAccount(String courierId, String passwordHash) {
        this.courierId = courierId;
        this.passwordHash = passwordHash;
    }

    public String getCourierId() {
        return courierId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
