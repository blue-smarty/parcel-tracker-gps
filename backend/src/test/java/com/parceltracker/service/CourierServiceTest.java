package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.model.DeliveryProof;
import com.parceltracker.dto.CourierEarnings;
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
    void acceptsAssignedRequest() {
        Parcel parcel = parcel(ParcelStatus.CREATED);
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel));
        when(repository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel accepted = service.decideRequest("courier-1", "parcel-1", true);

        assertEquals(ParcelStatus.ACCEPTED, accepted.getStatus());
        verify(repository).save(accepted);
    }

    @Test
    void rejectionReturnsRequestToUnassignedQueue() {
        Parcel parcel = parcel(ParcelStatus.CREATED);
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel));
        when(repository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel rejected = service.decideRequest("courier-1", "parcel-1", false);

        assertNull(rejected.getCourierId());
        assertEquals(ParcelStatus.CREATED, rejected.getStatus());
    }

    @Test
    void completesDeliveryOnlyWithProofAndRecordsEarnings() {
        Parcel parcel = parcel(ParcelStatus.OUT_FOR_DELIVERY);
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel));
        when(repository.save(any(Parcel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DeliveryProof proof = new DeliveryProof();
        proof.setBarcode("BAR-123");
        proof.setTipAmount(2.5);
        proof.setPayoutAmount(8.0);

        Parcel delivered = service.completeDelivery("courier-1", "parcel-1", proof);

        assertEquals(ParcelStatus.DELIVERED, delivered.getStatus());
        assertSame(proof, delivered.getProofOfDelivery());
        verify(locations).publishStatusUpdate(delivered);
    }

    @Test
    void requiresProofToCompleteDelivery() {
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel(ParcelStatus.OUT_FOR_DELIVERY)));

        assertThrows(IllegalArgumentException.class,
                () -> service.completeDelivery("courier-1", "parcel-1", new DeliveryProof()));
        verify(repository, never()).save(any());
    }

    @Test
    void summarizesCompletedJobsTipsAndPayouts() {
        Parcel completed = parcel(ParcelStatus.DELIVERED);
        DeliveryProof proof = new DeliveryProof();
        proof.setTipAmount(3.25);
        proof.setPayoutAmount(12.50);
        completed.setProofOfDelivery(proof);
        when(repository.findByCourierIdAndStatus("courier-1", ParcelStatus.DELIVERED))
                .thenReturn(List.of(completed));

        CourierEarnings earnings = service.getEarnings("courier-1");

        assertEquals(1, earnings.completedDeliveries());
        assertEquals(3.25, earnings.tips());
        assertEquals(12.50, earnings.payouts());
    }

    @Test
    void directDeliveredStatusUpdateIsRejected() {
        when(repository.findById("parcel-1")).thenReturn(Optional.of(parcel(ParcelStatus.OUT_FOR_DELIVERY)));

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
