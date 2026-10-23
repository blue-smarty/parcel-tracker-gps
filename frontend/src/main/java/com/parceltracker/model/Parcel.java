package com.parceltracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Parcel {
    private String id;
    private String trackingNumber;
    private String sender;
    private String recipient;
    private String pickupAddress;
    private Location currentLocation;
    private String destination;
    private String packageSize;
    private Double weightKg;
    private String deliverySpeed;
    private LocalDate scheduledDate;
    private String scheduledWindow;
    private Double distanceKm;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Double estimatedPrice;
    private String paymentMethod;
    private String customerContact;
    private String status;
    private List<Location> locationHistory = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    private DeliveryProof proofOfDelivery;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<Location> getLocationHistory() { return locationHistory; }
    public void setLocationHistory(List<Location> locationHistory) { this.locationHistory = locationHistory; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public DeliveryProof getProofOfDelivery() { return proofOfDelivery; }
    public void setProofOfDelivery(DeliveryProof proofOfDelivery) { this.proofOfDelivery = proofOfDelivery; }
}
