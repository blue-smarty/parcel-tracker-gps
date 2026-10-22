package com.parceltracker.controller;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.ApiService;
import com.parceltracker.service.WebSocketService;
import com.parceltracker.ui.MapPanel;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.application.Platform;

/** Owns the map and the live WebSocket feed that moves its markers. */
public class MapController {
    private final MapPanel map = new MapPanel();
    private final ApiService api;
    private final WebSocketService ws;

    public MapController(ApiService api, String wsBaseUrl, BiConsumer<String, Location> onLiveUpdate,
                         Consumer<Boolean> onConnectionChange) {
        this.api = api;
        this.ws = new WebSocketService(wsBaseUrl, "all",
                (id, loc) -> Platform.runLater(() -> {
                    if (loc != null) map.updateLocation(id, loc);
                    onLiveUpdate.accept(id, loc);
                }),
                connected -> Platform.runLater(() -> onConnectionChange.accept(connected)));
    }

    public MapPanel getView() {
        return map;
    }

    public void start() {
        ws.connect();
    }

    public void stop() {
        ws.disconnect();
    }

    public void showParcels(List<Parcel> parcels) {
        map.setParcels(parcels);
    }

    public void select(Parcel parcel) {
        map.setSelected(parcel.getId(), parcel.getLocationHistory());
    }
}
