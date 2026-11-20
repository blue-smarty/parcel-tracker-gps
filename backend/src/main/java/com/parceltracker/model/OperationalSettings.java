package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "operational_settings")
public class OperationalSettings {
    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;
    private int maxItemsPerBooking = 200;
    private int maxPackageLines = 10;
    private int maxQuantityPerItem = 20;
    private double maxItemWeightKg = 10000;
    private double maxDimensionCm = 10000;
    private LocalTime bookingCutoff = LocalTime.of(17, 0);
    private String timeZone = "Australia/Melbourne";
    private int maxScheduleDaysAhead = 30;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> deliveryWindows = new ArrayList<>(List.of(
            "08:00-10:00", "10:00-12:00", "12:00-14:00", "14:00-16:00", "16:00-18:00"));
    private boolean staleAssignmentEnabled = true;
    private int staleAssignmentMinutes = 120;
    private boolean unassignStaleAssignments;

    public static OperationalSettings defaults() { return new OperationalSettings(); }
    public int getMaxItemsPerBooking() { return maxItemsPerBooking; }
    public void setMaxItemsPerBooking(int value) { maxItemsPerBooking = value; }
    public int getMaxPackageLines() { return maxPackageLines; }
    public void setMaxPackageLines(int value) { maxPackageLines = value; }
    public int getMaxQuantityPerItem() { return maxQuantityPerItem; }
    public void setMaxQuantityPerItem(int value) { maxQuantityPerItem = value; }
    public double getMaxItemWeightKg() { return maxItemWeightKg; }
    public void setMaxItemWeightKg(double value) { maxItemWeightKg = value; }
    public double getMaxDimensionCm() { return maxDimensionCm; }
    public void setMaxDimensionCm(double value) { maxDimensionCm = value; }
    public LocalTime getBookingCutoff() { return bookingCutoff; }
    public void setBookingCutoff(LocalTime value) { bookingCutoff = value; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String value) { timeZone = value; }
    public int getMaxScheduleDaysAhead() { return maxScheduleDaysAhead; }
    public void setMaxScheduleDaysAhead(int value) { maxScheduleDaysAhead = value; }
    public List<String> getDeliveryWindows() { return deliveryWindows; }
    public void setDeliveryWindows(List<String> value) { deliveryWindows = value; }
    public boolean isStaleAssignmentEnabled() { return staleAssignmentEnabled; }
    public void setStaleAssignmentEnabled(boolean value) { staleAssignmentEnabled = value; }
    public int getStaleAssignmentMinutes() { return staleAssignmentMinutes; }
    public void setStaleAssignmentMinutes(int value) { staleAssignmentMinutes = value; }
    public boolean isUnassignStaleAssignments() { return unassignStaleAssignments; }
    public void setUnassignStaleAssignments(boolean value) { unassignStaleAssignments = value; }
}
