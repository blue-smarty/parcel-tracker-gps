package com.parceltracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerAddressRequest(
        @NotBlank @Size(max = 80) String label,
        @NotBlank @Size(max = 255) String contactName,
        @NotBlank @Size(max = 255) String contactPhone,
        @NotBlank @Size(max = 255) String address) {
}
