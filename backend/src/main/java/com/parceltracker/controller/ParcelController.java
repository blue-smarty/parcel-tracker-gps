package com.parceltracker.controller;

import com.parceltracker.model.Parcel;
import com.parceltracker.dto.CourierAssignmentRequest;
import com.parceltracker.service.ParcelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parcels")
@CrossOrigin
public class ParcelController {
    private final ParcelService service;

    public ParcelController(ParcelService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Parcel create(@Valid @RequestBody Parcel parcel) {
        return service.create(parcel);
    }

    @GetMapping
    public List<Parcel> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Parcel get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public Parcel update(@PathVariable String id, @Valid @RequestBody Parcel parcel) {
        return service.update(id, parcel);
    }

    @PutMapping("/{id}/courier")
    public Parcel assignCourier(@PathVariable String id, @Valid @RequestBody CourierAssignmentRequest request) {
        return service.assignCourier(id, request.getCourierId().trim());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
