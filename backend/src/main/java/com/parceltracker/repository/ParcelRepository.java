package com.parceltracker.repository;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ParcelRepository extends MongoRepository<Parcel, String> {
    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    List<Parcel> findByCourierIdAndStatusNotIn(String courierId, Collection<ParcelStatus> statuses);
    List<Parcel> findByCourierIdAndStatus(String courierId, ParcelStatus status);

    @Query(value = "{ '_id': ?0 }", fields = "{ 'current_location': 1, 'tracking_number': 1 }")
    Optional<Parcel> findLatestLocationById(String id);

    @Query("{ 'status': ?0, 'courier_id': { $nin: [null, ''] }, 'escalated_at': null, "
            + "$or: [ { 'updated_at': { $lte: ?1 } }, { 'updated_at': null, 'created_at': { $lte: ?1 } } ] }")
    List<Parcel> findStaleAssigned(ParcelStatus status, Instant cutoff);

    List<Parcel> findByStatusIn(Collection<ParcelStatus> statuses);

    @Query("{ 'status': ?0, 'finalized_at': null, "
            + "$or: [ { 'updated_at': { $lte: ?1 } }, { 'updated_at': null, 'created_at': { $lte: ?1 } } ] }")
    List<Parcel> findDeliveredBefore(ParcelStatus status, Instant cutoff);
}
