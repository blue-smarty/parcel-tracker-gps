package com.parceltracker.repository;

import com.parceltracker.model.PricingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PricingSettingsRepository extends JpaRepository<PricingSettings, String> {
}
