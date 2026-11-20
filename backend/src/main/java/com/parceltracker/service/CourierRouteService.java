package com.parceltracker.service;

import com.parceltracker.model.Parcel;
import com.parceltracker.model.ParcelStatus;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Service
public class CourierRouteService {
    private static final int MAX_DELIVERIES = 9;
    private static final int STOP_SECONDS = 300;
    private static final int BEAM_WIDTH = 2000;
    private final CourierService couriers;
    private final RestTemplate provider;
    private final String apiKey;

    public CourierRouteService(CourierService couriers, RestTemplate addressLookupRestTemplate,
                               @Value("${parcel.tomtom.api-key:}") String apiKey) {
        this.couriers = couriers;
        this.provider = addressLookupRestTemplate;
        this.apiKey = apiKey.trim();
    }

    private record Point(double latitude, double longitude) {}
    private record Job(Parcel parcel, boolean needsPickup, int pickupIndex, int deliveryIndex) {}
    private record Step(int job, boolean pickup, String address) {}
    private record Cost(double meters, double seconds) {}
    private record State(int mask, int last, double seconds, double meters, int late,
                         double[] pickupTimes, List<Integer> path) {}
    private record RankedState(State state, int projectedLate) {}
    public record Stop(String parcelId, String trackingNumber, String type, String address,
                       Instant targetDeadline, String targetStart, Integer targetMinutes,
                       Instant estimatedArrival, Instant estimatedDeadline, Boolean late) {}
    public record RouteResult(double distanceMeters, int estimatedLateDeliveries, List<Stop> stops) {}

