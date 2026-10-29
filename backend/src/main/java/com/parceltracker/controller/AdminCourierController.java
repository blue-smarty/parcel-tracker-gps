package com.parceltracker.controller;

import com.parceltracker.model.CourierAccount;
import com.parceltracker.repository.CourierAccountRepository;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/couriers")
public class AdminCourierController {
    private static final Pattern COURIER_ID = Pattern.compile("^[A-Za-z0-9._-]{3,40}$");
    private static final int MIN_PASSWORD_LENGTH = 12;

    public record CreateRequest(String courierId, String password) { }
    public record PasswordRequest(String password) { }
    public record CourierSummary(String courierId) { }

    private final CourierAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AdminCourierController(CourierAccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<CourierSummary> list() {
        return accounts.findAll().stream().map(a -> new CourierSummary(a.getCourierId())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourierSummary create(@RequestBody CreateRequest request) {
        String id = request.courierId() == null ? "" : request.courierId().trim();
        if (!COURIER_ID.matcher(id).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Courier ID must be 3-40 letters, digits, dots, dashes or underscores");
        }
        checkPassword(request.password());
        if (accounts.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Courier already exists");
        }
        accounts.save(new CourierAccount(id, passwordEncoder.encode(request.password())));
        return new CourierSummary(id);
    }

    @PutMapping("/{courierId}/password")
    public CourierSummary resetPassword(@PathVariable String courierId, @RequestBody PasswordRequest request) {
        if (!accounts.existsById(courierId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Courier not found");
        }
        checkPassword(request.password());
        accounts.save(new CourierAccount(courierId, passwordEncoder.encode(request.password())));
        return new CourierSummary(courierId);
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must contain at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }
}
