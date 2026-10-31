package com.parceltracker.repository;

import com.parceltracker.model.BrandingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandingRepository extends JpaRepository<BrandingSettings, String> {
}
