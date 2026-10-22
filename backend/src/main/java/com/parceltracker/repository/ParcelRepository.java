package com.parceltracker.repository;

import com.parceltracker.model.Parcel;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ParcelRepository extends MongoRepository<Parcel, String> {
    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    @Query(value = "{ '_id': ?0 }", fields = "{ 'current_location': 1, 'tracking_number': 1 }")
    Optional<Parcel> findLatestLocationById(String id);
}
