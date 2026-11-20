package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courier_accounts")
public class CourierAccount {
    @Id
    @Column(name = "courier_id")
    private String courierId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "max_active_parcels")
    private Integer maxActiveParcels;

    @Column(name = "max_payload_weight_kg")
    private Double maxPayloadWeightKg;

    public CourierAccount() {
    }

    public CourierAccount(String courierId, String passwordHash) {
        this.courierId = courierId;
        this.passwordHash = passwordHash;
    }

    public void setCourierId(String courierId) { this.courierId = courierId; }

    public String getCourierId() {
        return courierId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public Integer getMaxActiveParcels() { return maxActiveParcels; }
    public void setMaxActiveParcels(Integer maxActiveParcels) { this.maxActiveParcels = maxActiveParcels; }
    public Double getMaxPayloadWeightKg() { return maxPayloadWeightKg; }
    public void setMaxPayloadWeightKg(Double maxPayloadWeightKg) { this.maxPayloadWeightKg = maxPayloadWeightKg; }
}
