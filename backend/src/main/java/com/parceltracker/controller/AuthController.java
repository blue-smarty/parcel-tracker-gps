package com.parceltracker.controller;

import com.parceltracker.dto.RegistrationRequest;
import com.parceltracker.model.CustomerAccount;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.CustomerAccountRepository;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CustomerAccountRepository customers;
    private final CourierAccountRepository couriers;
    private final PasswordEncoder encoder;
    private final String adminUsername;

    public AuthController(CustomerAccountRepository customers, CourierAccountRepository couriers,
                          PasswordEncoder encoder,
                          @Value("${parcel.auth.admin-username:}") String adminUsername) {
        this.customers = customers;
        this.couriers = couriers;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (email.equalsIgnoreCase(adminUsername) || couriers.existsById(email) || customers.existsById(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "An account with this email already exists."));
        }
        CustomerAccount account = customers.save(
                new CustomerAccount(email, request.name().trim(), encoder.encode(request.password())));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("username", account.getEmail(), "name", account.getDisplayName(), "role", "CUSTOMER"));
    }

    /** Verifies HTTP Basic credentials and reports the account role so the UI can route the user. */
    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .findFirst().orElse("");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", authentication.getName());
        body.put("role", role);
        body.put("name", "CUSTOMER".equals(role)
                ? customers.findById(authentication.getName()).map(CustomerAccount::getDisplayName)
                        .orElse(authentication.getName())
                : authentication.getName());
        return body;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).findFirst().orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
