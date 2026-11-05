package com.parceltracker.controller;

import com.parceltracker.dto.PickupRequest;
import com.parceltracker.model.Pickup;
import com.parceltracker.service.PickupService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pickups")
public class PickupController {
    private final PickupService service;

    public PickupController(PickupService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pickup create(@Valid @RequestBody PickupRequest request, Authentication authentication) {
        String customerEmail = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()))
                ? authentication.getName() : null;
        return service.create(request, customerEmail);
    }

    @GetMapping("/{id}")
    public Pickup get(@PathVariable String id, Authentication authentication) {
        Pickup pickup = service.get(id);
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin && (pickup.getCustomerEmail() == null
                || !pickup.getCustomerEmail().equalsIgnoreCase(authentication.getName()))) {
            throw new NoSuchElementException("Pickup not found");
        }
        return pickup;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
