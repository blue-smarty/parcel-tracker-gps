package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courier_push_subscriptions")
public class CourierPushSubscription {
    @Id
    @Column(name = "endpoint_hash", length = 64)
    private String endpointHash;
    @Column(name = "courier_id", nullable = false)
    private String courierId;
    @Column(nullable = false, length = 2048)
    private String endpoint;
    @Column(name = "public_key", nullable = false, length = 256)
    private String publicKey;
    @Column(name = "auth_secret", nullable = false, length = 256)
    private String authSecret;

    protected CourierPushSubscription() {}

    public CourierPushSubscription(String endpointHash, String courierId, String endpoint,
                                   String publicKey, String authSecret) {
        this.endpointHash = endpointHash;
        this.courierId = courierId;
        this.endpoint = endpoint;
        this.publicKey = publicKey;
        this.authSecret = authSecret;
    }

    public String getCourierId() { return courierId; }
    public String getEndpoint() { return endpoint; }
    public String getPublicKey() { return publicKey; }
    public String getAuthSecret() { return authSecret; }
}
