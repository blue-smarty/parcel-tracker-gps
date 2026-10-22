package com.parceltracker.controller;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.ApiService;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class CourierController {
    @FXML private TextField courierIdField;
    @FXML private TableView<Parcel> deliveriesTable;
    @FXML private Label detailsLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> statusField;
    @FXML private TextField latitudeField;
    @FXML private TextField longitudeField;
    @FXML private Button refreshButton;
    @FXML private Button statusButton;
    @FXML private Button locationButton;

    private ApiService api;
    private Parcel selected;

    public void init(ApiService api) {
        this.api = api;
        addColumn("Tracking #", 150, Parcel::getTrackingNumber);
        addColumn("Recipient", 150, Parcel::getRecipient);
        addColumn("Destination", 180, Parcel::getDestination);
        addColumn("Status", 150, Parcel::getStatus);
        VBox.setVgrow(deliveriesTable, Priority.ALWAYS);
        deliveriesTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, parcel) -> select(parcel));
        statusButton.setOnAction(event -> updateStatus());
        locationButton.setOnAction(event -> publishLocation());
        setActionsEnabled(false);
    }

    private void addColumn(String title, double width, java.util.function.Function<Parcel, String> value) {
        TableColumn<Parcel, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));
        deliveriesTable.getColumns().add(column);
    }

    @FXML
    private void loadDeliveries() {
        reload();
    }

    private void reload() {
        String courierId = courierIdField.getText() == null ? "" : courierIdField.getText().trim();
        if (courierId.isEmpty()) {
            statusLabel.setText("Enter your courier ID to load assigned deliveries.");
            return;
        }
        run("courier-deliveries", () -> api.getCourierDeliveries(courierId), deliveries -> {
            deliveriesTable.setItems(FXCollections.observableArrayList(deliveries));
            statusLabel.setText("Loaded " + deliveries.size() + " active deliveries.");
            if (deliveries.isEmpty()) select(null);
        });
    }

    private void select(Parcel parcel) {
        selected = parcel;
        if (parcel == null) {
            detailsLabel.setText("Select an assigned delivery.");
            statusField.getItems().clear();
            setActionsEnabled(false);
            return;
        }
        detailsLabel.setText("Tracking: " + parcel.getTrackingNumber()
                + "\nRecipient: " + parcel.getRecipient()
                + "\nDestination: " + parcel.getDestination()
                + "\nStatus: " + parcel.getStatus());
        List<String> next = switch (parcel.getStatus() == null ? "" : parcel.getStatus()) {
            case "CREATED" -> List.of("PICKED_UP", "FAILED");
            case "PICKED_UP" -> List.of("IN_TRANSIT", "FAILED");
            case "IN_TRANSIT" -> List.of("OUT_FOR_DELIVERY", "FAILED");
            case "OUT_FOR_DELIVERY" -> List.of("DELIVERED", "FAILED");
            default -> List.of();
        };
        statusField.setItems(FXCollections.observableArrayList(next));
        statusField.getSelectionModel().selectFirst();
        setActionsEnabled(true);
        statusButton.setDisable(next.isEmpty());
    }

    private void updateStatus() {
        if (selected == null || statusField.getValue() == null) return;
        String courierId = courierIdField.getText().trim();
        String parcelId = selected.getId();
        String status = statusField.getValue();
        run("courier-status", () -> api.updateCourierDeliveryStatus(courierId, parcelId, status), parcel -> {
            statusLabel.setText("Delivery status updated to " + parcel.getStatus() + ".");
            reload();
        });
    }

    private void publishLocation() {
        if (selected == null) return;
        try {
            double latitude = Double.parseDouble(latitudeField.getText().trim());
            double longitude = Double.parseDouble(longitudeField.getText().trim());
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("Latitude must be -90 to 90 and longitude -180 to 180.");
            }
            Location location = new Location(latitude, longitude, null);
            String courierId = courierIdField.getText().trim();
            String parcelId = selected.getId();
            run("courier-location", () -> api.updateCourierDeliveryLocation(courierId, parcelId, location),
                    parcel -> statusLabel.setText("Location published for " + parcel.getTrackingNumber() + "."));
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter valid numeric latitude and longitude.");
        } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
        }
    }

    private <T> void run(String name, CheckedSupplier<T> action, java.util.function.Consumer<T> onSuccess) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.get();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> statusLabel.setText(PickupController.describe(task.getException())));
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void setActionsEnabled(boolean enabled) {
        statusField.setDisable(!enabled);
        statusButton.setDisable(!enabled);
        latitudeField.setDisable(!enabled);
        longitudeField.setDisable(!enabled);
        locationButton.setDisable(!enabled);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
