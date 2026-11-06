package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parceltracker.model.DriverAction;
import com.parceltracker.model.DriverActionType;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.DriverActionRepository;
import com.parceltracker.repository.ParcelRepository;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverActionServiceTest {
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final DriverActionRepository actions = mock(DriverActionRepository.class);
    private final ContactGateway gateway = mock(ContactGateway.class);
    private final DriverActionService service = new DriverActionService(parcels, actions, gateway);
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcel = new Parcel();
        parcel.setId("p1");
        parcel.setTrackingNumber("TRK1");
        parcel.setCourierId("c1");
        parcel.setCustomerContact("+15551234567");
        parcel.setStatus(ParcelStatus.OUT_FOR_DELIVERY);
        when(parcels.findById("p1")).thenReturn(Optional.of(parcel));
        when(actions.save(any(DriverAction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void maskedCallReturnsProxyNotCustomerNumber() {
        when(gateway.openMaskedCall("p1", "+15551234567")).thenReturn("+15550100");
        var result = service.maskedCall("c1", "p1");
        assertEquals("+15550100", result.proxyNumber());
    }

    @Test
    void sendsProximityTextOnce() {
        service.proximityText("c1", "p1");
        verify(gateway).sendText(anyString(), anyString());

        DriverAction recent = new DriverAction();
        recent.setCreatedAt(Instant.now());
        when(actions.findFirstByParcelIdAndTypeOrderByCreatedAtDesc("p1", DriverActionType.PROXIMITY_TEXT))
                .thenReturn(Optional.of(recent));
        assertEquals("Text already sent", service.proximityText("c1", "p1").message());
        verify(gateway).sendText(anyString(), anyString());
    }

    @Test
    void gateCodeReusesLastCodeWhenNoneGiven() {
        DriverAction last = new DriverAction();
        last.setDetail("4821");
        when(actions.findFirstByParcelIdAndTypeOrderByCreatedAtDesc("p1", DriverActionType.GATE_CODE))
                .thenReturn(Optional.of(last));
        service.logGateCode("c1", "p1", null);
        verify(actions).save(any(DriverAction.class));
    }

    @Test
    void gateCodeWithNothingOnFileFails() {
        assertThrows(IllegalStateException.class, () -> service.logGateCode("c1", "p1", " "));
        verify(actions, never()).save(any());
    }

    @Test
    void rejectsOtherCouriersParcel() {
        assertThrows(NoSuchElementException.class, () -> service.maskedCall("c2", "p1"));
    }

    @Test
    void rejectsTextBeforeNearDelivery() {
        parcel.setStatus(ParcelStatus.ACCEPTED);
        assertThrows(IllegalStateException.class, () -> service.proximityText("c1", "p1"));
    }
}
