package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.model.ParcelItem;
import com.parceltracker.model.PricingSettings;
import com.parceltracker.model.CourierAccount;
import com.parceltracker.model.ServiceLevelSettings;
import com.parceltracker.repository.ParcelRepository;
import com.parceltracker.repository.CourierAccountRepository;
import com.parceltracker.repository.OperationalSettingsRepository;
import com.parceltracker.repository.PricingSettingsRepository;
import com.parceltracker.repository.ServiceLevelSettingsRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;

class ParcelServiceTest {
    @Test
    void snapshotsBookingTargetAndDiscardsClientDeadline() {
        ParcelRepository repository = mock(ParcelRepository.class);
        ServiceLevelSettingsRepository levels = mock(ServiceLevelSettingsRepository.class);
        ServiceLevelSettings settings = new ServiceLevelSettings();
        settings.setGeneralMinutes(300);
        when(levels.findById(ServiceLevelSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), mock(ServiceAreaService.class),
                mock(CourierPushService.class), levels, mock(CourierAccountRepository.class),
                new OperationalSettingsService(mock(OperationalSettingsRepository.class)));
        Parcel parcel = new Parcel();
        parcel.setDeliverySpeed("GENERAL");
        parcel.setTargetMinutes(1);
        parcel.setTargetDeadline(java.time.Instant.EPOCH);

        Parcel saved = service.create(parcel, "customer@example.com");

        assertEquals(300, saved.getTargetMinutes());
        assertEquals("BOOKING", saved.getTargetStart());
        assertEquals(saved.getCreatedAt().plusSeconds(300 * 60L), saved.getTargetDeadline());
        settings.setGeneralMinutes(400);
        assertEquals(300, saved.getTargetMinutes());
    }

    @Test
    void pickupTargetWaitsForPickupAndDirectHasNoTarget() {
        ParcelRepository repository = mock(ParcelRepository.class);
        ServiceLevelSettingsRepository levels = mock(ServiceLevelSettingsRepository.class);
        ServiceLevelSettings settings = new ServiceLevelSettings();
        settings.setTargetStart("PICKUP");
        when(levels.findById(ServiceLevelSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), mock(ServiceAreaService.class),
                mock(CourierPushService.class), levels, mock(CourierAccountRepository.class),
                new OperationalSettingsService(mock(OperationalSettingsRepository.class)));
        Parcel parcel = new Parcel();
        parcel.setDeliverySpeed("EXPRESS");
        service.create(parcel, null);
        assertEquals(120, parcel.getTargetMinutes());
        assertNull(parcel.getTargetDeadline());
        var pickup = java.time.Instant.parse("2026-01-01T10:00:00Z");
        parcel.startPickupTarget(pickup);
        parcel.startPickupTarget(pickup.plusSeconds(60));
        assertEquals(pickup.plusSeconds(120 * 60L), parcel.getTargetDeadline());

        Parcel direct = new Parcel();
        direct.setDeliverySpeed("DIRECT");
        direct.setTargetMinutes(1);
        service.create(direct, null);
        assertNull(direct.getTargetMinutes());
        assertNull(direct.getTargetDeadline());
    }

