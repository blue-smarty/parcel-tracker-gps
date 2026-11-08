package com.parceltracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pricing_settings")
public class PricingSettings {
    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;
    private double baseFee;
    private double perKmRate;
    private double perKgRate;
    private double sameDayMultiplier;

    public PricingSettings() {
    }

    public PricingSettings(double baseFee, double perKmRate, double perKgRate, double sameDayMultiplier) {
        this.baseFee = baseFee;
        this.perKmRate = perKmRate;
        this.perKgRate = perKgRate;
        this.sameDayMultiplier = sameDayMultiplier;
    }

    public static PricingSettings defaults() {
        return new PricingSettings(5.0, 1.2, 0.5, 1.75);
    }

    public String getId() { return id; }
    public double getBaseFee() { return baseFee; }
    public void setBaseFee(double baseFee) { this.baseFee = baseFee; }
    public double getPerKmRate() { return perKmRate; }
    public void setPerKmRate(double perKmRate) { this.perKmRate = perKmRate; }
    public double getPerKgRate() { return perKgRate; }
    public void setPerKgRate(double perKgRate) { this.perKgRate = perKgRate; }
    public double getSameDayMultiplier() { return sameDayMultiplier; }
    public void setSameDayMultiplier(double sameDayMultiplier) { this.sameDayMultiplier = sameDayMultiplier; }
}
