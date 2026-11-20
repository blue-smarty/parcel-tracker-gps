package com.parceltracker.controller;

import com.parceltracker.model.BrandingSettings;
import com.parceltracker.repository.BrandingRepository;
import com.parceltracker.service.ConfigurationHistoryService;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class BrandingController {
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg");
    static final int MAX_LOGO_BYTES = 256 * 1024;

    private final BrandingRepository repository;
    private final ConfigurationHistoryService history;

    public BrandingController(BrandingRepository repository, ConfigurationHistoryService history) {
        this.repository = repository;
        this.history = history;
    }

    @GetMapping("/api/branding")
    public BrandingSettings get() {
        return repository.findById(BrandingSettings.SINGLETON_ID).orElseGet(BrandingSettings::defaults);
    }

    @PutMapping("/api/admin/branding")
    @Transactional
    public BrandingSettings update(@RequestBody BrandingSettings request, Authentication authentication) {
        BrandingSettings before = history.snapshot(
                repository.findById(BrandingSettings.SINGLETON_ID).orElse(null), BrandingSettings.class);
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
            WebsiteBrandingController.validateImage(type, logo, MAX_LOGO_BYTES);
        }
        BrandingSettings saved = repository.save(new BrandingSettings(name, request.getPrimaryColor(),
                request.getAccentColor(), type, logo));
        history.record("BRANDING", before, saved, authentication.getName());
        return saved;
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
