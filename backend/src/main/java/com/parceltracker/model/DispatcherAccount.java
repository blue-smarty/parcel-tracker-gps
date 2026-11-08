package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dispatcher_accounts")
public class DispatcherAccount {
    @Id
    @Column(length = 40)
    private String username;
    @Column(nullable = false)
    private String passwordHash;

    protected DispatcherAccount() {}

    public DispatcherAccount(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
}
