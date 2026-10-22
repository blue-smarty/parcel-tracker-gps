package com.parceltracker.service;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.parceltracker.repository.ParcelRepository;

@Service
public class ParcelService {
    private final ParcelRepository repository;

    public ParcelService(ParcelRepository repository) {
        this.repository = repository;
    }

    public Parcel create(Parcel parcel) {
        Instant now = Instant.now();
        parcel.setId(null);
        if (parcel.getTrackingNumber() == null || parcel.getTrackingNumber().isBlank()) {
            parcel.setTrackingNumber("TRK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        }
        if (parcel.getStatus() == null) {
            parcel.setStatus(ParcelStatus.CREATED);
        }
        parcel.getLocationHistory().clear();
        if (parcel.getCurrentLocation() != null) {
            if (parcel.getCurrentLocation().getTimestamp() == null) {
                parcel.getCurrentLocation().setTimestamp(now);
            }
            parcel.getLocationHistory().add(parcel.getCurrentLocation());
        }
        parcel.setCreatedAt(now);
        parcel.setUpdatedAt(now);
        return repository.save(parcel);
    }

    public List<Parcel> findAll() {
        return repository.findAll();
    }

    public Parcel get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Parcel not found: " + id));
    }

    public Parcel update(String id, Parcel changes) {
        Parcel existing = get(id);
        if (changes.getSender() != null) existing.setSender(changes.getSender());
        if (changes.getRecipient() != null) existing.setRecipient(changes.getRecipient());
        if (changes.getDestination() != null) existing.setDestination(changes.getDestination());
        if (changes.getCourierId() != null) existing.setCourierId(changes.getCourierId());
        if (changes.getStatus() != null) existing.setStatus(changes.getStatus());
        existing.setUpdatedAt(Instant.now());
        return repository.save(existing);
    }

    public void delete(String id) {
        get(id);
        repository.deleteById(id);
    }

    public Parcel save(Parcel parcel) {
        return repository.save(parcel);
    }

    public Parcel assignCourier(String id, String courierId) {
        Parcel parcel = get(id);
        parcel.setCourierId(courierId);
        parcel.setUpdatedAt(Instant.now());
        return repository.save(parcel);
    }
}
