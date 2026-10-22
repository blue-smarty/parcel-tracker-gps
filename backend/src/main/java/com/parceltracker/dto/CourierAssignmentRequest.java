package com.parceltracker.dto;

import jakarta.validation.constraints.NotBlank;

public class CourierAssignmentRequest {
    @NotBlank
    private String courierId;

    public String getCourierId() { return courierId; }
    public void setCourierId(String courierId) { this.courierId = courierId; }
}
