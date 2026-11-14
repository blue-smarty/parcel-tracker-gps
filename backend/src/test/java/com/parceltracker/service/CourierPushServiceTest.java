package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.CourierPushSubscription;
import com.parceltracker.repository.CourierPushSubscriptionRepository;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

class CourierPushServiceTest {
    @Test
    void validatesEndpointsAndKeepsSubscriptionsOwnedByCourier() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        var generator = KeyPairGenerator.getInstance("ECDH", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPublicKey) pair.getPublic()));
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()));
        String auth = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]);
        var repo = mock(CourierPushSubscriptionRepository.class);
        var service = new CourierPushService(repo, key, secret, "mailto:push@example.com");
        String endpoint = "https://fcm.googleapis.com/fcm/send/test";

        assertThrows(IllegalArgumentException.class, () -> service.subscribe("one", "https://127.0.0.1/x", key, auth));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe("one", "https://fcm.googleapis.com.evil/x", key, auth));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe("one", endpoint, key, "bad"));
        service.subscribe("one", endpoint, key, auth);
        verify(repo).save(any(CourierPushSubscription.class));

        var request = new PushService(key, secret, "mailto:push@example.com").preparePost(
                new Notification(endpoint, key, auth, "New delivery"), Encoding.AES128GCM);
        assertEquals("aes128gcm", request.getFirstHeader("Content-Encoding").getValue());
        assertTrue(request.getFirstHeader("Authorization").getValue().startsWith("vapid "));

        var existing = new CourierPushSubscription("id", "one", endpoint, key, auth);
        when(repo.findById(anyString())).thenReturn(Optional.of(existing));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe("two", endpoint, key, auth));
        service.unsubscribe("two", endpoint);
        verify(repo, never()).delete(any());
        service.unsubscribe("one", endpoint);
        verify(repo).delete(existing);
    }

    @Test
    void reportsWhenPushIsNotConfigured() throws Exception {
        var service = new CourierPushService(mock(CourierPushSubscriptionRepository.class), "", "", "");
        assertThrows(IllegalStateException.class, service::publicKey);
        assertThrows(IllegalStateException.class, () -> service.send("one", "message"));
    }
}
