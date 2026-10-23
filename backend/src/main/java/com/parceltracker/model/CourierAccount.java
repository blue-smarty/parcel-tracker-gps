package com.parceltracker.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "courier_accounts")
public class CourierAccount {
    @Id
    private String courierId;

    @Field("password_hash")
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
