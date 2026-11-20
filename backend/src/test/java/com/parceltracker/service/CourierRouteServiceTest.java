package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CourierRouteServiceTest {
    private final CourierService couriers = mock(CourierService.class);
    private final RestTemplate provider = mock(RestTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final CourierRouteService routes = new CourierRouteService(couriers, provider, "key");

    private Parcel parcel(String id, ParcelStatus status) {
        Parcel parcel = new Parcel();
        parcel.setId(id);
        parcel.setTrackingNumber("TRK-" + id);
        parcel.setPickupAddress("Pickup " + id);
        parcel.setDestination("Delivery " + id);
        parcel.setStatus(status);
        return parcel;
    }

    private void providerResults(BiFunction<Integer, Integer, Map<String, Integer>> cost) {
        AtomicInteger index = new AtomicInteger();
        when(provider.getForObject(any(URI.class), eq(JsonNode.class))).thenAnswer(invocation ->
                mapper.readTree("{\"results\":[{\"position\":{\"lat\":"
                        + (-37.8 + index.getAndIncrement() * 0.001) + ",\"lon\":144.9}}]}"));
        when(provider.postForObject(any(URI.class), any(), eq(JsonNode.class))).thenAnswer(invocation -> {
            JsonNode request = mapper.valueToTree(invocation.getArgument(1));
            int origins = request.path("origins").size(), destinations = request.path("destinations").size();
            assertTrue(origins * destinations <= 100);
            assertEquals("live", request.path("options").path("traffic").asText());
            List<Map<String, Object>> cells = new ArrayList<>();
            for (int i = 0; i < origins; i++) {
                for (int j = 0; j < destinations; j++) {
                    cells.add(Map.of("originIndex", i, "destinationIndex", j,
                            "routeSummary", cost.apply(i, j)));
                }
            }
            return mapper.valueToTree(Map.of("data", cells));
        });
    }

    @Test
    void prioritizesAvoidingLateDeliveryOverShorterDistance() {
        Parcel urgent = parcel("urgent", ParcelStatus.PICKED_UP);
        urgent.setTargetDeadline(Instant.now().plusSeconds(900));
        Parcel other = parcel("other", ParcelStatus.IN_TRANSIT);
        when(couriers.getActiveDeliveries("c1")).thenReturn(List.of(urgent, other));
        providerResults((i, j) -> Map.of("lengthInMeters", i == 0 && j == 0 ? 2000 : 100,
                "travelTimeInSeconds", 300));
        var result = routes.calculate("c1", -37.9, 144.8);
        assertEquals(List.of("urgent", "other"), result.stops().stream().map(CourierRouteService.Stop::parcelId).toList());
        assertEquals(0, result.estimatedLateDeliveries());
        assertFalse(result.stops().get(0).late());
    }

    @Test
    void plansPickupBeforeDeliveryAndMeasuresPickupBasedDeadlineFromCompletion() {
        Parcel parcel = parcel("1", ParcelStatus.ACCEPTED);
        parcel.setTargetStart("PICKUP");
        parcel.setTargetMinutes(6);
        when(couriers.getActiveDeliveries("c1")).thenReturn(List.of(parcel));
        providerResults((i, j) -> Map.of("lengthInMeters", 100, "travelTimeInSeconds", 60));
        var result = routes.calculate("c1", -37.9, 144.8);
        assertEquals(List.of("PICKUP", "DELIVERY"), result.stops().stream().map(CourierRouteService.Stop::type).toList());
        assertEquals(Duration.ofMinutes(6), Duration.between(result.stops().get(0).estimatedArrival(),
                result.stops().get(1).estimatedDeadline()));
        assertEquals(0, result.estimatedLateDeliveries());
        assertFalse(result.stops().get(1).late());
    }

    @Test
    void reportsUnavoidableLateDelivery() {
        Parcel parcel = parcel("1", ParcelStatus.PICKED_UP);
        parcel.setTargetDeadline(Instant.now().minusSeconds(1));
        when(couriers.getActiveDeliveries("c1")).thenReturn(List.of(parcel));
        providerResults((i, j) -> Map.of("lengthInMeters", 100, "travelTimeInSeconds", 60));
        var result = routes.calculate("c1", -37.9, 144.8);
        assertEquals(1, result.estimatedLateDeliveries());
        assertEquals(Boolean.TRUE, result.stops().get(0).late());
    }

    @Test
    void rejectsTooManyDeliveriesWithoutProviderCalls() {
        when(couriers.getActiveDeliveries("c1")).thenReturn(IntStream.range(0, 10)
                .mapToObj(i -> parcel(Integer.toString(i), ParcelStatus.ACCEPTED)).toList());
        assertEquals(422, assertThrows(ResponseStatusException.class,
                () -> routes.calculate("c1", -37.9, 144.8)).getStatusCode().value());
        verifyNoInteractions(provider);
    }

    @Test
    void rejectsIncompleteMatrix() {
        when(couriers.getActiveDeliveries("c1")).thenReturn(List.of(parcel("1", ParcelStatus.PICKED_UP)));
        when(provider.getForObject(any(URI.class), eq(JsonNode.class)))
                .thenReturn(mapper.readTree("{\"results\":[{\"position\":{\"lat\":-37.8,\"lon\":144.9}}]}"));
        when(provider.postForObject(any(URI.class), any(), eq(JsonNode.class)))
                .thenReturn(mapper.readTree("{\"data\":[]}"));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> routes.calculate("c1", -37.9, 144.8)).getStatusCode().value());
    }

    @Test
    void supportsNineDeliveriesAndBatchesTheMatrix() {
        when(couriers.getActiveDeliveries("c1")).thenReturn(IntStream.range(0, 9)
                .mapToObj(i -> parcel(Integer.toString(i), ParcelStatus.ACCEPTED)).toList());
        providerResults((i, j) -> Map.of("lengthInMeters", 100, "travelTimeInSeconds", 60));
        var result = routes.calculate("c1", -37.9, 144.8);
        assertEquals(18, result.stops().size());
        for (int i = 0; i < 9; i++) {
            String id = Integer.toString(i);
            var stops = result.stops().stream().filter(stop -> stop.parcelId().equals(id)).toList();
            assertEquals(List.of("PICKUP", "DELIVERY"), stops.stream().map(CourierRouteService.Stop::type).toList());
        }
        verify(provider, times(4)).postForObject(any(URI.class), any(), eq(JsonNode.class));
    }
}
