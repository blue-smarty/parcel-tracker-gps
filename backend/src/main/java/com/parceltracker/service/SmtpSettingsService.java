package com.parceltracker.service;

import com.parceltracker.model.SmtpSettings;
import com.parceltracker.repository.SmtpSettingsRepository;
import java.util.Optional;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class SmtpSettingsService {
    public record ConfiguredMail(JavaMailSender sender, String fromAddress) { }

    private final SmtpSettingsRepository repository;
    private final String password;

    public SmtpSettingsService(SmtpSettingsRepository repository, @Value("${spring.mail.password:}") String password) {
        this.repository = repository;
        this.password = password;
    }

    public Optional<SmtpSettings> get() {
        return repository.findById(SmtpSettings.SINGLETON_ID);
    }

    public SmtpSettings save(SmtpSettings settings) {
        if (settings.isAuthentication() && password.isBlank()) {
            throw new IllegalArgumentException("Set SPRING_MAIL_PASSWORD in deployment secrets before enabling SMTP authentication.");
        }
        return repository.save(settings);
    }

    public void clear() {
        repository.deleteById(SmtpSettings.SINGLETON_ID);
    }

    public Optional<ConfiguredMail> configuredMail() {
        return get().map(settings -> {
            if (settings.isAuthentication() && password.isBlank()) {
                throw new IllegalStateException("SMTP authentication is enabled but SPRING_MAIL_PASSWORD is missing.");
            }
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(settings.getHost());
            sender.setPort(settings.getPort());
            if (settings.isAuthentication()) {
                sender.setUsername(settings.getUsername());
                sender.setPassword(password);
            }
            Properties properties = sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth", Boolean.toString(settings.isAuthentication()));
            properties.setProperty("mail.smtp.starttls.enable", Boolean.toString("STARTTLS".equals(settings.getSecurity())));
            properties.setProperty("mail.smtp.starttls.required", Boolean.toString("STARTTLS".equals(settings.getSecurity())));
            properties.setProperty("mail.smtp.ssl.enable", Boolean.toString("TLS".equals(settings.getSecurity())));
            properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
            properties.setProperty("mail.smtp.connectiontimeout", "10000");
            properties.setProperty("mail.smtp.timeout", "10000");
            properties.setProperty("mail.smtp.writetimeout", "10000");
            return new ConfiguredMail(sender, settings.getFromAddress());
        });
    }
}
