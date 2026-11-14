package com.parceltracker.service;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.parceltracker.repository.ParcelRepository;

@Service
public class ParcelService {
    private final ParcelRepository repository;
    private final DemoPricingService pricing;
    private final ServiceAreaService serviceArea;
    private final CourierPushService push;

    public ParcelService(ParcelRepository repository, DemoPricingService pricing, ServiceAreaService serviceArea,
                         CourierPushService push) {
        this.repository = repository;
        this.pricing = pricing;
        this.serviceArea = serviceArea;
        this.push = push;
    }

    public Parcel create(Parcel parcel, String customerEmail) {
        String courierId = serviceArea.requireDelivery(parcel.getPickupAddress(), parcel.getDestination());
        prepareNewParcel(parcel, customerEmail);
        parcel.setCourierId(courierId);
        Parcel saved = repository.save(parcel);
        push.deliveryChanged(saved.getCourierId());
        return saved;
    }

    public Parcel createAssigned(Parcel parcel, String courierId) {
        serviceArea.requireDelivery(parcel.getPickupAddress(), parcel.getDestination());
        prepareNewParcel(parcel, null);
        parcel.setCourierId(courierId);
        Parcel saved = repository.save(parcel);
        push.deliveryChanged(saved.getCourierId());
        return saved;
    }

    private void prepareNewParcel(Parcel parcel, String customerEmail) {
        Instant now = Instant.now();
        parcel.setId(null);
        parcel.setTrackingNumber("TRK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        parcel.setCustomerEmail(customerEmail);
        parcel.setCourierId(null);
        parcel.setStatus(ParcelStatus.CREATED);
        parcel.setCurrentLocation(null);
        parcel.setLocationHistory(new ArrayList<>());
        parcel.setEscalatedAt(null);
        parcel.setLocationInactive(false);
        parcel.setFinalizedAt(null);
        parcel.setProofOfDelivery(null);
        if (parcel.getDistanceKm() != null && parcel.getWeightKg() != null
                && parcel.getDeliverySpeed() != null) {
            parcel.setEstimatedPrice(pricing.estimate(parcel.getDistanceKm(), parcel.getWeightKg(),
                    parcel.getDeliverySpeed(), parcel.getStraightLineDistanceKm()));
        } else {
            parcel.setEstimatedPrice(null);
        }
        parcel.setCreatedAt(now);
        parcel.setUpdatedAt(now);
    }

    public List<Parcel> findAll() {
        return repository.findAll();
    }

    public List<Parcel> findByCustomerEmail(String customerEmail) {
        return repository.findByCustomerEmailOrderByCreatedAtDesc(customerEmail);
    }

    public Parcel get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Parcel not found: " + id));
    }

    public Parcel update(String id, Parcel changes) {
        Parcel existing = get(id);
        String oldCourier = existing.getCourierId();
        if (changes.getDestination() != null) serviceArea.requireDestination(changes.getDestination());
        if (changes.getSender() != null) existing.setSender(changes.getSender());
        if (changes.getRecipient() != null) existing.setRecipient(changes.getRecipient());
        if (changes.getDestination() != null) existing.setDestination(changes.getDestination());
        if (changes.getCourierId() != null) existing.setCourierId(changes.getCourierId());
        if (changes.getStatus() != null) existing.setStatus(changes.getStatus());
        existing.setUpdatedAt(Instant.now());
        Parcel saved = repository.save(existing);
        if (!Objects.equals(oldCourier, saved.getCourierId())) push.deliveryChanged(oldCourier);
        push.deliveryChanged(saved.getCourierId());
        return saved;
    }

    public void delete(String id) {
        Parcel existing = get(id);
        repository.deleteById(id);
        push.deliveryChanged(existing.getCourierId());
    }

    public Parcel save(Parcel parcel) {
        return repository.save(parcel);
    }

    public Parcel assignCourier(String id, String courierId) {
        Parcel parcel = get(id);
        String oldCourier = parcel.getCourierId();
        parcel.setCourierId(courierId);
        parcel.setUpdatedAt(Instant.now());
        Parcel saved = repository.save(parcel);
        if (!Objects.equals(oldCourier, saved.getCourierId())) {
            push.deliveryChanged(oldCourier);
            push.deliveryChanged(saved.getCourierId());
        }
        return saved;
    }
}
