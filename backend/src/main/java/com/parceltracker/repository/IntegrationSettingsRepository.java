package com.parceltracker.repository;

import com.parceltracker.model.IntegrationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSettingsRepository extends JpaRepository<IntegrationSettings, String> {}
