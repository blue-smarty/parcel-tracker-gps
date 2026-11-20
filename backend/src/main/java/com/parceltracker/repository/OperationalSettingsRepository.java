package com.parceltracker.repository;

import com.parceltracker.model.OperationalSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalSettingsRepository extends JpaRepository<OperationalSettings, String> {
}
