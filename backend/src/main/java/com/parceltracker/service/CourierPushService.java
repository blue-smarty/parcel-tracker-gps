package com.parceltracker.service;

import com.parceltracker.model.CourierPushSubscription;
import com.parceltracker.repository.CourierPushSubscriptionRepository;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CourierPushService {
    private static final Logger log = LoggerFactory.getLogger(CourierPushService.class);
    private static final Set<String> PUSH_HOSTS = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com",
            "push.services.mozilla.com", "web.push.apple.com");
    private final CourierPushSubscriptionRepository subscriptions;
    private final String publicKey;
    private final PushService push;

    public CourierPushService(CourierPushSubscriptionRepository subscriptions,
            @Value("${parcel.push.public-key:}") String publicKey,
            @Value("${parcel.push.private-key:}") String privateKey,
            @Value("${parcel.push.subject:}") String subject) throws GeneralSecurityException {
        this.subscriptions = subscriptions;
        this.publicKey = publicKey;
        if (publicKey.isBlank() && privateKey.isBlank() && subject.isBlank()) {
            push = null;
        } else {
            if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()
                    || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) {
                throw new IllegalStateException("Configure all VAPID settings and a mailto: or https: subject");
            }
            Security.addProvider(new BouncyCastleProvider());
            push = new PushService(publicKey, privateKey, subject);
            if (!Utils.verifyKeyPair(push.getPrivateKey(), push.getPublicKey())) {
                throw new IllegalStateException("VAPID public and private keys do not match");
            }
        }
    }

    public String publicKey() {
        requireEnabled();
        return publicKey;
    }

    public void subscribe(String courierId, String endpoint, String key, String auth) {
        requireEnabled();
        validateEndpoint(endpoint);
        try {
            byte[] publicBytes = Base64.getUrlDecoder().decode(key);
            byte[] authBytes = Base64.getUrlDecoder().decode(auth);
            if (publicBytes.length != 65 || authBytes.length != 16) throw new IllegalArgumentException("Invalid push keys");
            new Notification(endpoint, key, auth, "check");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid push subscription keys");
        }
        String id = hash(endpoint);
        subscriptions.findById(id).ifPresent(existing -> {
            if (!courierId.equals(existing.getCourierId())) {
                throw new IllegalArgumentException("This browser is already subscribed to another courier");
            }
        });
        subscriptions.save(new CourierPushSubscription(id, courierId, endpoint, key, auth));
    }

    public void unsubscribe(String courierId, String endpoint) {
        validateEndpoint(endpoint);
        String id = hash(endpoint);
        subscriptions.findById(id).filter(existing -> courierId.equals(existing.getCourierId()))
                .ifPresent(subscriptions::delete);
    }

    @Async
    public void deliveryChanged(String courierId) {
        if (push != null && courierId != null && !courierId.isBlank()) {
            send(courierId, "A delivery has been assigned or updated. Open ParcelPilot for details.");
        }
    }

    public int send(String courierId, String message) {
        requireEnabled();
        int accepted = 0;
        RequestConfig config = RequestConfig.custom().setConnectTimeout(5000).setSocketTimeout(5000)
                .setConnectionRequestTimeout(5000).setRedirectsEnabled(false).build();
        try (var client = HttpClients.custom().setDefaultRequestConfig(config).disableRedirectHandling().build()) {
            for (CourierPushSubscription subscription : subscriptions.findByCourierId(courierId)) {
                try {
                    validateEndpoint(subscription.getEndpoint());
                    var notification = new Notification(subscription.getEndpoint(), subscription.getPublicKey(),
                            subscription.getAuthSecret(), message.getBytes(StandardCharsets.UTF_8), 86400);
                    var request = push.preparePost(notification, Encoding.AES128GCM);
                    try (var response = client.execute(request)) {
                        int status = response.getStatusLine().getStatusCode();
                        if (status >= 200 && status < 300) accepted++;
                        else if (status == 404 || status == 410) subscriptions.delete(subscription);
                        else log.warn("Push provider rejected notification with HTTP {}", status);
                    }
                } catch (GeneralSecurityException | IOException | JoseException | IllegalArgumentException e) {
                    log.warn("Failed to send courier push notification: {}", e.getClass().getSimpleName());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to close push HTTP client: {}", e.getClass().getSimpleName());
        }
        return accepted;
    }

    private void requireEnabled() {
        if (push == null) throw new IllegalStateException("Push notifications are not configured on the server");
    }

    private static void validateEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (endpoint.length() > 2048 || !"https".equals(uri.getScheme()) || host == null
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null
                    || !(PUSH_HOSTS.contains(host) || host.endsWith(".push.apple.com"))) {
                throw new IllegalArgumentException("Unsupported push endpoint");
            }
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("Invalid push endpoint");
        }
    }

    private static String hash(String endpoint) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
