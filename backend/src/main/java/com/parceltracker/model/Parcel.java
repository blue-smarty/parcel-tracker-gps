package com.parceltracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "parcels", indexes = {
        @Index(name = "status_updated_at", columnList = "status, updated_at"),
        @Index(name = "status_created_at", columnList = "status, created_at"),
        @Index(name = "courier_status", columnList = "courier_id, status"),
        @Index(name = "parcels_courier_id_idx", columnList = "courier_id")
})
public class Parcel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "tracking_number", nullable = false, unique = true)
    private String trackingNumber;

    @NotBlank
    private String sender;

    @NotBlank
    private String recipient;

    @Column(name = "courier_id")
    private String courierId;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Valid
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_location", columnDefinition = "jsonb")
    private Location currentLocation;

    private String destination;

    @Column(name = "package_size")
    @Pattern(regexp = "SMALL|MEDIUM|LARGE")
    private String packageSize;

    @Positive
    @Column(name = "width_cm")
    private Double widthCm;

    @Positive
    @Column(name = "length_cm")
    private Double lengthCm;

    @Positive
    @Column(name = "height_cm")
    private Double heightCm;

    @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "delivery_speed")
    @Pattern(regexp = "SAME_DAY|SCHEDULED")
    private String deliverySpeed;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "scheduled_window")
    private String scheduledWindow;

    @Positive
    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "destination_latitude")
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double destinationLatitude;

    @Column(name = "destination_longitude")
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double destinationLongitude;

    @Column(name = "estimated_price")
    private Double estimatedPrice;

    @Column(name = "payment_method")
    @Pattern(regexp = "DEMO_CARD|DEMO_MOBILE_WALLET|CASH_ON_DELIVERY")
    private String paymentMethod;

    @Column(name = "customer_contact")
    private String customerContact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParcelStatus status = ParcelStatus.CREATED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "location_history", nullable = false, columnDefinition = "jsonb")
    private List<Location> locationHistory = new ArrayList<>();

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "location_inactive", nullable = false)
    private boolean locationInactive;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Valid
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proof_of_delivery", columnDefinition = "jsonb")
    private DeliveryProof proofOfDelivery;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getCourierId() { return courierId; }
    public void setCourierId(String courierId) { this.courierId = courierId; }
    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }
    public Location getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Location currentLocation) { this.currentLocation = currentLocation; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public String getPackageSize() { return packageSize; }
    public void setPackageSize(String packageSize) { this.packageSize = packageSize; }
    public Double getWidthCm() { return widthCm; }
    public void setWidthCm(Double widthCm) { this.widthCm = widthCm; }
    public Double getLengthCm() { return lengthCm; }
    public void setLengthCm(Double lengthCm) { this.lengthCm = lengthCm; }
    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public String getDeliverySpeed() { return deliverySpeed; }
    public void setDeliverySpeed(String deliverySpeed) { this.deliverySpeed = deliverySpeed; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    public String getScheduledWindow() { return scheduledWindow; }
    public void setScheduledWindow(String scheduledWindow) { this.scheduledWindow = scheduledWindow; }
    public Double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
    public Double getDestinationLatitude() { return destinationLatitude; }
    public void setDestinationLatitude(Double destinationLatitude) { this.destinationLatitude = destinationLatitude; }
    public Double getDestinationLongitude() { return destinationLongitude; }
    public void setDestinationLongitude(Double destinationLongitude) { this.destinationLongitude = destinationLongitude; }
    public Double getEstimatedPrice() { return estimatedPrice; }
    public void setEstimatedPrice(Double estimatedPrice) { this.estimatedPrice = estimatedPrice; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getCustomerContact() { return customerContact; }
    public void setCustomerContact(String customerContact) { this.customerContact = customerContact; }
    public ParcelStatus getStatus() { return status; }
    public void setStatus(ParcelStatus status) { this.status = status; }
    public List<Location> getLocationHistory() { return locationHistory; }
    public void setLocationHistory(List<Location> locationHistory) { this.locationHistory = locationHistory; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(Instant escalatedAt) { this.escalatedAt = escalatedAt; }
    public boolean isLocationInactive() { return locationInactive; }
    public void setLocationInactive(boolean locationInactive) { this.locationInactive = locationInactive; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }
    @JsonIgnore
    public DeliveryProof getProofOfDelivery() { return proofOfDelivery; }
    public void setProofOfDelivery(DeliveryProof proofOfDelivery) { this.proofOfDelivery = proofOfDelivery; }
}
