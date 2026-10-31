package com.parceltracker.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "branding")
public class BrandingSettings {
    public static final String SINGLETON_ID = "default";

    @Id
    private String id = SINGLETON_ID;
    private String appName;
    private String primaryColor;
    private String accentColor;
    private String logoContentType;
    @Column(columnDefinition = "text")
    private String logoBase64;

    public BrandingSettings() {
    }

    public BrandingSettings(String appName, String primaryColor, String accentColor,
                            String logoContentType, String logoBase64) {
        this.appName = appName;
        this.primaryColor = primaryColor;
        this.accentColor = accentColor;
        this.logoContentType = logoContentType;
        this.logoBase64 = logoBase64;
    }

    public static BrandingSettings defaults() {
        return new BrandingSettings("Parcel Tracker GPS", "#1f4e79", "#2e8b57", null, null);
    }

    public String getId() { return id; }
    public String getAppName() { return appName; }
    public String getPrimaryColor() { return primaryColor; }
    public String getAccentColor() { return accentColor; }
    public String getLogoContentType() { return logoContentType; }
    public String getLogoBase64() { return logoBase64; }
}
