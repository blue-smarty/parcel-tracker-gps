package com.parceltracker.config;

import com.parceltracker.model.CourierAccount;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.DispatcherAccountRepository;
import java.util.Locale;
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
    private final DispatcherAccountRepository dispatchers;

    public CourierAccountBootstrap(
            CourierAccountRepository accounts,
            PasswordEncoder passwordEncoder,
            DispatcherAccountRepository dispatchers,
            @Value("${parcel.auth.bootstrap-courier-id:}") String courierId,
            @Value("${parcel.auth.bootstrap-password:}") String password) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.courierId = courierId;
        this.password = password;
        this.dispatchers = dispatchers;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (courierId.isBlank() && password.isBlank()) return;
        if (courierId.isBlank() || password.length() < 12) {
            throw new IllegalStateException("Configure both bootstrap courier ID and a password of at least 12 characters");
        }
        if (dispatchers.existsById(courierId.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Bootstrap courier ID conflicts with a dispatcher account");
        }
        var existing = accounts.findById(courierId);
        if (existing.isPresent() && passwordEncoder.matches(password, existing.get().getPasswordHash())) return;
        accounts.save(new CourierAccount(courierId, passwordEncoder.encode(password)));
    }
}
