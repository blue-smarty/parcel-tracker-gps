package com.parceltracker.controller;

import com.parceltracker.dto.CourierStatusUpdate;
import com.parceltracker.dto.CourierDecisionRequest;
import com.parceltracker.dto.CourierEarnings;
import com.parceltracker.dto.CourierIdentity;
import com.parceltracker.model.DeliveryProof;
import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.CourierService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/couriers/{courierId}")
@CrossOrigin
public class CourierController {
    private final CourierService service;

    public CourierController(CourierService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @PreAuthorize("#courierId == authentication.name")
    public CourierIdentity identity(Authentication authentication) {
        return new CourierIdentity(authentication.getName());
    }

    @GetMapping("/deliveries")
    @PreAuthorize("#courierId == authentication.name")
    public List<Parcel> activeDeliveries(@PathVariable String courierId) {
        return service.getActiveDeliveries(courierId);
    }

    @GetMapping("/earnings")
    @PreAuthorize("#courierId == authentication.name")
    public CourierEarnings earnings(@PathVariable String courierId) {
        return service.getEarnings(courierId);
    }

    @PutMapping("/deliveries/{parcelId}/decision")
    @PreAuthorize("#courierId == authentication.name")
    public Parcel decide(@PathVariable String courierId, @PathVariable String parcelId,
                         @Valid @RequestBody CourierDecisionRequest decision) {
        return service.decideRequest(courierId, parcelId, decision.getAccepted());
    }

    @PostMapping("/deliveries/{parcelId}/proof")
    @PreAuthorize("#courierId == authentication.name")
    public Parcel completeDelivery(@PathVariable String courierId, @PathVariable String parcelId,
                                   @Valid @RequestBody DeliveryProof proof) {
        return service.completeDelivery(courierId, parcelId, proof);
    }

    @PutMapping("/deliveries/{parcelId}/status")
    @PreAuthorize("#courierId == authentication.name")
    public Parcel updateStatus(@PathVariable String courierId, @PathVariable String parcelId,
                               @Valid @RequestBody CourierStatusUpdate update) {
        return service.updateStatus(courierId, parcelId, update.getStatus());
    }

    @PostMapping("/deliveries/{parcelId}/location")
    @PreAuthorize("#courierId == authentication.name")
    public Parcel updateLocation(@PathVariable String courierId, @PathVariable String parcelId,
                                 @Valid @RequestBody Location location) {
        return service.updateLocation(courierId, parcelId, location);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
