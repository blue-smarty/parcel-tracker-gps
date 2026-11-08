package com.parceltracker.controller;

import com.parceltracker.repository.CourierAccountRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DispatchController {
    public record CourierSummary(String courierId) {}
    private final CourierAccountRepository couriers;

    public DispatchController(CourierAccountRepository couriers) {
        this.couriers = couriers;
    }

    @GetMapping("/api/dispatch/couriers")
    public List<CourierSummary> couriers() {
        return couriers.findAll().stream().map(account -> new CourierSummary(account.getCourierId())).toList();
    }
}
