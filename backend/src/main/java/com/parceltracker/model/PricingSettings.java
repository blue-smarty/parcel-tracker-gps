package com.parceltracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pricing_settings")
public class PricingSettings {
    public static final String SINGLETON_ID = "default";

    public record RadiusBand(Double upToKm, Double distanceFee) { }

    @Id
    private String id = SINGLETON_ID;
    private double baseFee;
    private double perKmRate;
    private double perKgRate;
    private double sameDayMultiplier;
    private String pricingMode = "ROUTE";
    @JdbcTypeCode(SqlTypes.JSON)
    private List<RadiusBand> radiusBands = new ArrayList<>();

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
    public String getPricingMode() { return pricingMode; }
    public void setPricingMode(String pricingMode) { this.pricingMode = pricingMode; }
    public List<RadiusBand> getRadiusBands() { return radiusBands; }
    public void setRadiusBands(List<RadiusBand> radiusBands) { this.radiusBands = radiusBands; }
}
