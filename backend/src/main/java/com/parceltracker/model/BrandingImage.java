package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "branding_images")
public class BrandingImage {
    @Id
    private String id;
    private String name;
    private String contentType;
    @Column(columnDefinition = "text")
    private String data;

    public BrandingImage() {}

    public BrandingImage(String id, String name, String contentType, String data) {
        this.id = id;
        this.name = name;
        this.contentType = contentType;
        this.data = data;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getContentType() { return contentType; }
    public String getData() { return data; }
}
