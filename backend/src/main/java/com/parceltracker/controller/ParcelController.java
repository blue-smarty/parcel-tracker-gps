package com.parceltracker.controller;

import com.parceltracker.model.Parcel;
import com.parceltracker.dto.CourierAssignmentRequest;
import com.parceltracker.dto.AdhocParcelRequest;
import com.parceltracker.repository.CourierAccountRepository;
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
    private final CourierAccountRepository couriers;

    public ParcelController(ParcelService service, CourierAccountRepository couriers) {
        this.service = service;
        this.couriers = couriers;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Parcel create(@Valid @RequestBody Parcel parcel, Authentication authentication) {
        String customerEmail = isCustomer(authentication) ? authentication.getName() : null;
        return service.create(parcel, customerEmail);
    }

    @PostMapping("/adhoc")
    @ResponseStatus(HttpStatus.CREATED)
    public Parcel createAdhoc(@Valid @RequestBody AdhocParcelRequest request) {
        String courierId = request.courierId().trim();
        if (!couriers.existsById(courierId)) {
            throw new IllegalArgumentException("Courier not found");
        }
        Parcel parcel = new Parcel();
        parcel.setSender(request.sender().trim());
        parcel.setCustomerContact(request.customerContact().trim());
        parcel.setPickupAddress(request.pickupAddress().trim());
        parcel.setRecipient(request.recipient().trim());
        parcel.setDestination(request.destination().trim());
        parcel.setPackageSize(request.packageSize());
        parcel.setWidthCm(request.widthCm());
        parcel.setLengthCm(request.lengthCm());
        parcel.setHeightCm(request.heightCm());
        parcel.setWeightKg(request.weightKg());
        parcel.setDistanceKm(request.distanceKm());
        parcel.setStraightLineDistanceKm(request.straightLineDistanceKm());
        parcel.setDeliverySpeed(request.deliverySpeed());
        parcel.setScheduledDate(request.scheduledDate());
        parcel.setScheduledWindow(request.scheduledWindow());
        parcel.setPaymentMethod(request.paymentMethod());
        return service.createAssigned(parcel, courierId);
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
        String courierId = request.getCourierId().trim();
        if (!couriers.existsById(courierId)) throw new IllegalArgumentException("Courier not found");
        return service.assignCourier(id, courierId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    static void requireOwnerOrAdmin(Parcel parcel, Authentication authentication) {
        if (!isStaff(authentication) && (parcel.getCustomerEmail() == null
                || !parcel.getCustomerEmail().equalsIgnoreCase(authentication.getName()))) {
            throw new NoSuchElementException("Parcel not found");
        }
    }

    private static boolean isCustomer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
    }

    private static boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_DISPATCHER".equals(authority.getAuthority()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
