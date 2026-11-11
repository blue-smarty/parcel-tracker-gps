package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import com.parceltracker.repository.PricingSettingsRepository;
import org.junit.jupiter.api.Test;

class ParcelServiceTest {
    @Test
    void createsAndAssignsInOneSaveAndResetsClientState() {
        ParcelRepository repository = mock(ParcelRepository.class);
        when(repository.save(any(Parcel.class))).thenAnswer(call -> call.getArgument(0));
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), mock(ServiceAreaService.class));
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
    }

    @Test
    void doesNotSaveWhenDeliveryIsOutsideServiceArea() {
        ParcelRepository repository = mock(ParcelRepository.class);
        ServiceAreaService area = mock(ServiceAreaService.class);
        doThrow(new ServiceAreaException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "outside"))
                .when(area).requireDelivery(anyString(), anyString());
        ParcelService service = new ParcelService(repository,
                new DemoPricingService(mock(PricingSettingsRepository.class)), area);
        Parcel parcel = new Parcel();
        parcel.setPickupAddress("Pickup");
        parcel.setDestination("Outside");

        assertThrows(ServiceAreaException.class, () -> service.create(parcel, "customer@example.com"));
        assertThrows(ServiceAreaException.class, () -> service.createAssigned(parcel, "courier"));
        verify(repository, never()).save(any());
    }
}