    @Test
    void notifiesBothCouriersWhenAssignmentChanges() {
        ParcelRepository repository = mock(ParcelRepository.class);
        CourierPushService push = mock(CourierPushService.class);
        Parcel parcel = new Parcel();
        parcel.setCourierId("old");
        when(repository.findById("parcel")).thenReturn(Optional.of(parcel));
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), mock(ServiceAreaService.class), push,
                mock(ServiceLevelSettingsRepository.class), mock(CourierAccountRepository.class),
                new OperationalSettingsService(mock(OperationalSettingsRepository.class)));

        service.assignCourier("parcel", "new");
        verify(push).deliveryChanged("old");
        verify(push).deliveryChanged("new");
        clearInvocations(push);
        service.assignCourier("parcel", "new");
        verifyNoInteractions(push);
    }

    @Test
    void createsAndAssignsInOneSaveAndResetsClientState() {
        ParcelRepository repository = mock(ParcelRepository.class);
        CourierPushService push = mock(CourierPushService.class);
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), mock(ServiceAreaService.class), push,
                mock(ServiceLevelSettingsRepository.class), mock(CourierAccountRepository.class),
                new OperationalSettingsService(mock(OperationalSettingsRepository.class)));
        Parcel parcel = new Parcel();
        parcel.setId("client-id");
        parcel.setTrackingNumber("client-tracking");
        parcel.setStatus(ParcelStatus.DELIVERED);
        parcel.setCourierId("other");
        parcel.setCustomerEmail("client@example.com");
        parcel.setPickupAddress("1 Main St");

        Parcel result = service.createAssigned(parcel, "courier-1");

        verify(repository, times(1)).save(parcel);
        assertNull(result.getId());
        assertTrue(result.getTrackingNumber().startsWith("TRK-"));
        assertEquals(ParcelStatus.CREATED, result.getStatus());
        assertEquals("courier-1", result.getCourierId());
        assertNull(result.getCustomerEmail());
        assertEquals("1 Main St", result.getPickupAddress());
        verify(push).deliveryChanged("courier-1");
    }

    @Test
    void assignsCourierReturnedForTheDeliveryArea() {
        ParcelRepository repository = mock(ParcelRepository.class);
        ServiceAreaService area = mock(ServiceAreaService.class);
        when(area.requireDelivery(anyString(), anyString())).thenReturn("courier-2");
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), area, mock(CourierPushService.class),
                mock(ServiceLevelSettingsRepository.class), mock(CourierAccountRepository.class),
                new OperationalSettingsService(mock(OperationalSettingsRepository.class)));
        Parcel parcel = new Parcel();
        parcel.setPickupAddress("Pickup");
        parcel.setDestination("Destination");

        Parcel result = service.create(parcel, "customer@example.com");

        assertEquals("courier-2", result.getCourierId());
    }

    @Test
    void doesNotSaveWhenDeliveryIsOutsideServiceArea() {
        ParcelRepository repository = mock(ParcelRepository.class);
        ServiceAreaService area = mock(ServiceAreaService.class);
        doThrow(new ServiceAreaException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "outside"))
                .when(area).requireDelivery(anyString(), anyString());
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), area, mock(CourierPushService.class),
                mock(ServiceLevelSettingsRepository.class), mock(CourierAccountRepository.class),
                new OperationalSettingsService(mock(OperationalSettingsRepository.class)));
        Parcel parcel = new Parcel();
        parcel.setPickupAddress("Pickup");
        parcel.setDestination("Outside");

        assertThrows(ServiceAreaException.class, () -> service.create(parcel, "customer@example.com"));
        assertThrows(ServiceAreaException.class, () -> service.createAssigned(parcel, "courier"));
        verify(repository, never()).save(any());
    }

    @Test
    void pricesAndStoresActualWeightFromTheIndividualItems() {
        ParcelRepository repository = mock(ParcelRepository.class);
        PricingSettingsRepository rates = mock(PricingSettingsRepository.class);
        PricingSettings settings = new PricingSettings(0, 0, 1, 1);
        settings.setWeightMode("GREATER");
        when(rates.findById(PricingSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository, new DemoPricingService(rates),
                mock(ServiceAreaService.class), mock(CourierPushService.class), mock(ServiceLevelSettingsRepository.class),
                mock(CourierAccountRepository.class), new OperationalSettingsService(mock(OperationalSettingsRepository.class)));
        Parcel parcel = new Parcel();
        parcel.setItems(java.util.List.of(new ParcelItem(1.0, 50.0, 40.0, 25.0, 2),
                new ParcelItem(12.0, 10.0, 10.0, 10.0, 1)));
        parcel.setWeightKg(999.0);
        parcel.setDistanceKm(10.0);
        parcel.setDeliverySpeed("SCHEDULED");
        service.create(parcel, "customer@example.com");
        assertEquals(14.0, parcel.getWeightKg());
        assertEquals(32.0, parcel.getEstimatedPrice());
        settings.setWeightMode("ACTUAL");
        assertEquals(32.0, parcel.getEstimatedPrice());
    }

    @Test
    void enforcesConfiguredCourierVehicleParcelAndPayloadLimits() {
        ParcelRepository repository = mock(ParcelRepository.class);
        CourierAccountRepository courierAccounts = mock(CourierAccountRepository.class);
        CourierAccount courier = new CourierAccount("van-1", "hash");
        courier.setVehicleType("VAN");
        courier.setMaxActiveParcels(1);
        courier.setMaxPayloadWeightKg(50.0);
        when(courierAccounts.findForCapacityCheck("van-1")).thenReturn(Optional.of(courier));
        when(repository.findByCourierIdAndStatusNotIn(anyString(), any())).thenReturn(java.util.List.of());
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository, new DemoPricingService(mock(PricingSettingsRepository.class)),
                mock(ServiceAreaService.class), mock(CourierPushService.class),
                mock(ServiceLevelSettingsRepository.class), courierAccounts,
                new OperationalSettingsService(mock(OperationalSettingsRepository.class)));
        Parcel parcel = new Parcel();
        parcel.setWeightKg(20.0);
        service.createAssigned(parcel, "van-1");

        Parcel existing = new Parcel();
        existing.setId("existing");
        existing.setCourierId("van-1");
        existing.setWeightKg(25.0);
        existing.setStatus(ParcelStatus.ACCEPTED);
        when(repository.findByCourierIdAndStatusNotIn(anyString(), any())).thenReturn(java.util.List.of(existing));
        Parcel tooHeavy = new Parcel();
        tooHeavy.setWeightKg(30.0);
        assertThrows(IllegalArgumentException.class, () -> service.createAssigned(tooHeavy, "van-1"));

        when(repository.findByCourierIdAndStatusNotIn(anyString(), any())).thenReturn(java.util.List.of(existing));
        courier.setMaxActiveParcels(2);
        courier.setMaxPayloadWeightKg(40.0);
        Parcel full = new Parcel();
        full.setWeightKg(20.0);
        assertThrows(IllegalArgumentException.class, () -> service.createAssigned(full, "van-1"));
    }
}
