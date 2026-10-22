package com.parceltracker.model;

import java.time.LocalDate;

public class PickupRequest {
    private String contactName;
    private String contactPhone;
    private String street;
    private String city;
    private String postalCode;
    private String parcelDescription;
    private double weightKg;
    private String dimensions;
    private LocalDate pickupDate;
    private String timeWindow;
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
    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
    public LocalDate getPickupDate() { return pickupDate; }
    public void setPickupDate(LocalDate pickupDate) { this.pickupDate = pickupDate; }
    public String getTimeWindow() { return timeWindow; }
    public void setTimeWindow(String timeWindow) { this.timeWindow = timeWindow; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
