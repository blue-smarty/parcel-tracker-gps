package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CourierServiceTest {
    private final ParcelRepository repository = mock(ParcelRepository.class);
    private final LocationService locations = mock(LocationService.class);
    private final CourierService service = new CourierService(repository, locations);

    @Test
    void listsOnlyAssignedActiveDeliveries() {
        when(repository.findByCourierIdAndStatusNotIn(eq("courier-1"), any()))
                .thenReturn(List.of(new Parcel()));

        assertEquals(1, service.getActiveDeliveries("courier-1").size());
        verify(repository).findByCourierIdAndStatusNotIn(eq("courier-1"), any());
    }

    @Test
    void updatesAnAllowedStatusAndPublishesTrackingEvent() {
        Parcel parcel = parcel(ParcelStatus.CREATED);
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel));
        when(repository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel updated = service.updateStatus("courier-1", "parcel-1", ParcelStatus.PICKED_UP);

        assertEquals(ParcelStatus.PICKED_UP, updated.getStatus());
        assertNotNull(updated.getUpdatedAt());
        verify(locations).publishStatusUpdate(updated);
    }

    @Test
    void rejectsInvalidTransition() {
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel(ParcelStatus.CREATED)));

        assertThrows(IllegalStateException.class,
                () -> service.updateStatus("courier-1", "parcel-1", ParcelStatus.DELIVERED));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsParcelAssignedToAnotherCourier() {
        Parcel parcel = parcel(ParcelStatus.PICKED_UP);
        parcel.setCourierId("courier-2");
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel));

        assertThrows(NoSuchElementException.class,
                () -> service.updateLocation("courier-1", "parcel-1", new Location(10.0, 20.0, null)));
        verify(locations, never()).updateLocation(any(), any());
    }

    @Test
    void requiresPickupBeforeCourierCanPublishLocation() {
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel(ParcelStatus.CREATED)));

        assertThrows(IllegalStateException.class,
                () -> service.updateLocation("courier-1", "parcel-1", new Location(10.0, 20.0, null)));
        verify(locations, never()).updateLocation(any(), any());
    }

    private static Parcel parcel(ParcelStatus status) {
        Parcel parcel = new Parcel();
        parcel.setId("parcel-1");
        parcel.setCourierId("courier-1");
        parcel.setStatus(status);
        return parcel;
    }
}
