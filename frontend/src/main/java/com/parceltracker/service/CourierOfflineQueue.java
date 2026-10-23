package com.parceltracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.parceltracker.model.DeliveryProof;
import com.parceltracker.model.Location;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class CourierOfflineQueue {
    private final Path file;

    public CourierOfflineQueue() {
        this(Path.of(System.getProperty("user.home"), ".parcel-tracker", "courier-updates.json"));
    }

    public CourierOfflineQueue(Path file) {
        this.file = file;
    }

    public synchronized void enqueue(CourierUpdate update) throws IOException {
        List<CourierUpdate> updates = read();
        updates.add(update);
        write(updates);
    }

    public synchronized int size() throws IOException {
        return read().size();
    }

    public synchronized int flush(ApiService api) throws IOException, InterruptedException {
        List<CourierUpdate> updates = read();
        int synced = 0;
        while (!updates.isEmpty()) {
            apply(api, updates.get(0));
            updates.remove(0);
            write(updates);
            synced++;
        }
        return synced;
    }

    private List<CourierUpdate> read() throws IOException {
        if (!Files.exists(file)) return new ArrayList<>();
        return ApiService.MAPPER.readValue(Files.readString(file), new TypeReference<List<CourierUpdate>>() { });
    }

    private void write(List<CourierUpdate> updates) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "courier-updates-", ".tmp");
        try {
            Files.writeString(temporary, ApiService.MAPPER.writeValueAsString(updates));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void apply(ApiService api, CourierUpdate update) throws IOException, InterruptedException {
        switch (update.getType()) {
            case DECISION -> api.decideCourierDelivery(update.getCourierId(), update.getParcelId(),
                    Boolean.TRUE.equals(update.getAccepted()));
            case STATUS -> api.updateCourierDeliveryStatus(update.getCourierId(), update.getParcelId(),
                    update.getStatus());
            case LOCATION -> api.updateCourierDeliveryLocation(update.getCourierId(), update.getParcelId(),
                    update.getLocation());
            case PROOF -> api.completeCourierDelivery(update.getCourierId(), update.getParcelId(), update.getProof());
        }
    }

    public static class CourierUpdate {
        public enum Type { DECISION, STATUS, LOCATION, PROOF }

        private Type type;
        private String courierId;
        private String parcelId;
        private String status;
        private Boolean accepted;
        private Location location;
        private DeliveryProof proof;

        public CourierUpdate() {
        }

        public CourierUpdate(Type type, String courierId, String parcelId) {
            this.type = type;
            this.courierId = courierId;
            this.parcelId = parcelId;
        }

        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }
        public String getCourierId() { return courierId; }
        public void setCourierId(String courierId) { this.courierId = courierId; }
        public String getParcelId() { return parcelId; }
        public void setParcelId(String parcelId) { this.parcelId = parcelId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Boolean getAccepted() { return accepted; }
        public void setAccepted(Boolean accepted) { this.accepted = accepted; }
        public Location getLocation() { return location; }
        public void setLocation(Location location) { this.location = location; }
        public DeliveryProof getProof() { return proof; }
        public void setProof(DeliveryProof proof) { this.proof = proof; }
    }
}
