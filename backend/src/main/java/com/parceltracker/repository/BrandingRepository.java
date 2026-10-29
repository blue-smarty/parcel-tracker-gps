package com.parceltracker.repository;

import com.parceltracker.model.BrandingSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BrandingRepository extends MongoRepository<BrandingSettings, String> {
}
