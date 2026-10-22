package com.parceltracker.controller;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.ApiService;
import com.parceltracker.ui.LocationHistoryPanel;
import com.parceltracker.ui.ParcelListPanel;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainController {
    @FXML private StackPane listContainer;
    @FXML private StackPane mapContainer;
    @FXML private StackPane historyContainer;
    @FXML private Label detailsLabel;
    @FXML private Label connectionLabel;
    @FXML private Button refreshButton;
    @FXML private Button pickupButton;
    private Stage pickupStage;

    private ApiService api;
    private MapController mapController;
    private ParcelListPanel listPanel;
    private final LocationHistoryPanel historyPanel = new LocationHistoryPanel();
    private final List<Parcel> parcels = new ArrayList<>();
    private Parcel selected;

    public void init(String httpBaseUrl, String wsBaseUrl) {
        api = new ApiService(httpBaseUrl);
        listPanel = new ParcelListPanel(this::select);
        mapController = new MapController(api, wsBaseUrl, this::onLiveUpdate, this::onConnection);
        listContainer.getChildren().add(listPanel);
        mapContainer.getChildren().add(mapController.getView());
        historyContainer.getChildren().add(historyPanel);
        refreshButton.setOnAction(e -> reload());
        pickupButton.setOnAction(e -> openPickupForm());
        reload();
        mapController.start();
    }

    public void shutdown() {
        if (mapController != null) mapController.stop();
    }

    private void openPickupForm() {
        if (pickupStage != null && pickupStage.isShowing()) {
            pickupStage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pickup.fxml"));
            Parent root = loader.load();
            pickupStage = new Stage();
            PickupController controller = loader.getController();
            controller.init(api, pickupStage::close);
            pickupStage.setTitle("Schedule Pickup");
            pickupStage.setScene(new Scene(root));
            pickupStage.show();
        } catch (java.io.IOException e) {
            detailsLabel.setText("Cannot open pickup form: " + e.getMessage());
        }
    }

    private void reload() {
        runAsync(() -> {
            try {
                List<Parcel> list = api.getParcels();
                Platform.runLater(() -> {
                    parcels.clear();
                    parcels.addAll(list);
                    listPanel.setParcels(list);
                    mapController.showParcels(list);
                    detailsLabel.setText("Loaded " + list.size() + " parcels");
                });
            } catch (Exception e) {
                Platform.runLater(() -> detailsLabel.setText("Cannot load parcels: " + e.getMessage()));
            }
        });
    }

    private void select(Parcel parcel) {
        selected = parcel;
        mapController.select(parcel);
        historyPanel.setHistory(parcel.getLocationHistory());
        showDetails(parcel);
    }

    private void showDetails(Parcel p) {
        Location l = p.getCurrentLocation();
        detailsLabel.setText(String.format("Tracking: %s%nSender: %s%nRecipient: %s%nDestination: %s%nStatus: %s%nLocation: %s",
                p.getTrackingNumber(), p.getSender(), p.getRecipient(), p.getDestination(), p.getStatus(),
                l == null ? "unknown" : String.format("%.5f, %.5f", l.getLatitude(), l.getLongitude())));
    }

    private void onLiveUpdate(String parcelId, Location location) {
        for (Parcel p : parcels) {
            if (p.getId().equals(parcelId)) {
                p.setCurrentLocation(location);
                p.getLocationHistory().add(location);
                if (p.getStatus() == null || "CREATED".equals(p.getStatus())) p.setStatus("IN_TRANSIT");
                if (selected != null && selected.getId().equals(parcelId)) {
                    historyPanel.add(location);
                    showDetails(p);
                }
                listPanel.refresh();
                return;
            }
        }
        reload();
    }

    private void onConnection(boolean connected) {
        connectionLabel.setText(connected ? "● Live" : "○ Disconnected - retrying...");
    }

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r, "api-call");
        t.setDaemon(true);
        t.start();
    }
}
