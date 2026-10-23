package com.parceltracker.config;

import com.parceltracker.model.CourierAccount;
import com.parceltracker.repository.CourierAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class CourierAccountBootstrap implements ApplicationRunner {
    private final CourierAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final String courierId;
    private final String password;

    public CourierAccountBootstrap(
            CourierAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            @Value("${parcel.auth.bootstrap-courier-id:}") String courierId,
            @Value("${parcel.auth.bootstrap-password:}") String password) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.courierId = courierId;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (courierId.isBlank() && password.isBlank()) return;
        if (courierId.isBlank() || password.length() < 12) {
            throw new IllegalStateException("Configure both bootstrap courier ID and a password of at least 12 characters");
        }
        if (accounts.existsById(courierId)) {
            throw new IllegalStateException("Bootstrap courier ID already exists; provision accounts without overwriting them");
        }
        accounts.save(new CourierAccount(courierId, passwordEncoder.encode(password)));
    }
}