    public RouteResult calculate(String courierId, double latitude, double longitude) {
        List<Parcel> parcels = couriers.getActiveDeliveries(courierId);
        if (parcels.isEmpty()) return new RouteResult(0, 0, List.of());
        if (parcels.size() > MAX_DELIVERIES) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Route optimization supports up to 9 active deliveries.");
        }
        if (apiKey.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Route lookup is not configured.");
        }
        List<Job> jobs = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (Parcel parcel : parcels) {
            boolean pickup = parcel.getStatus() == ParcelStatus.CREATED || parcel.getStatus() == ParcelStatus.ACCEPTED;
            int pickupIndex = -1;
            if (pickup) {
                pickupIndex = steps.size();
                steps.add(new Step(jobs.size(), true, requireAddress(parcel.getPickupAddress())));
            }
            int deliveryIndex = steps.size();
            steps.add(new Step(jobs.size(), false, requireAddress(parcel.getDestination())));
            jobs.add(new Job(parcel, pickup, pickupIndex, deliveryIndex));
        }
        try {
            Map<String, Point> geocoded = new HashMap<>();
            List<Point> points = new ArrayList<>();
            points.add(new Point(latitude, longitude));
            for (Step step : steps) points.add(geocoded.computeIfAbsent(step.address(), this::geocode));
            Cost[][] costs = matrix(points);
            Instant start = Instant.now();
            State best = optimize(jobs, steps, costs, start);
            return result(best, jobs, steps, costs, start);
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Route lookup is temporarily unavailable.");
        }
    }

    private State optimize(List<Job> jobs, List<Step> steps, Cost[][] costs, Instant start) {
        List<State> beam = List.of(new State(0, 0, 0, 0, 0, new double[jobs.size()], List.of()));
        for (int count = 0; count < steps.size(); count++) {
            List<RankedState> candidates = new ArrayList<>();
            for (State state : beam) {
                for (int i = 0; i < steps.size(); i++) {
                    if ((state.mask() & (1 << i)) != 0) continue;
                    Step step = steps.get(i);
                    Job job = jobs.get(step.job());
                    if (!step.pickup() && job.needsPickup()
                            && (state.mask() & (1 << job.pickupIndex())) == 0) continue;
                    Cost leg = costs[state.last()][i];
                    double completion = state.seconds() + leg.seconds() + STOP_SECONDS;
                    double[] pickupTimes = state.pickupTimes().clone();
                    if (step.pickup()) pickupTimes[step.job()] = completion;
                    Instant deadline = deadline(job, start, pickupTimes[step.job()]);
                    int late = state.late() + (!step.pickup() && deadline != null
                            && start.plusMillis(Math.round(completion * 1000)).isAfter(deadline) ? 1 : 0);
                    List<Integer> path = new ArrayList<>(state.path());
                    path.add(i);
                    State candidate = new State(state.mask() | (1 << i), i + 1, completion,
                            state.meters() + leg.meters(), late, pickupTimes, path);
                    candidates.add(new RankedState(candidate, projectedLate(candidate, jobs, costs, start)));
                }
            }
            candidates.sort(Comparator.comparingInt(RankedState::projectedLate)
                    .thenComparingInt(s -> s.state().late()).thenComparingDouble(s -> s.state().meters())
                    .thenComparingDouble(s -> s.state().seconds()));
            beam = candidates.subList(0, Math.min(BEAM_WIDTH, candidates.size())).stream()
                    .map(RankedState::state).toList();
        }
        return beam.stream().min(Comparator.comparingInt(State::late).thenComparingDouble(State::meters)
                .thenComparingDouble(State::seconds)).orElseThrow();
    }

    private int projectedLate(State state, List<Job> jobs, Cost[][] costs, Instant start) {
        int late = state.late();
        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if ((state.mask() & (1 << job.deliveryIndex())) != 0) continue;
            double pickupTime = state.pickupTimes()[i];
            double arrival;
            if (job.needsPickup() && (state.mask() & (1 << job.pickupIndex())) == 0) {
                pickupTime = state.seconds() + costs[state.last()][job.pickupIndex()].seconds() + STOP_SECONDS;
                arrival = pickupTime + costs[job.pickupIndex() + 1][job.deliveryIndex()].seconds() + STOP_SECONDS;
            } else {
                arrival = state.seconds() + costs[state.last()][job.deliveryIndex()].seconds() + STOP_SECONDS;
            }
            Instant target = deadline(job, start, pickupTime);
            if (target != null && start.plusMillis(Math.round(arrival * 1000)).isAfter(target)) late++;
        }
        return late;
    }

    private static Instant deadline(Job job, Instant start, double pickupSeconds) {
        Parcel parcel = job.parcel();
        if (parcel.getTargetDeadline() != null) return parcel.getTargetDeadline();
        if (job.needsPickup() && "PICKUP".equals(parcel.getTargetStart()) && parcel.getTargetMinutes() != null) {
            return start.plusMillis(Math.round(pickupSeconds * 1000)).plusSeconds(parcel.getTargetMinutes() * 60L);
        }
        return null;
    }

    private RouteResult result(State state, List<Job> jobs, List<Step> steps, Cost[][] costs, Instant start) {
        List<Stop> result = new ArrayList<>();
        double seconds = 0;
        int last = 0;
        double[] pickupTimes = new double[jobs.size()];
        for (int index : state.path()) {
            Step step = steps.get(index);
            Job job = jobs.get(step.job());
            Parcel parcel = job.parcel();
            seconds += costs[last][index].seconds() + STOP_SECONDS;
            if (step.pickup()) pickupTimes[step.job()] = seconds;
            Instant arrival = start.plusMillis(Math.round(seconds * 1000));
            Instant target = step.pickup() ? null : deadline(job, start, pickupTimes[step.job()]);
            result.add(new Stop(parcel.getId(), parcel.getTrackingNumber(), step.pickup() ? "PICKUP" : "DELIVERY",
                    step.address(), parcel.getTargetDeadline(), parcel.getTargetStart(), parcel.getTargetMinutes(),
                    arrival, target, target == null ? null : arrival.isAfter(target)));
            last = index + 1;
        }
        return new RouteResult(state.meters(), state.late(), List.copyOf(result));
    }

    private Cost[][] matrix(List<Point> points) {
        int destinations = points.size() - 1;
        Cost[][] costs = new Cost[points.size()][destinations];
        List<Map<String, Point>> targets = points.subList(1, points.size()).stream()
                .map(point -> Map.of("point", point)).toList();
        int batchSize = Math.max(1, 100 / destinations);
        URI uri = UriComponentsBuilder.fromUriString("https://api.tomtom.com")
                .path("/routing/matrix/2").queryParam("key", "{key}").buildAndExpand(apiKey).encode().toUri();
        for (int start = 0; start < points.size(); start += batchSize) {
            int end = Math.min(points.size(), start + batchSize);
            List<Map<String, Point>> origins = points.subList(start, end).stream()
                    .map(point -> Map.of("point", point)).toList();
            Map<String, Object> request = Map.of("origins", origins, "destinations", targets,
                    "options", Map.of("travelMode", "car", "departAt", "now", "traffic", "live"));
            JsonNode response = provider.postForObject(uri, request, JsonNode.class);
            JsonNode data = response == null ? null : response.path("data");
            if (data == null || !data.isArray()) throw invalidResponse();
            for (JsonNode cell : data) {
                JsonNode origin = cell.path("originIndex"), destination = cell.path("destinationIndex");
                if (!origin.isIntegralNumber() || !destination.isIntegralNumber()) throw invalidResponse();
                int i = origin.asInt(), j = destination.asInt();
                if (i < 0 || i >= end - start || j < 0 || j >= destinations || costs[start + i][j] != null) {
                    throw invalidResponse();
                }
                if (points.get(start + i).equals(points.get(j + 1))) {
                    costs[start + i][j] = new Cost(0, 0);
                    continue;
                }
                JsonNode summary = cell.path("routeSummary");
                JsonNode distance = summary.path("lengthInMeters"), time = summary.path("travelTimeInSeconds");
                if (!distance.isNumber() || !time.isNumber() || !Double.isFinite(distance.asDouble())
                        || !Double.isFinite(time.asDouble()) || distance.asDouble() < 0 || time.asDouble() < 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "A route between stops could not be calculated.");
                }
                costs[start + i][j] = new Cost(distance.asDouble(), time.asDouble());
            }
            for (int i = start; i < end; i++) {
                for (int j = 0; j < destinations; j++) if (costs[i][j] == null) throw invalidResponse();
            }
        }
        return costs;
    }

    private Point geocode(String address) {
        URI uri = UriComponentsBuilder.fromUriString("https://api.tomtom.com")
                .pathSegment("search", "2", "geocode", "{address}.json")
                .queryParam("key", "{key}").queryParam("limit", 1)
                .buildAndExpand(address, apiKey).encode().toUri();
        JsonNode response = provider.getForObject(uri, JsonNode.class);
        JsonNode results = response == null ? null : response.path("results");
        if (results == null || !results.isArray()) throw invalidResponse();
        if (results.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "A stop address could not be found.");
        JsonNode position = results.path(0).path("position");
        JsonNode lat = position.path("lat"), lon = position.path("lon");
        if (!lat.isNumber() || !lon.isNumber() || !Double.isFinite(lat.asDouble())
                || !Double.isFinite(lon.asDouble()) || Math.abs(lat.asDouble()) > 90
                || Math.abs(lon.asDouble()) > 180) throw invalidResponse();
        return new Point(lat.asDouble(), lon.asDouble());
    }

    private static String requireAddress(String value) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "An active delivery is missing a pickup or delivery address.");
        return value.trim();
    }

    private static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Route provider returned an invalid response.");
    }
}
