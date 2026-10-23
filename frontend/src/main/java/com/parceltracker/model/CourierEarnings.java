package com.parceltracker.model;

public class CourierEarnings {
    private long completedDeliveries;
    private double tips;
    private double payouts;

    public long getCompletedDeliveries() { return completedDeliveries; }
    public void setCompletedDeliveries(long completedDeliveries) { this.completedDeliveries = completedDeliveries; }
    public double getTips() { return tips; }
    public void setTips(double tips) { this.tips = tips; }
    public double getPayouts() { return payouts; }
    public void setPayouts(double payouts) { this.payouts = payouts; }
}
