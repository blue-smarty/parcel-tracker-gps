package com.parceltracker.repository;

import com.parceltracker.model.SmtpSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpSettingsRepository extends JpaRepository<SmtpSettings, String> {
}
