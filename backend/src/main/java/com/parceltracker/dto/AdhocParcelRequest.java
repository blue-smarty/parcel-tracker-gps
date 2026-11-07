package com.parceltracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdhocParcelRequest(
        @NotBlank @Size(max = 255) String sender,
        @NotBlank @Size(max = 255) String customerContact,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String recipient,
        @NotBlank @Size(max = 255) String destination,
        @NotBlank @Size(max = 255) String courierId) {
}
