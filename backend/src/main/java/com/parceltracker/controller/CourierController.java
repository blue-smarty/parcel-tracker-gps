package com.parceltracker.controller;

import com.parceltracker.dto.CourierStatusUpdate;
import com.parceltracker.dto.CourierDecisionRequest;
import com.parceltracker.dto.CourierIdentity;
import com.parceltracker.model.DeliveryProof;
import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.dto.DriverActionResult;
import com.parceltracker.dto.GateCodeRequest;
import com.parceltracker.service.CourierService;
import com.parceltracker.service.DriverActionService;
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
public class CourierController {
    private final CourierService service;
    private final DriverActionService actions;

    public CourierController(CourierService service, DriverActionService actions) {
        this.service = service;
        this.actions = actions;
    }

    @GetMapping("/me")
    @PreAuthorize("#courierId == authentication.name")
    public CourierIdentity identity(@PathVariable String courierId, Authentication authentication) {
        return new CourierIdentity(authentication.getName());
    }

    @GetMapping("/deliveries")
    @PreAuthorize("#courierId == authentication.name")
    public List<Parcel> activeDeliveries(@PathVariable String courierId) {
        return service.getActiveDeliveries(courierId);
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

    @PostMapping("/deliveries/{parcelId}/actions/call")
    @PreAuthorize("#courierId == authentication.name")
    public DriverActionResult maskedCall(@PathVariable String courierId, @PathVariable String parcelId) {
        return actions.maskedCall(courierId, parcelId);
    }

    @PostMapping("/deliveries/{parcelId}/actions/proximity-text")
    @PreAuthorize("#courierId == authentication.name")
    public DriverActionResult proximityText(@PathVariable String courierId, @PathVariable String parcelId) {
        return actions.proximityText(courierId, parcelId);
    }

    @PostMapping("/deliveries/{parcelId}/actions/gate-code")
    @PreAuthorize("#courierId == authentication.name")
    public DriverActionResult gateCode(@PathVariable String courierId, @PathVariable String parcelId,
                                       @Valid @RequestBody(required = false) GateCodeRequest request) {
        return actions.logGateCode(courierId, parcelId, request == null ? null : request.getCode());
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
