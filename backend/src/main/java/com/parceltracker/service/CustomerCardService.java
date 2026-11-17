package com.parceltracker.service;

import com.parceltracker.model.CustomerAccount;
import com.parceltracker.model.CustomerCard;
import com.parceltracker.repository.CustomerCardRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Creates AES-256-GCM encrypted customer cards; the full card is decrypted only for admins. */
@Service
public class CustomerCardService {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    public record Card(String name, String email, String phone, Instant createdAt) {}
    public record DispatcherView(String name, String phone) {}

    private final CustomerCardRepository cards;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public CustomerCardService(CustomerCardRepository cards,
            @Value("${parcel.cards.encryption-key:}") String base64Key) {
        this.cards = cards;
        if (base64Key.isBlank()) {
            this.key = null;
        } else {
            byte[] raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("Customer card encryption key must be 32 bytes, base64 encoded");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    public boolean isEnabled() {
        return key != null;
    }

    public void createCard(CustomerAccount account, String phone) {
        if (!isEnabled()) {
            return;
        }
        Card card = new Card(account.getDisplayName(), account.getEmail(),
                phone == null || phone.isBlank() ? null : phone.trim(), account.getCreatedAt());
        cards.save(new CustomerCard(account.getEmail(), encrypt(card)));
    }

    public Optional<Card> find(String email) {
        return cards.findById(email).map(c -> decrypt(c.getEncryptedCard()));
    }

    public Optional<DispatcherView> findForDispatcher(String email) {
        return find(email).map(c -> new DispatcherView(c.name(), c.phone()));
    }

    private String encrypt(Card card) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(mapper.writeValueAsBytes(card));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException | JacksonException e) {
            throw new IllegalStateException("Could not encrypt customer card", e);
        }
    }

    private Card decrypt(String encoded) {
        if (!isEnabled()) {
            throw new IllegalStateException("Customer card encryption is not configured");
        }
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return mapper.readValue(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), Card.class);
        } catch (GeneralSecurityException | JacksonException e) {
            throw new IllegalStateException("Could not decrypt customer card", e);
        }
    }
}
