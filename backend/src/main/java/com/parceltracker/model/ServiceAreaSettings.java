package com.parceltracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "service_area_settings")
public class ServiceAreaSettings {
    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;
    private boolean melbourneMetroOnly;
    private String coverageMode = "UNRESTRICTED";
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> states = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> councils = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<List<List<Double>>> polygons = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> polygonCouriers = new ArrayList<>();

    public boolean isMelbourneMetroOnly() { return melbourneMetroOnly; }
    public void setMelbourneMetroOnly(boolean value) { melbourneMetroOnly = value; }
    public String getCoverageMode() { return coverageMode; }
    public void setCoverageMode(String value) { coverageMode = value; }
    public List<String> getStates() { return states; }
    public void setStates(List<String> value) { states = value; }
    public List<String> getCouncils() { return councils; }
    public void setCouncils(List<String> value) { councils = value; }
    public List<List<List<Double>>> getPolygons() { return polygons; }
    public void setPolygons(List<List<List<Double>>> value) { polygons = value; }
    public List<String> getPolygonCouriers() { return polygonCouriers; }
    public void setPolygonCouriers(List<String> value) { polygonCouriers = value; }
}
