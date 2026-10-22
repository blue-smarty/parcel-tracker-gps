package com.parceltracker.service;

import com.parceltracker.dto.PickupRequest;
import com.parceltracker.model.Pickup;
import com.parceltracker.model.PickupStatus;
import com.parceltracker.repository.PickupRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class PickupService {
    private final PickupRepository repository;

    public PickupService(PickupRepository repository) {
        this.repository = repository;
    }

    public Pickup create(PickupRequest request) {
        Pickup p = new Pickup();
        p.setContactName(request.getContactName().trim());
        p.setContactPhone(request.getContactPhone().trim());
        p.setStreet(request.getStreet().trim());
        p.setCity(request.getCity().trim());
        p.setPostalCode(request.getPostalCode().trim());
        p.setParcelDescription(request.getParcelDescription().trim());
        p.setWeightKg(request.getWeightKg());
        p.setDimensions(request.getDimensions());
        p.setPickupDate(request.getPickupDate());
        p.setTimeWindow(request.getTimeWindow());
        p.setNotes(request.getNotes());
        p.setStatus(PickupStatus.REQUESTED);
        p.setCreatedAt(Instant.now());
        return repository.save(p);
    }

    public Pickup get(String id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Pickup not found: " + id));
    }
}
