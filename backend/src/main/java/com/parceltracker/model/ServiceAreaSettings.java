package com.parceltracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_area_settings")
public class ServiceAreaSettings {
    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;
    private boolean melbourneMetroOnly;

    public boolean isMelbourneMetroOnly() { return melbourneMetroOnly; }
    public void setMelbourneMetroOnly(boolean value) { melbourneMetroOnly = value; }
}
