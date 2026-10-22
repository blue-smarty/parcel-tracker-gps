package com.parceltracker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class PickupRequest {
    @NotBlank(message = "Contact name is required")
    private String contactName;

    @NotBlank(message = "Contact phone is required")
    @Pattern(regexp = "^\\+?[0-9][0-9 ()\\-]{6,19}$", message = "Contact phone is not a valid phone number")
    private String contactPhone;

    @NotBlank(message = "Street is required")
    private String street;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Postal code is required")
    private String postalCode;

    @NotBlank(message = "Parcel description is required")
    private String parcelDescription;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Weight must be greater than zero")
    private Double weightKg;

    private String dimensions;

    @NotNull(message = "Pickup date is required")
    @FutureOrPresent(message = "Pickup date must not be in the past")
    private LocalDate pickupDate;

    @NotBlank(message = "Time window is required")
    private String timeWindow;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    private String notes;

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getParcelDescription() { return parcelDescription; }
    public void setParcelDescription(String parcelDescription) { this.parcelDescription = parcelDescription; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate pickupDate) { this.pickupDate = pickupDate; }
    public String getTimeWindow() { return timeWindow; }
    public void setTimeWindow(String timeWindow) { this.timeWindow = timeWindow; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
