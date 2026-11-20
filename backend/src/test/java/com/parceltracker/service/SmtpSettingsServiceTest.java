package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.parceltracker.model.SmtpSettings;
import com.parceltracker.repository.SmtpSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class SmtpSettingsServiceTest {
    @Test
    void buildsSenderUsingDeploymentPasswordAndRequiredTls() {
        SmtpSettingsRepository repository = mock(SmtpSettingsRepository.class);
        SmtpSettings settings = new SmtpSettings();
        settings.setHost("smtp.example.com");
        settings.setPort(587);
        settings.setUsername("mailer");
        settings.setFromAddress("mail@example.com");
        settings.setAuthentication(true);
        settings.setSecurity("STARTTLS");
        when(repository.findById(SmtpSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        var mail = new SmtpSettingsService(repository, "secret").configuredMail().orElseThrow();
        JavaMailSenderImpl sender = (JavaMailSenderImpl) mail.sender();
        assertEquals("smtp.example.com", sender.getHost());
        assertEquals("secret", sender.getPassword());
        assertEquals("mail@example.com", mail.fromAddress());
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity"));
        assertThrows(IllegalArgumentException.class, () -> new SmtpSettingsService(repository, "").save(settings));
        assertThrows(IllegalStateException.class, () -> new SmtpSettingsService(repository, "").configuredMail());
    }
}
