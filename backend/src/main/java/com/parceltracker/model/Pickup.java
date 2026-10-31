package com.parceltracker.model;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pickups")
public class Pickup {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String street;
    private String city;
    @Column(name = "postal_code")
    private String postalCode;
    @Column(name = "contact_name")
    private String contactName;
    @Column(name = "contact_phone")
    private String contactPhone;
    @Column(name = "parcel_description", columnDefinition = "text")
    private String parcelDescription;
    @Column(name = "weight_kg")
    private double weightKg;
    private String dimensions;
    @Column(name = "pickup_date")
    private LocalDate pickupDate;
    @Column(name = "time_window")
    private String timeWindow;
    @Column(columnDefinition = "text")
    private String notes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PickupStatus status = PickupStatus.REQUESTED;
    @Column(name = "created_at")
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
