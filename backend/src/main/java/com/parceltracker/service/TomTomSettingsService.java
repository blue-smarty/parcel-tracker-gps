package com.parceltracker.service;

import com.parceltracker.model.IntegrationSettings;
import com.parceltracker.repository.IntegrationSettingsRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TomTomSettingsService {
    private static final String ID = "tomtom";
    private static final int NONCE_LENGTH = 12;
    private final IntegrationSettingsRepository repository;
    private final String encryptionKey;
    private final String routeFallback;
    private final String searchFallback;
    private final SecureRandom random = new SecureRandom();

    public TomTomSettingsService(IntegrationSettingsRepository repository,
            @Value("${parcel.integrations.encryption-key:}") String encryptionKey,
            @Value("${parcel.tomtom.api-key:}") String routeFallback,
            @Value("${parcel.address-lookup.tomtom-api-key:}") String searchFallback) {
        this.repository = repository;
        this.encryptionKey = encryptionKey.trim();
        this.routeFallback = routeFallback.trim();
        this.searchFallback = searchFallback.trim();
    }

    public record Status(boolean saved, boolean savedUsable, boolean environmentConfigured, boolean encryptionConfigured) {}

    @Transactional(readOnly = true)
    public Status status() {
        var saved = repository.findById(ID);
        boolean usable = false;
        if (saved.isPresent()) {
            try {
                decrypt(saved.get().getEncryptedKey());
                usable = true;
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value() != 503) throw ex;
            }
        }
        return new Status(saved.isPresent(), usable, !routeFallback.isEmpty() || !searchFallback.isEmpty(),
                validEncryptionKey());
    }

    @Transactional(readOnly = true)
    public String routeKey() { return resolve(routeFallback); }

    @Transactional(readOnly = true)
    public String searchKey() { return resolve(searchFallback); }

    private String resolve(String fallback) {
        return repository.findById(ID).map(settings -> decrypt(settings.getEncryptedKey())).orElse(fallback);
    }

    @Transactional
    public void save(String value) {
        if (value == null || value.isBlank() || value.length() > 512
                || !value.equals(value.trim()) || value.chars().anyMatch(Character::isWhitespace)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid TomTom API key without whitespace.");
        }
        repository.save(new IntegrationSettings(ID, encrypt(value)));
    }

    @Transactional
    public void remove() { repository.deleteById(ID); }

    private boolean validEncryptionKey() {
        try {
            return Base64.getDecoder().decode(encryptionKey).length == 32;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private SecretKeySpec masterKey() {
        if (!validEncryptionKey()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Integration encryption key is not configured.");
        }
        return new SecretKeySpec(Base64.getDecoder().decode(encryptionKey), "AES");
    }

    private String encrypt(String value) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ID.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt integration settings.", ex);
        }
    }

    private String decrypt(String value) {
        try {
            String[] parts = value.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid ciphertext format.");
            byte[] nonce = Base64.getDecoder().decode(parts[0]);
            if (nonce.length != NONCE_LENGTH) throw new IllegalArgumentException("Invalid nonce.");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(ID.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Integration settings cannot be decrypted.");
        }
    }
}
