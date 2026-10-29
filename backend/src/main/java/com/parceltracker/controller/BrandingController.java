package com.parceltracker.controller;

import com.parceltracker.model.BrandingSettings;
import com.parceltracker.repository.BrandingRepository;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class BrandingController {
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg");
    static final int MAX_LOGO_BYTES = 256 * 1024;

    private final BrandingRepository repository;

    public BrandingController(BrandingRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/branding")
    public BrandingSettings get() {
        return repository.findById(BrandingSettings.SINGLETON_ID).orElseGet(BrandingSettings::defaults);
    }

    @PutMapping("/api/admin/branding")
    public BrandingSettings update(@RequestBody BrandingSettings request) {
        String name = request.getAppName() == null ? "" : request.getAppName().trim();
        if (name.isEmpty() || name.length() > 60) bad("App name must be 1-60 characters");
        if (!validColor(request.getPrimaryColor()) || !validColor(request.getAccentColor())) {
            bad("Colours must be in #RRGGBB format");
        }
        String type = request.getLogoContentType();
        String logo = request.getLogoBase64();
        if (logo == null || logo.isBlank()) {
            type = null;
            logo = null;
        } else {
            if (type == null || !LOGO_TYPES.contains(type)) bad("Logo must be a PNG or JPEG image");
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(logo);
            } catch (IllegalArgumentException e) {
                throw badRequest("Logo is not valid base64");
            }
            if (bytes.length > MAX_LOGO_BYTES) bad("Logo must be 256 KB or smaller");
        }
        return repository.save(new BrandingSettings(name, request.getPrimaryColor(),
                request.getAccentColor(), type, logo));
    }

    private static boolean validColor(String value) {
        return value != null && COLOR.matcher(value).matches();
    }

    private static void bad(String message) {
        throw badRequest(message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
