package com.parceltracker.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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

    @Valid
    @Field("current_location")
    private Location currentLocation;

    private String destination;

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
    public Location getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Location currentLocation) { this.currentLocation = currentLocation; }
    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
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
}
