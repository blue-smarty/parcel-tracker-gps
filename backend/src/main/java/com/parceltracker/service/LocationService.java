package com.parceltracker.service;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.time.Instant;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class LocationService {
    private final ParcelService parcelService;
    private final List<BiConsumer<Parcel, Location>> listeners = new CopyOnWriteArrayList<>();

    public LocationService(ParcelService parcelService) {
        this.parcelService = parcelService;
    }

    public void addListener(BiConsumer<Parcel, Location> listener) {
        listeners.add(listener);
    }

    public Parcel updateLocation(String parcelId, Location location) {
        Parcel parcel = parcelService.get(parcelId);
        if (location.getTimestamp() == null) {
            location.setTimestamp(Instant.now());
        }
        parcel.setCurrentLocation(location);
        parcel.getLocationHistory().add(location);
        if (parcel.getStatus() == ParcelStatus.CREATED) {
            parcel.setStatus(ParcelStatus.IN_TRANSIT);
        }
        parcel.setUpdatedAt(Instant.now());
        Parcel saved = parcelService.save(parcel);
        listeners.forEach(l -> l.accept(saved, location));
        return saved;
    }

    public List<Location> getHistory(String parcelId) {
        return parcelService.get(parcelId).getLocationHistory();
    }
}
