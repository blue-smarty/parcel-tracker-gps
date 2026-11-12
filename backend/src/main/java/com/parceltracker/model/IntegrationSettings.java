package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "integration_settings")
public class IntegrationSettings {
    @Id
    private String id;

    @Column(name = "encrypted_key", nullable = false, columnDefinition = "text")
    private String encryptedKey;

    protected IntegrationSettings() {}

    public IntegrationSettings(String id, String encryptedKey) {
        this.id = id;
        this.encryptedKey = encryptedKey;
    }

    public String getEncryptedKey() { return encryptedKey; }
}
