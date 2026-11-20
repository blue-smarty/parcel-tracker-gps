package com.parceltracker.controller;

import com.parceltracker.service.CourierRouteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/couriers/{courierId}/route")
public class CourierRouteController {
    private final CourierRouteService routes;

    public CourierRouteController(CourierRouteService routes) {
        this.routes = routes;
    }

    public record RouteRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}

    @PostMapping
    @PreAuthorize("#courierId == authentication.name")
    public CourierRouteService.RouteResult route(@PathVariable String courierId,
                                                  @Valid @RequestBody RouteRequest request) {
        return routes.calculate(courierId, request.latitude(), request.longitude());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason()));
    }
}
