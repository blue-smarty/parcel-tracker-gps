package com.parceltracker.repository;

import com.parceltracker.model.ServiceLevelSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceLevelSettingsRepository extends JpaRepository<ServiceLevelSettings, String> {
}
