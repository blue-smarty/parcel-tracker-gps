package com.parceltracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import com.parceltracker.model.ParcelItem;

public record AdhocParcelRequest(
        @NotBlank @Size(max = 255) String sender,
        @NotBlank @Size(max = 255) String customerContact,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String recipient,
        @NotBlank @Size(max = 255) String destination,
        @NotBlank @Size(max = 255) String courierId,
        @Pattern(regexp = "SMALL|MEDIUM|LARGE") String packageSize,
        @Positive Double widthCm,
        @Positive Double lengthCm,
        @Positive Double heightCm,
        @Positive Double weightKg,
        @Valid @Size(max = 100) List<@NotNull ParcelItem> items,
        @Positive Double distanceKm,
        @DecimalMin("0.0") Double straightLineDistanceKm,
        @Pattern(regexp = "SAME_DAY|SCHEDULED|GENERAL|EXPRESS|DIRECT") String deliverySpeed,
        LocalDate scheduledDate,
        @Size(max = 32) String scheduledWindow,
        @Pattern(regexp = "DEMO_CARD|DEMO_MOBILE_WALLET|CASH_ON_DELIVERY") String paymentMethod) {
}
