package com.parceltracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "website_theme")
public class WebsiteTheme {
    @Id
    private String id = "default";
    @Column(columnDefinition = "text")
    private String css;
    private String fontFamily;

    public WebsiteTheme() {}

    public WebsiteTheme(String css, String fontFamily) {
        this.css = css;
        this.fontFamily = fontFamily;
    }

    public String getCss() { return css; }
    public String getFontFamily() { return fontFamily; }
}
