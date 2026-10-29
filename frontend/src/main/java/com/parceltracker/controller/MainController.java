package com.parceltracker.controller;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.Branding;
import com.parceltracker.service.ApiService;
import com.parceltracker.service.BrandingTheme;
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
    @FXML private Button courierButton;
    @FXML private Button customerButton;
    @FXML private Button adminButton;
    private Stage pickupStage;
    private Stage courierStage;
    private Stage customerStage;
    private Stage adminStage;
    private boolean adminLoginInProgress;
    private CustomerController customerController;
    private AdminController adminController;

    private ApiService api;
    private MapController mapController;
    private ParcelListPanel listPanel;
    private final LocationHistoryPanel historyPanel = new LocationHistoryPanel();
    private final List<Parcel> parcels = new ArrayList<>();
    private Parcel selected;

    private Branding branding = Branding.defaults();

    public ApiService getApi() {
        return api;
    }

    public void init(String httpBaseUrl, String wsBaseUrl) {
        api = new ApiService(httpBaseUrl);
        branding = BrandingTheme.load(api);
        listPanel = new ParcelListPanel(this::select);
        mapController = new MapController(api, wsBaseUrl, this::onLiveUpdate, this::onConnection);
        listContainer.getChildren().add(listPanel);
        mapContainer.getChildren().add(mapController.getView());
        historyContainer.getChildren().add(historyPanel);
        refreshButton.setOnAction(e -> reload());
        pickupButton.setOnAction(e -> openPickupForm());
        courierButton.setOnAction(e -> openCourierDashboard());
        customerButton.setOnAction(e -> openCustomerApp());
        adminButton.setOnAction(e -> openAdminPanel(wsBaseUrl));
        reload();
        mapController.start();
    }

    public void shutdown() {
        if (mapController != null) mapController.stop();
        if (adminController != null) adminController.shutdown();
        api.clearAdminCredentials();
    }

    private void openAdminPanel(String wsBaseUrl) {
        if (adminStage != null && adminStage.isShowing()) {
            adminStage.toFront();
            return;
        }
        if (adminLoginInProgress) return;
        AdminLoginDialog.showAndWait(adminButton.getScene().getWindow()).ifPresent(credentials -> {
            adminLoginInProgress = true;
            adminButton.setDisable(true);
            runAsync(() -> {
                try {
                    api.authenticateAdmin(credentials.username(), credentials.password());
                    Platform.runLater(() -> {
                        adminLoginInProgress = false;
                        adminButton.setDisable(false);
                        showAdminPanel(wsBaseUrl);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        adminLoginInProgress = false;
                        adminButton.setDisable(false);
                        detailsLabel.setText("Admin sign-in failed: " + e.getMessage());
                    });
                }
            });
        });
    }

    private void showAdminPanel(String wsBaseUrl) {
        adminController = new AdminController();
        adminController.init(api, wsBaseUrl);
        adminStage = new Stage();
        adminStage.setTitle("Admin & Dispatcher Command Center — Demo");
        adminStage.setScene(new Scene(adminController, 1320, 900));
        adminStage.setOnHidden(event -> {
            adminController.shutdown();
            adminController = null;
            adminStage = null;
            api.clearAdminCredentials();
        });
        BrandingTheme.apply(adminStage, branding);
        adminStage.show();
    }

    private void openCustomerApp() {
        if (customerStage != null && customerStage.isShowing()) {
            customerStage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/customer.fxml"));
            Parent root = loader.load();
            customerController = loader.getController();
            customerController.init(api);
            customerStage = new Stage();
            customerStage.setTitle("Customer App — Demo");
            customerStage.setScene(new Scene(root, 900, 760));
            customerStage.setOnHidden(e -> customerController = null);
            BrandingTheme.apply(customerStage, branding);
            customerStage.show();
        } catch (java.io.IOException e) {
            detailsLabel.setText("Cannot open customer app: " + e.getMessage());
        }
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
            BrandingTheme.apply(pickupStage, branding);
            pickupStage.show();
        } catch (java.io.IOException e) {
            detailsLabel.setText("Cannot open pickup form: " + e.getMessage());
        }
    }

    private void openCourierDashboard() {
        if (courierStage != null && courierStage.isShowing()) {
            courierStage.toFront();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/courier.fxml"));
            Parent root = loader.load();
            courierStage = new Stage();
            CourierController controller = loader.getController();
            controller.init(api);
            courierStage.setTitle("Courier Dashboard");
            courierStage.setScene(new Scene(root, 1000, 850));
            BrandingTheme.apply(courierStage, branding);
            courierStage.show();
        } catch (java.io.IOException e) {
            detailsLabel.setText("Cannot open courier dashboard: " + e.getMessage());
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
                    if (customerController != null) customerController.onTrackingRefresh(list);
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
        if (location == null) {
            reload();
            return;
        }
        if (customerController != null) customerController.onLiveLocation(parcelId, location);
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
