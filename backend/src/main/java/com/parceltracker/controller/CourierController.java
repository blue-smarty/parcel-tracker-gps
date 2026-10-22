package com.parceltracker.controller;

import com.parceltracker.dto.CourierStatusUpdate;
import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.CourierService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/couriers/{courierId}/deliveries")
@CrossOrigin
public class CourierController {
    private final CourierService service;

    public CourierController(CourierService service) {
        this.service = service;
    }

    @GetMapping
    public List<Parcel> activeDeliveries(@PathVariable String courierId) {
        return service.getActiveDeliveries(courierId);
    }

    @PutMapping("/{parcelId}/status")
    public Parcel updateStatus(@PathVariable String courierId, @PathVariable String parcelId,
                               @Valid @RequestBody CourierStatusUpdate update) {
        return service.updateStatus(courierId, parcelId, update.getStatus());
    }

    @PostMapping("/{parcelId}/location")
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
