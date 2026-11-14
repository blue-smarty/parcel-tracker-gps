package com.parceltracker.controller;

import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.CourierPushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class CourierPushController {
    public record Keys(@NotBlank @Size(max = 256) String p256dh, @NotBlank @Size(max = 256) String auth) {}
    public record Subscription(@NotBlank @Size(max = 2048) String endpoint, @Valid @jakarta.validation.constraints.NotNull Keys keys) {}
    public record Endpoint(@NotBlank @Size(max = 2048) String endpoint) {}
    public record Message(@NotBlank @Size(max = 240) String message) {}

    private final CourierPushService push;
    private final CourierAccountRepository couriers;

    public CourierPushController(CourierPushService push, CourierAccountRepository couriers) {
        this.push = push;
        this.couriers = couriers;
    }

    @GetMapping("/api/couriers/{courierId}/push-key")
    @PreAuthorize("#courierId == authentication.name")
    public Map<String, String> key(@PathVariable String courierId) {
        return Map.of("publicKey", push.publicKey());
    }

    @PostMapping("/api/couriers/{courierId}/push-subscription")
    @PreAuthorize("#courierId == authentication.name")
    public ResponseEntity<Void> subscribe(@PathVariable String courierId, @Valid @RequestBody Subscription request) {
        push.subscribe(courierId, request.endpoint(), request.keys().p256dh(), request.keys().auth());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/couriers/{courierId}/push-unsubscribe")
    @PreAuthorize("#courierId == authentication.name")
    public ResponseEntity<Void> unsubscribe(@PathVariable String courierId, @Valid @RequestBody Endpoint request) {
        push.unsubscribe(courierId, request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/couriers/{courierId}/push-message")
    public Map<String, Integer> message(@PathVariable String courierId, @Valid @RequestBody Message request) {
        if (!couriers.existsById(courierId)) throw new IllegalArgumentException("Courier not found");
        return Map.of("accepted", push.send(courierId, request.message().trim()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> unavailable(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }
}
