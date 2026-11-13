package com.parceltracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

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
        @Positive Double distanceKm,
        @DecimalMin("0.0") Double straightLineDistanceKm,
        @Pattern(regexp = "SAME_DAY|SCHEDULED") String deliverySpeed,
        LocalDate scheduledDate,
        @Pattern(regexp = "08:00-10:00|10:00-12:00|12:00-14:00|14:00-16:00|16:00-18:00") String scheduledWindow,
        @Pattern(regexp = "DEMO_CARD|DEMO_MOBILE_WALLET|CASH_ON_DELIVERY") String paymentMethod) {
}
