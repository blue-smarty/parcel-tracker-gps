package com.parceltracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Branding(String appName, String primaryColor, String accentColor,
                       String logoContentType, String logoBase64) {
    public static Branding defaults() {
        return new Branding("Parcel Tracker GPS", "#1f4e79", "#2e8b57", null, null);
    }
}
