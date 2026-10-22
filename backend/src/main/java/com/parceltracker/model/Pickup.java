package com.parceltracker.model;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "pickups")
public class Pickup {
    @Id
    private String id;
    private String street;
    private String city;
    @Field("postal_code")
    private String postalCode;
    @Field("contact_name")
    private String contactName;
    @Field("contact_phone")
    private String contactPhone;
    @Field("parcel_description")
    private String parcelDescription;
    @Field("weight_kg")
    private double weightKg;
    private String dimensions;
    @Field("pickup_date")
    private LocalDate pickupDate;
    @Field("time_window")
    private String timeWindow;
    private String notes;
    private PickupStatus status = PickupStatus.REQUESTED;
    @Field("created_at")
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
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
    public PickupStatus getStatus() { return status; }
    public void setStatus(PickupStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
