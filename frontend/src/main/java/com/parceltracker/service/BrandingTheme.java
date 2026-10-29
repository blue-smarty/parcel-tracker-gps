package com.parceltracker.service;

import com.parceltracker.model.Branding;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** Applies admin-configured branding (name, colours, logo) to JavaFX windows. */
public final class BrandingTheme {
    private BrandingTheme() {
    }

    public static Branding load(ApiService api) {
        try {
            return api.getBranding();
        } catch (Exception e) {
            return Branding.defaults();
        }
    }

    public static Image logo(Branding branding) {
        if (branding.logoBase64() == null || branding.logoBase64().isBlank()) return null;
        try {
            Image image = new Image(new ByteArrayInputStream(Base64.getDecoder().decode(branding.logoBase64())));
            return image.isError() ? null : image;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String style(Branding branding) {
        String primary = safeColor(branding.primaryColor(), "#1f4e79");
        return "-fx-accent: " + primary + "; -fx-focus-color: " + primary
                + "; -fx-faint-focus-color: " + primary + "22;";
    }

    public static void apply(Stage stage, Branding branding) {
        if (stage.getScene() != null) {
            Parent root = stage.getScene().getRoot();
            String existing = root.getStyle() == null ? "" : root.getStyle();
            root.setStyle(existing + " " + style(branding));
        }
        String title = stage.getTitle() == null ? "" : stage.getTitle();
        if (!title.startsWith(branding.appName())) {
            stage.setTitle(title.isBlank() ? branding.appName() : branding.appName() + " — " + title);
        }
        Image logo = logo(branding);
        if (logo != null) stage.getIcons().setAll(logo);
    }

    private static String safeColor(String value, String fallback) {
        return value != null && value.matches("^#[0-9a-fA-F]{6}$") ? value : fallback;
    }
}
