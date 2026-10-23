package com.parceltracker.dto;

import jakarta.validation.constraints.NotNull;

public class CourierDecisionRequest {
    @NotNull
    private Boolean accepted;

    public Boolean getAccepted() { return accepted; }
    public void setAccepted(Boolean accepted) { this.accepted = accepted; }
}
