package com.parceltracker.dto;

import com.parceltracker.model.ParcelStatus;
import jakarta.validation.constraints.NotNull;

public class CourierStatusUpdate {
    @NotNull
    private ParcelStatus status;

    public ParcelStatus getStatus() { return status; }
    public void setStatus(ParcelStatus status) { this.status = status; }
}
