package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import com.parceltracker.repository.PricingSettingsRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocationServiceTest {
    private final ParcelRepository repo = mock(ParcelRepository.class);
    private final ParcelService parcels = new ParcelService(repo,
            new DemoPricingService(mock(PricingSettingsRepository.class)), mock(ServiceAreaService.class));
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
        Parcel saved = parcels.create(p, "customer@example.com");
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

        Parcel saved = parcels.create(parcel, "customer@example.com");

        assertEquals(31.5, saved.getEstimatedPrice());
    }

    @Test
    void createDiscardsClientSuppliedTrackingAndOperationalState() {
        when(repo.save(any(Parcel.class))).thenAnswer(i -> i.getArgument(0));
        Parcel parcel = new Parcel();
        parcel.setSender("a");
        parcel.setRecipient("b");
        parcel.setTrackingNumber("FORGED");
        parcel.setCourierId("courier-1");
        parcel.setStatus(ParcelStatus.DELIVERED);
        parcel.setCurrentLocation(new Location(10.0, 20.0, null));
        parcel.setFinalizedAt(java.time.Instant.now());
        parcel.setLocationInactive(true);

        Parcel saved = parcels.create(parcel, "customer@example.com");

        assertNotEquals("FORGED", saved.getTrackingNumber());
        assertEquals("customer@example.com", saved.getCustomerEmail());
        assertNull(saved.getCourierId());
        assertEquals(ParcelStatus.CREATED, saved.getStatus());
        assertNull(saved.getCurrentLocation());
        assertTrue(saved.getLocationHistory().isEmpty());
        assertNull(saved.getFinalizedAt());
        assertFalse(saved.isLocationInactive());
    }
}
