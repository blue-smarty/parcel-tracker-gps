package com.parceltracker.repository;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParcelRepository extends JpaRepository<Parcel, String> {
    Optional<Parcel> findByTrackingNumber(String trackingNumber);
    List<Parcel> findByCourierIdAndStatusNotIn(String courierId, Collection<ParcelStatus> statuses);
    List<Parcel> findByCourierIdAndStatus(String courierId, ParcelStatus status);

    @Query("select p from Parcel p where p.status = :status "
            + "and p.courierId is not null and p.courierId <> '' and p.escalatedAt is null "
            + "and (p.updatedAt <= :cutoff or (p.updatedAt is null and p.createdAt <= :cutoff))")
    List<Parcel> findStaleAssigned(@Param("status") ParcelStatus status, @Param("cutoff") Instant cutoff);

    List<Parcel> findByStatusIn(Collection<ParcelStatus> statuses);

    @Query("select p from Parcel p where p.status = :status and p.finalizedAt is null "
            + "and (p.updatedAt <= :cutoff or (p.updatedAt is null and p.createdAt <= :cutoff))")
    List<Parcel> findDeliveredBefore(@Param("status") ParcelStatus status, @Param("cutoff") Instant cutoff);
}
