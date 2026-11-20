package com.parceltracker.controller;

import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.model.CourierAccount;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DispatchController {
    public record CourierSummary(String courierId, String vehicleType, Integer maxActiveParcels,
            Double maxPayloadWeightKg) {}
    private final CourierAccountRepository couriers;

    public DispatchController(CourierAccountRepository couriers) {
        this.couriers = couriers;
    }

    @GetMapping("/api/dispatch/couriers")
    public List<CourierSummary> couriers() {
        return couriers.findAll().stream().map(DispatchController::summary).toList();
    }

    private static CourierSummary summary(CourierAccount account) {
        return new CourierSummary(account.getCourierId(), account.getVehicleType(),
                account.getMaxActiveParcels(), account.getMaxPayloadWeightKg());
    }
}
