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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parcels")
public class ParcelController {
    private final ParcelService service;

    public ParcelController(ParcelService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Parcel create(@Valid @RequestBody Parcel parcel, Authentication authentication) {
        String customerEmail = isCustomer(authentication) ? authentication.getName() : null;
        return service.create(parcel, customerEmail);
    }

    @GetMapping
    public List<Parcel> list() {
        return service.findAll();
    }

    @GetMapping("/mine")
    public List<Parcel> mine(Authentication authentication) {
        return service.findByCustomerEmail(authentication.getName());
    }

    @GetMapping("/{id}")
    public Parcel get(@PathVariable String id, Authentication authentication) {
        Parcel parcel = service.get(id);
        requireOwnerOrAdmin(parcel, authentication);
        return parcel;
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

    static void requireOwnerOrAdmin(Parcel parcel, Authentication authentication) {
        if (!isAdmin(authentication) && (parcel.getCustomerEmail() == null
                || !parcel.getCustomerEmail().equalsIgnoreCase(authentication.getName()))) {
            throw new NoSuchElementException("Parcel not found");
        }
    }

    private static boolean isCustomer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }
}
