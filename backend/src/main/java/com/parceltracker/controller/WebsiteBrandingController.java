package com.parceltracker.controller;

import com.parceltracker.model.BrandingImage;
import com.parceltracker.model.BrandingSettings;
import com.parceltracker.model.WebsiteTheme;
import com.parceltracker.repository.BrandingImageRepository;
import com.parceltracker.repository.BrandingRepository;
import com.parceltracker.repository.WebsiteThemeRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class WebsiteBrandingController {
    private static final Set<String> FONTS = Set.of("Arial", "Georgia", "Verdana", "Tahoma");
    private static final int MAX_CSS_LENGTH = 200_000;
    private static final int MAX_IMAGE_BYTES = 1024 * 1024;
    private final BrandingRepository branding;
    private final WebsiteThemeRepository themes;
    private final BrandingImageRepository images;

    public WebsiteBrandingController(BrandingRepository branding, WebsiteThemeRepository themes,
                                     BrandingImageRepository images) {
        this.branding = branding;
        this.themes = themes;
        this.images = images;
    }

    record WebsiteBranding(boolean configured, BrandingSettings branding, String fontFamily) {}
    record ThemeRequest(String css, String fontFamily) {}
    record ImageRequest(String name, String contentType, String data) {}
    record ImageInfo(String id, String name, String url) {}

    @GetMapping("/api/branding/website")
    public WebsiteBranding website() {
        var saved = branding.findById(BrandingSettings.SINGLETON_ID);
        return new WebsiteBranding(saved.isPresent(), saved.orElseGet(BrandingSettings::defaults),
                themes.findById("default").map(WebsiteTheme::getFontFamily).orElse(null));
    }

    @GetMapping(value = "/api/branding/stylesheet", produces = "text/css")
    public ResponseEntity<String> stylesheet() throws IOException {
        String css = themes.findById("default").map(WebsiteTheme::getCss).orElse(null);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(new MediaType("text", "css", StandardCharsets.UTF_8))
                .body(css == null ? defaultCss() : css);
    }

    @GetMapping("/api/admin/website-theme")
    public ThemeRequest theme() throws IOException {
        WebsiteTheme theme = themes.findById("default").orElseGet(WebsiteTheme::new);
        return new ThemeRequest(theme.getCss() == null ? defaultCss() : theme.getCss(),
                theme.getFontFamily() == null ? "Arial" : theme.getFontFamily());
    }

    @GetMapping("/api/admin/website-theme/default")
    public ThemeRequest defaultTheme() throws IOException {
        return new ThemeRequest(defaultCss(), "Arial");
    }

    @PutMapping("/api/admin/website-theme")
    public ThemeRequest updateTheme(@RequestBody ThemeRequest request) {
        if (request.css() == null || request.css().length() > MAX_CSS_LENGTH) {
            throw bad("Stylesheet must be at most 200,000 characters");
        }
        if (request.fontFamily() == null || !FONTS.contains(request.fontFamily())) throw bad("Choose a supported font");
        themes.save(new WebsiteTheme(request.css(), request.fontFamily()));
        return request;
    }

    @GetMapping("/api/admin/branding/images")
    public List<ImageInfo> listImages() {
        return images.findAll().stream().map(WebsiteBrandingController::info).toList();
    }

    @PostMapping("/api/admin/branding/images")
    public ImageInfo uploadImage(@RequestBody ImageRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 100) throw bad("Image name must be 1-100 characters");
        byte[] bytes = validateImage(request.contentType(), request.data(), MAX_IMAGE_BYTES);
        BrandingImage image = new BrandingImage(UUID.randomUUID().toString(), name, request.contentType(),
                Base64.getEncoder().encodeToString(bytes));
        return info(images.save(image));
    }

    @GetMapping("/api/branding/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        BrandingImage image = images.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(Base64.getDecoder().decode(image.getData()));
    }

    @DeleteMapping("/api/admin/branding/images/{id}")
    public ResponseEntity<Void> deleteImage(@PathVariable String id) {
        if (!images.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        images.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    static byte[] validateImage(String type, String data, int maxBytes) {
        if (type == null || !Set.of("image/png", "image/jpeg").contains(type)) throw bad("Image must be PNG or JPEG");
        if (data == null || data.length() > ((maxBytes + 2) / 3) * 4) throw bad("Image is too large");
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            throw bad("Image is not valid base64");
        }
        if (bytes.length == 0 || bytes.length > maxBytes) throw bad("Image is empty or too large");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("Invalid image");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                if (!(type.equals("image/png") && format.equalsIgnoreCase("png"))
                        && !(type.equals("image/jpeg") && format.equalsIgnoreCase("jpeg"))) {
                    throw bad("Image content does not match its type");
                }
                if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) {
                    throw bad("Image dimensions must not exceed 4096 pixels");
                }
                if (reader.read(0) == null) throw bad("Invalid image");
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw bad("Invalid image");
        }
        return bytes;
    }

    private static ImageInfo info(BrandingImage image) {
        return new ImageInfo(image.getId(), image.getName(), "/api/branding/images/" + image.getId());
    }

    private static String defaultCss() throws IOException {
        try (var input = new ClassPathResource("static/styles.css").getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
