package com.parceltracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_level_settings")
public class ServiceLevelSettings {
    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;
    private int generalMinutes = 240;
    private int expressMinutes = 120;
    private String targetStart = "BOOKING";

    public String getId() { return id; }
    public int getGeneralMinutes() { return generalMinutes; }
    public void setGeneralMinutes(int generalMinutes) { this.generalMinutes = generalMinutes; }
    public int getExpressMinutes() { return expressMinutes; }
    public void setExpressMinutes(int expressMinutes) { this.expressMinutes = expressMinutes; }
    public String getTargetStart() { return targetStart; }
    public void setTargetStart(String targetStart) { this.targetStart = targetStart; }
}
