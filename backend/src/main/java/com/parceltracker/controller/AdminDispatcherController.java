package com.parceltracker.controller;

import com.parceltracker.model.DispatcherAccount;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.DispatcherAccountRepository;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin/dispatchers")
public class AdminDispatcherController {
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,40}$");
    private final DispatcherAccountRepository dispatchers;
    private final CourierAccountRepository couriers;
    private final PasswordEncoder encoder;
    private final String adminUsername;

    public record CreateRequest(String username, String password) {}
    public record PasswordRequest(String password) {}
    public record Summary(String username) {}

    public AdminDispatcherController(DispatcherAccountRepository dispatchers, CourierAccountRepository couriers,
            PasswordEncoder encoder, @Value("${parcel.auth.admin-username:}") String adminUsername) {
        this.dispatchers = dispatchers;
        this.couriers = couriers;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
    }

    @GetMapping
    public List<Summary> list() {
        return dispatchers.findAll().stream().map(account -> new Summary(account.getUsername())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Summary create(@RequestBody CreateRequest request) {
        String username = request.username() == null ? "" : request.username().trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must be 3-40 letters, digits, dots, dashes or underscores.");
        }
        validatePassword(request.password());
        if (username.equalsIgnoreCase(adminUsername) || dispatchers.existsById(username)
                || couriers.existsByCourierIdIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use.");
        }
        dispatchers.save(new DispatcherAccount(username, encoder.encode(request.password())));
        return new Summary(username);
    }

    @PutMapping("/{username}/password")
    public Summary resetPassword(@PathVariable String username, @RequestBody PasswordRequest request) {
        validatePassword(request.password());
        if (!dispatchers.existsById(username)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatcher not found.");
        dispatchers.save(new DispatcherAccount(username, encoder.encode(request.password())));
        return new Summary(username);
    }

    @DeleteMapping("/{username}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String username) {
        if (!dispatchers.existsById(username)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispatcher not found.");
        dispatchers.deleteById(username);
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 12 characters and at most 72 UTF-8 bytes.");
        }
    }
}
