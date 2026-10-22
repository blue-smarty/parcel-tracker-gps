package com.parceltracker.repository;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ParcelRepository extends MongoRepository<Parcel, String> {
    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    List<Parcel> findByCourierIdAndStatusNotIn(String courierId, Collection<ParcelStatus> statuses);

    @Query(value = "{ '_id': ?0 }", fields = "{ 'current_location': 1, 'tracking_number': 1 }")
    Optional<Parcel> findLatestLocationById(String id);
}
