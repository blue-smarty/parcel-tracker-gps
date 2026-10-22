package com.parceltracker.repository;

import com.parceltracker.model.Pickup;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PickupRepository extends MongoRepository<Pickup, String> {
}
