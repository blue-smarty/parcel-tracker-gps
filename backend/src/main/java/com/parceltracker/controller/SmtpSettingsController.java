package com.parceltracker.controller;

import com.parceltracker.model.SmtpSettings;
import com.parceltracker.service.SmtpSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import com.parceltracker.service.ConfigurationHistoryService;
import org.springframework.transaction.annotation.Transactional;

@RestController
public class SmtpSettingsController {
    public record Request(
            @NotBlank @Size(max = 253) @Pattern(regexp = "[A-Za-z0-9.-]+") String host,
            @NotNull @Min(1) @Max(65535) Integer port,
            @NotNull @Size(max = 255) String username,
            @NotBlank @Email @Size(max = 255) String fromAddress,
            @NotNull @Pattern(regexp = "NONE|STARTTLS|TLS") String security,
            @NotNull Boolean authentication) { }

    private final SmtpSettingsService service;
    private final ConfigurationHistoryService history;

    public SmtpSettingsController(SmtpSettingsService service, ConfigurationHistoryService history) {
        this.service = service;
        this.history = history;
    }

    @GetMapping("/api/admin/smtp")
    public ResponseEntity<SmtpSettings> get() {
        return service.get().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/api/admin/smtp")
    @Transactional
    public ResponseEntity<Void> clear(Authentication authentication) {
        SmtpSettings before = history.snapshot(service.get().orElse(null), SmtpSettings.class);
        service.clear();
        if (before != null) history.record("SMTP", before, null, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/smtp")
    @Transactional
    public SmtpSettings update(@Valid @RequestBody Request request, Authentication authentication) {
        if (request.authentication() && (request.username().isBlank() || "NONE".equals(request.security()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SMTP authentication requires a username and TLS.");
        }
        SmtpSettings before = history.snapshot(service.get().orElse(null), SmtpSettings.class);
        SmtpSettings settings = service.get().orElseGet(SmtpSettings::new);
        settings.setHost(request.host().trim());
        settings.setPort(request.port());
        settings.setUsername(request.username().trim());
        settings.setFromAddress(request.fromAddress().trim());
        settings.setSecurity(request.security());
        settings.setAuthentication(request.authentication());
        try {
            SmtpSettings saved = service.save(settings);
            history.record("SMTP", before, saved, authentication.getName());
            return saved;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> invalidSettings(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidFields(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .findFirst().orElse("Invalid SMTP settings.");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
