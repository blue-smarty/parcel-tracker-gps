package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocationServiceTest {
    private final ParcelRepository repo = mock(ParcelRepository.class);
    private final ParcelService parcels = new ParcelService(repo, new DemoPricingService());
    private final LocationService service = new LocationService(parcels);

    @Test
    void updateLocationStoresHistoryAndNotifies() {
        Parcel p = new Parcel();
        p.setId("1");
        when(repo.findById("1")).thenReturn(Optional.of(p));
        when(repo.save(any(Parcel.class))).thenAnswer(i -> i.getArgument(0));
        AtomicInteger calls = new AtomicInteger();
        service.addListener((parcel, loc) -> calls.incrementAndGet());

        Parcel result = service.updateLocation("1", new Location(10.0, 20.0, null));

        assertEquals(1, result.getLocationHistory().size());
        assertNotNull(result.getCurrentLocation().getTimestamp());
        assertEquals(ParcelStatus.IN_TRANSIT, result.getStatus());
        assertEquals(1, calls.get());
    }

    @Test
    void createGeneratesTrackingNumber() {
        when(repo.save(any(Parcel.class))).thenAnswer(i -> i.getArgument(0));
        Parcel p = new Parcel();
        p.setSender("a");
        p.setRecipient("b");
        Parcel saved = parcels.create(p);
        assertTrue(saved.getTrackingNumber().startsWith("TRK-"));
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void createCalculatesDemoPriceFromDistanceWeightAndSpeed() {
        when(repo.save(any(Parcel.class))).thenAnswer(i -> i.getArgument(0));
        Parcel parcel = new Parcel();
        parcel.setSender("a");
        parcel.setRecipient("b");
        parcel.setDistanceKm(10.0);
        parcel.setWeightKg(2.0);
        parcel.setDeliverySpeed("SAME_DAY");

        Parcel saved = parcels.create(parcel);

        assertEquals(31.5, saved.getEstimatedPrice());
    }
}
