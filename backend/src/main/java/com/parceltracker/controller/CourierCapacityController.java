package com.parceltracker.controller;

import com.parceltracker.model.CourierAccount;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.service.ConfigurationHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RestController
public class CourierCapacityController {
    public record Request(
            @NotBlank @Pattern(regexp = "MOTORCYCLE|CAR|VAN|TRUCK") String vehicleType,
            @NotNull @Min(1) @Max(100) Integer maxActiveParcels,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") Double maxPayloadWeightKg) { }
    public record Profile(String courierId, String vehicleType, Integer maxActiveParcels, Double maxPayloadWeightKg) { }

    private final CourierAccountRepository couriers;
    private final ConfigurationHistoryService history;

    public CourierCapacityController(CourierAccountRepository couriers, ConfigurationHistoryService history) {
        this.couriers = couriers;
        this.history = history;
    }

    @GetMapping("/api/admin/courier-capacity")
    public List<Profile> list() {
        return couriers.findAll().stream().map(CourierCapacityController::profile).toList();
    }

    @PutMapping("/api/admin/courier-capacity/{courierId}")
    @Transactional
    public Profile update(@PathVariable String courierId, @Valid @RequestBody Request request,
            Authentication authentication) {
        CourierAccount courier = couriers.findById(courierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Courier not found."));
        Profile before = profile(courier);
        courier.setVehicleType(request.vehicleType());
        courier.setMaxActiveParcels(request.maxActiveParcels());
        courier.setMaxPayloadWeightKg(request.maxPayloadWeightKg());
        Profile after = profile(couriers.save(courier));
        history.record("COURIER_CAPACITY", before, after, authentication.getName());
        return after;
    }

    private static Profile profile(CourierAccount courier) {
        return new Profile(courier.getCourierId(), courier.getVehicleType(),
                courier.getMaxActiveParcels(), courier.getMaxPayloadWeightKg());
    }
}
