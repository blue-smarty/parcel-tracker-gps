package com.parceltracker.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ParcelItem(
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") Double weight,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") Double length,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") Double width,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000") Double height,
        @NotNull @Min(1) @Max(1000) Integer quantity) {
}
