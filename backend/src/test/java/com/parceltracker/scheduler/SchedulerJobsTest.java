package com.parceltracker.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import com.parceltracker.repository.ParcelRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerJobsTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private ParcelRepository repository;
    private SchedulerProperties properties;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        repository = mock(ParcelRepository.class);
        properties = new SchedulerProperties();
        properties.getStaleAssigned().setThreshold(Duration.ofHours(2));
        properties.getLocationInactivity().setThreshold(Duration.ofMinutes(15));
        properties.getAutoClose().setGracePeriod(Duration.ofHours(24));
    }

    private Parcel parcel(ParcelStatus status, Instant updatedAt) {
        Parcel p = new Parcel();
        p.setId("p1");
        p.setCourierId("c1");
        p.setStatus(status);
        p.setUpdatedAt(updatedAt);
        return p;
    }

    // stale assigned
    @Test
    void staleNoCandidatesIsNoop() {
        when(repository.findStaleAssigned(any(), any())).thenReturn(List.of());
        assertEquals(0, new StaleAssignedEscalationJob(repository, properties, clock).execute());
        verify(repository, never()).save(any());
    }

    @Test
    void staleEscalatesOnceAndBoundaryIsInclusive() {
        Parcel atBoundary = parcel(ParcelStatus.CREATED, NOW.minus(Duration.ofHours(2)));
        Parcel justYounger = parcel(ParcelStatus.CREATED, NOW.minus(Duration.ofHours(2)).plusSeconds(1));
        Parcel noTimestamps = parcel(ParcelStatus.CREATED, null);
        when(repository.findStaleAssigned(any(), any())).thenReturn(List.of(atBoundary, justYounger, noTimestamps));
        StaleAssignedEscalationJob job = new StaleAssignedEscalationJob(repository, properties, clock);

        assertEquals(1, job.execute());
        assertEquals(NOW, atBoundary.getEscalatedAt());
        assertEquals("c1", atBoundary.getCourierId());
        assertNull(justYounger.getEscalatedAt());
        assertEquals(0, job.execute());
        verify(repository, times(1)).save(any());
    }

    @Test
    void staleCanUnassignAndIsolatesErrors() {
        properties.getStaleAssigned().setUnassign(true);
        Parcel bad = parcel(ParcelStatus.CREATED, NOW.minus(Duration.ofHours(3)));
        Parcel good = parcel(ParcelStatus.CREATED, NOW.minus(Duration.ofHours(3)));
        when(repository.findStaleAssigned(any(), any())).thenReturn(List.of(bad, good));
        when(repository.save(bad)).thenThrow(new RuntimeException("boom"));
        assertEquals(1, new StaleAssignedEscalationJob(repository, properties, clock).execute());
        assertNull(good.getCourierId());
        assertNotNull(good.getEscalatedAt());
    }

    // inactivity
    @Test
    void inactivityNoCandidatesIsNoop() {
        when(repository.findByStatusIn(any())).thenReturn(List.of());
        assertEquals(0, new CourierLocationInactivityJob(repository, properties, clock).execute());
        verify(repository, never()).save(any());
    }

    @Test
    void inactivityFlagsOnceAtBoundaryAndClearsOnRecovery() {
        Parcel p = parcel(ParcelStatus.IN_TRANSIT, NOW.minus(Duration.ofDays(1)));
        p.setCurrentLocation(new Location(1.0, 2.0, NOW.minus(Duration.ofMinutes(15))));
        Parcel fresh = parcel(ParcelStatus.IN_TRANSIT, NOW);
        fresh.setCurrentLocation(new Location(1.0, 2.0, NOW.minus(Duration.ofMinutes(15)).plusSeconds(1)));
        Parcel noTimes = parcel(ParcelStatus.IN_TRANSIT, null);
        when(repository.findByStatusIn(any())).thenReturn(List.of(p, fresh, noTimes));
        CourierLocationInactivityJob job = new CourierLocationInactivityJob(repository, properties, clock);

        assertEquals(1, job.execute());
        assertTrue(p.isLocationInactive());
        assertFalse(fresh.isLocationInactive());
        assertEquals(0, job.execute());
        verify(repository, times(1)).save(any());

        p.setCurrentLocation(new Location(1.0, 2.0, NOW));
        assertEquals(1, job.execute());
        assertFalse(p.isLocationInactive());
    }

    // auto close
    @Test
    void autoCloseNoCandidatesIsNoop() {
        when(repository.findDeliveredBefore(any(), any())).thenReturn(List.of());
        assertEquals(0, new DeliveryAutoCloseJob(repository, properties, clock).execute());
        verify(repository, never()).save(any());
    }

    @Test
    void autoCloseFinalizesOnceAndPreservesUpdatedAt() {
        Instant delivered = NOW.minus(Duration.ofHours(24));
        Parcel atBoundary = parcel(ParcelStatus.DELIVERED, delivered);
        Parcel recent = parcel(ParcelStatus.DELIVERED, delivered.plusSeconds(1));
        Parcel noTimes = parcel(ParcelStatus.DELIVERED, null);
        when(repository.findDeliveredBefore(any(), any())).thenReturn(List.of(atBoundary, recent, noTimes));
        DeliveryAutoCloseJob job = new DeliveryAutoCloseJob(repository, properties, clock);

        assertEquals(1, job.execute());
        assertEquals(NOW, atBoundary.getFinalizedAt());
        assertEquals(delivered, atBoundary.getUpdatedAt());
        assertNull(recent.getFinalizedAt());
        assertEquals(0, job.execute());
        verify(repository, times(1)).save(any());
    }
}
