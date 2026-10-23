package com.parceltracker.model;

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
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "parcels")
@CompoundIndexes({
        @CompoundIndex(name = "status_updated_at", def = "{'status': 1, 'updated_at': 1}"),
        @CompoundIndex(name = "status_created_at", def = "{'status': 1, 'created_at': 1}"),
        @CompoundIndex(name = "courier_status", def = "{'courier_id': 1, 'status': 1}")
})
public class Parcel {
    @Id
    private String id;

    @Indexed(unique = true)
    @Field("tracking_number")
    private String trackingNumber;

    @NotBlank
    private String sender;

    @NotBlank
    private String recipient;

    @Indexed
    @Field("courier_id")
    private String courierId;

    @Field("pickup_address")
    private String pickupAddress;

    @Valid
    @Field("current_location")
    private Location currentLocation;

    private String destination;

    @Field("package_size")
    @Pattern(regexp = "SMALL|MEDIUM|LARGE")
    private String packageSize;

    @DecimalMin(value = "0.0", inclusive = false)
    @Field("weight_kg")
    private Double weightKg;

    @Field("delivery_speed")
    @Pattern(regexp = "SAME_DAY|SCHEDULED")
    private String deliverySpeed;

    @Field("scheduled_date")
    private LocalDate scheduledDate;

    @Field("scheduled_window")
    private String scheduledWindow;

    @Positive
    @Field("distance_km")
    private Double distanceKm;

    @Field("destination_latitude")
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double destinationLatitude;

    @Field("destination_longitude")
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double destinationLongitude;

    @Field("estimated_price")
    private Double estimatedPrice;

    @Field("payment_method")
    @Pattern(regexp = "DEMO_CARD|DEMO_MOBILE_WALLET|CASH_ON_DELIVERY")
    private String paymentMethod;

    @Field("customer_contact")
    private String customerContact;

    private ParcelStatus status = ParcelStatus.CREATED;

    @Field("location_history")
    private List<Location> locationHistory = new ArrayList<>();

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    @Field("escalated_at")
    private Instant escalatedAt;

    @Field("location_inactive")
    private boolean locationInactive;

    @Field("finalized_at")
    private Instant finalizedAt;

    @Valid
    @Field("proof_of_delivery")
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
    public DeliveryProof getProofOfDelivery() { return proofOfDelivery; }
    public void setProofOfDelivery(DeliveryProof proofOfDelivery) { this.proofOfDelivery = proofOfDelivery; }
}
