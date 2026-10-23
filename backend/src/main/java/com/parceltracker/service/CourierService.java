package com.parceltracker.service;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.model.DeliveryProof;
import com.parceltracker.dto.CourierEarnings;
import com.parceltracker.repository.ParcelRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class CourierService {
    private static final List<ParcelStatus> TERMINAL_STATUSES =
            List.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED, ParcelStatus.RETURNED, ParcelStatus.CANCELLED);

    private final ParcelRepository repository;
    private final LocationService locationService;

    public CourierService(ParcelRepository repository, LocationService locationService) {
        this.repository = repository;
        this.locationService = locationService;
    }

    public List<Parcel> getActiveDeliveries(String courierId) {
        requireCourierId(courierId);
        return repository.findByCourierIdAndStatusNotIn(courierId, TERMINAL_STATUSES);
    }

    public Parcel updateStatus(String courierId, String parcelId, ParcelStatus nextStatus) {
        Parcel parcel = assignedParcel(courierId, parcelId);
        if (nextStatus == ParcelStatus.DELIVERED) {
            throw new IllegalStateException("Proof of delivery is required to complete a delivery");
        }
        if (nextStatus == null || !allowedTransitions(parcel.getStatus()).contains(nextStatus)) {
            throw new IllegalStateException("Invalid delivery status transition from " + parcel.getStatus()
                    + " to " + nextStatus);
        }
        parcel.setStatus(nextStatus);
        parcel.setUpdatedAt(Instant.now());
        Parcel saved = repository.save(parcel);
        locationService.publishStatusUpdate(saved);
        return saved;
    }

    public Parcel decideRequest(String courierId, String parcelId, boolean accepted) {
        Parcel parcel = assignedParcel(courierId, parcelId);
        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new IllegalStateException("Only new delivery requests can be accepted or rejected");
        }
        if (accepted) {
            parcel.setStatus(ParcelStatus.ACCEPTED);
        } else {
            parcel.setCourierId(null);
        }
        parcel.setUpdatedAt(Instant.now());
        return repository.save(parcel);
    }

    public Parcel completeDelivery(String courierId, String parcelId, DeliveryProof proof) {
        Parcel parcel = assignedParcel(courierId, parcelId);
        if (parcel.getStatus() != ParcelStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException("Proof of delivery can only be submitted for an out-for-delivery parcel");
        }
        if (proof == null || (isBlank(proof.getRecipientSignature())
                && isBlank(proof.getBarcode()) && isBlank(proof.getPhotoBase64()))) {
            throw new IllegalArgumentException("Capture a recipient signature, barcode, or drop-off photo");
        }
        parcel.setProofOfDelivery(proof);
        parcel.setStatus(ParcelStatus.DELIVERED);
        parcel.setUpdatedAt(Instant.now());
        Parcel saved = repository.save(parcel);
        locationService.publishStatusUpdate(saved);
        return saved;
    }

    public CourierEarnings getEarnings(String courierId) {
        requireCourierId(courierId);
        List<Parcel> completed = repository.findByCourierIdAndStatus(courierId, ParcelStatus.DELIVERED);
        double tips = 0.0;
        double payouts = 0.0;
        for (Parcel parcel : completed) {
            DeliveryProof proof = parcel.getProofOfDelivery();
            if (proof != null) {
                tips += proof.getTipAmount() == null ? 0.0 : proof.getTipAmount();
                payouts += proof.getPayoutAmount() == null ? 0.0 : proof.getPayoutAmount();
            }
        }
        return new CourierEarnings(completed.size(), tips, payouts);
    }

    public Parcel updateLocation(String courierId, String parcelId, Location location) {
        Parcel parcel = assignedParcel(courierId, parcelId);
        if (parcel.getStatus() != ParcelStatus.PICKED_UP
                && parcel.getStatus() != ParcelStatus.IN_TRANSIT
                && parcel.getStatus() != ParcelStatus.OUT_FOR_DELIVERY) {
            throw new IllegalStateException("Location updates require an active, picked-up delivery");
        }
        return locationService.updateLocation(parcelId, location);
    }

    private Parcel assignedParcel(String courierId, String parcelId) {
        requireCourierId(courierId);
        Parcel parcel = repository.findById(parcelId)
                .orElseThrow(() -> new NoSuchElementException("Parcel not found: " + parcelId));
        if (!courierId.equals(parcel.getCourierId())) {
            throw new NoSuchElementException("Parcel not assigned to courier: " + parcelId);
        }
        return parcel;
    }

    private static void requireCourierId(String courierId) {
        if (courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("Courier ID is required");
        }
    }

    private static Set<ParcelStatus> allowedTransitions(ParcelStatus current) {
        if (current == null) return Set.of();
        return switch (current) {
            case CREATED -> Set.of(ParcelStatus.PICKED_UP, ParcelStatus.FAILED);
            case ACCEPTED -> Set.of(ParcelStatus.PICKED_UP, ParcelStatus.FAILED);
            case PICKED_UP -> Set.of(ParcelStatus.IN_TRANSIT, ParcelStatus.FAILED);
            case IN_TRANSIT -> Set.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED);
            case OUT_FOR_DELIVERY -> Set.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED);
            default -> Set.of();
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
