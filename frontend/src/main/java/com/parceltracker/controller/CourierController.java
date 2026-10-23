package com.parceltracker.controller;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import com.parceltracker.model.DeliveryProof;
import com.parceltracker.service.ApiException;
import com.parceltracker.service.ApiService;
import com.parceltracker.service.CourierOfflineQueue;
import com.parceltracker.service.CourierOfflineQueue.CourierUpdate;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.stage.FileChooser;
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
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;
    @FXML private Button routeButton;
    @FXML private Button earningsButton;
    @FXML private Button proofButton;
    @FXML private Button photoButton;
    @FXML private Button clearSignatureButton;
    @FXML private Canvas signatureCanvas;
    @FXML private TextField barcodeField;
    @FXML private TextField dropOffNoteField;
    @FXML private TextField tipAmountField;
    @FXML private TextField payoutAmountField;
    @FXML private Label photoLabel;
    @FXML private Label earningsLabel;

    private ApiService api;
    private Parcel selected;
    private final CourierOfflineQueue offlineQueue = new CourierOfflineQueue();
    private String photoBase64;
    private final StringBuilder signature = new StringBuilder();
    private boolean drawingSignature;

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
        acceptButton.setOnAction(event -> decideRequest(true));
        rejectButton.setOnAction(event -> decideRequest(false));
        proofButton.setOnAction(event -> submitProof());
        routeButton.setOnAction(event -> openOptimizedRoute());
        earningsButton.setOnAction(event -> refreshEarnings());
        photoButton.setOnAction(event -> choosePhoto());
        clearSignatureButton.setOnAction(event -> clearSignature());
        signatureCanvas.setOnMousePressed(event -> beginSignature(event.getX(), event.getY()));
        signatureCanvas.setOnMouseDragged(event -> continueSignature(event.getX(), event.getY()));
        clearSignature();
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
        run("courier-deliveries", () -> {
            int synced = offlineQueue.flush(api);
            return new DeliveryLoad(api.getCourierDeliveries(courierId), synced);
        }, result -> {
            deliveriesTable.setItems(FXCollections.observableArrayList(result.deliveries()));
            statusLabel.setText("Loaded " + result.deliveries().size() + " active deliveries."
                    + (result.synced() == 0 ? "" : " Synced " + result.synced() + " offline update(s)."));
            if (result.deliveries().isEmpty()) select(null);
            refreshEarnings();
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
            case "CREATED" -> List.of();
            case "ACCEPTED" -> List.of("PICKED_UP", "FAILED");
            case "PICKED_UP" -> List.of("IN_TRANSIT", "FAILED");
            case "IN_TRANSIT" -> List.of("OUT_FOR_DELIVERY", "FAILED");
            case "OUT_FOR_DELIVERY" -> List.of("FAILED");
            default -> List.of();
        };
        statusField.setItems(FXCollections.observableArrayList(next));
        statusField.getSelectionModel().selectFirst();
        setActionsEnabled(true);
        statusButton.setDisable(next.isEmpty());
        boolean request = "CREATED".equals(parcel.getStatus());
        acceptButton.setDisable(!request);
        rejectButton.setDisable(!request);
        proofButton.setDisable(!"OUT_FOR_DELIVERY".equals(parcel.getStatus()));
    }

    private void updateStatus() {
        if (selected == null || statusField.getValue() == null) return;
        String courierId = courierIdField.getText().trim();
        String parcelId = selected.getId();
        String status = statusField.getValue();
        CourierUpdate update = new CourierUpdate(CourierUpdate.Type.STATUS, courierId, parcelId);
        update.setStatus(status);
        runQueued("courier-status", () -> api.updateCourierDeliveryStatus(courierId, parcelId, status), update,
                parcel -> {
            statusLabel.setText("Delivery status updated to " + parcel.getStatus() + ".");
            reload();
        });
    }

    private void decideRequest(boolean accepted) {
        if (selected == null) return;
        String courierId = courierIdField.getText().trim();
        String parcelId = selected.getId();
        CourierUpdate update = new CourierUpdate(CourierUpdate.Type.DECISION, courierId, parcelId);
        update.setAccepted(accepted);
        runQueued("courier-decision", () -> api.decideCourierDelivery(courierId, parcelId, accepted), update,
                parcel -> {
                    statusLabel.setText(accepted ? "Delivery request accepted." : "Delivery request rejected.");
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
            CourierUpdate update = new CourierUpdate(CourierUpdate.Type.LOCATION, courierId, parcelId);
            update.setLocation(location);
            runQueued("courier-location", () -> api.updateCourierDeliveryLocation(courierId, parcelId, location),
                    update, parcel -> statusLabel.setText("Location published for "
                            + parcel.getTrackingNumber() + "."));
        } catch (NumberFormatException e) {
            statusLabel.setText("Enter valid numeric latitude and longitude.");
        } catch (IllegalArgumentException e) {
            statusLabel.setText(e.getMessage());
        }
    }

    private void submitProof() {
        if (selected == null) return;
        try {
            DeliveryProof proof = new DeliveryProof();
            proof.setRecipientSignature(signature.toString());
            proof.setBarcode(trim(barcodeField.getText()));
            proof.setDropOffNote(trim(dropOffNoteField.getText()));
            proof.setPhotoBase64(photoBase64);
            proof.setTipAmount(parseAmount(tipAmountField.getText()));
            proof.setPayoutAmount(parseAmount(payoutAmountField.getText()));
            if (proof.getRecipientSignature().isBlank() && proof.getBarcode().isBlank()
                    && (proof.getPhotoBase64() == null || proof.getPhotoBase64().isBlank())) {
                statusLabel.setText("Draw a recipient signature or add a barcode or drop-off photo.");
                return;
            }
            String courierId = courierIdField.getText().trim();
            String parcelId = selected.getId();
            CourierUpdate update = new CourierUpdate(CourierUpdate.Type.PROOF, courierId, parcelId);
            update.setProof(proof);
            runQueued("courier-proof", () -> api.completeCourierDelivery(courierId, parcelId, proof), update,
                    parcel -> {
                        statusLabel.setText("Proof saved; delivery marked complete.");
                        clearSignature();
                        barcodeField.clear();
                        dropOffNoteField.clear();
                        tipAmountField.clear();
                        payoutAmountField.clear();
                        photoBase64 = null;
                        photoLabel.setText("No photo selected");
                        reload();
                    });
        } catch (NumberFormatException exception) {
            statusLabel.setText("Enter non-negative numeric tip and payout amounts.");
        }
    }

    private void choosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose proof-of-delivery photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        java.io.File selectedFile = chooser.showOpenDialog(signatureCanvas.getScene().getWindow());
        if (selectedFile == null) return;
        try {
            long maxBytes = 4L * 1024 * 1024;
            if (Files.size(selectedFile.toPath()) > maxBytes) {
                statusLabel.setText("Photo must be 4 MB or smaller.");
                return;
            }
            photoBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(selectedFile.toPath()));
            photoLabel.setText(selectedFile.getName());
            statusLabel.setText("Drop-off photo ready to submit.");
        } catch (IOException exception) {
            statusLabel.setText("Could not read photo: " + exception.getMessage());
        }
    }

    private void beginSignature(double x, double y) {
        if (signature.length() > 19_000) return;
        drawingSignature = true;
        GraphicsContext graphics = signatureCanvas.getGraphicsContext2D();
        graphics.beginPath();
        graphics.moveTo(x, y);
        graphics.stroke();
        appendSignaturePoint('M', x, y);
    }

    private void continueSignature(double x, double y) {
        if (!drawingSignature || signature.length() > 19_000) return;
        GraphicsContext graphics = signatureCanvas.getGraphicsContext2D();
        graphics.lineTo(x, y);
        graphics.stroke();
        appendSignaturePoint('L', x, y);
    }

    private void appendSignaturePoint(char command, double x, double y) {
        signature.append(command)
                .append(Math.round(x)).append(',')
                .append(Math.round(y)).append(';');
    }

    private void clearSignature() {
        drawingSignature = false;
        signature.setLength(0);
        GraphicsContext graphics = signatureCanvas.getGraphicsContext2D();
        graphics.setFill(javafx.scene.paint.Color.WHITE);
        graphics.fillRect(0, 0, signatureCanvas.getWidth(), signatureCanvas.getHeight());
        graphics.setStroke(javafx.scene.paint.Color.GRAY);
        graphics.strokeRect(0, 0, signatureCanvas.getWidth(), signatureCanvas.getHeight());
        graphics.setStroke(javafx.scene.paint.Color.BLACK);
        graphics.setLineWidth(2.0);
    }

    private void refreshEarnings() {
        String courierId = courierIdField.getText() == null ? "" : courierIdField.getText().trim();
        if (courierId.isEmpty()) {
            earningsLabel.setText("Enter your courier ID to load earnings.");
            return;
        }
        run("courier-earnings", () -> api.getCourierEarnings(courierId), earnings -> earningsLabel.setText(
                String.format(Locale.US, "Completed deliveries: %d | tips: $%.2f | payouts: $%.2f",
                        earnings.getCompletedDeliveries(), earnings.getTips(), earnings.getPayouts())));
    }

    private void openOptimizedRoute() {
        List<Parcel> remaining = new ArrayList<>();
        for (Parcel parcel : deliveriesTable.getItems()) {
            if (parcel.getDestinationLatitude() != null && parcel.getDestinationLongitude() != null) {
                remaining.add(parcel);
            }
        }
        Location start = selected == null ? null : selected.getCurrentLocation();
        if (start == null || start.getLatitude() == null || start.getLongitude() == null) {
            start = deliveriesTable.getItems().stream().map(Parcel::getCurrentLocation)
                    .filter(location -> location != null && location.getLatitude() != null
                            && location.getLongitude() != null)
                    .findFirst().orElse(null);
        }
        if (remaining.isEmpty()) {
            statusLabel.setText("No active delivery has destination coordinates for route planning.");
            return;
        }
        if (start == null) {
            statusLabel.setText("A current GPS location is needed to optimize the delivery route.");
            return;
        }

        List<Parcel> ordered = new ArrayList<>();
        double latitude = start.getLatitude();
        double longitude = start.getLongitude();
        while (!remaining.isEmpty()) {
            Parcel nearest = remaining.get(0);
            double shortestDistance = routeDistance(latitude, longitude, nearest);
            for (Parcel candidate : remaining) {
                double distance = routeDistance(latitude, longitude, candidate);
                if (distance < shortestDistance) {
                    nearest = candidate;
                    shortestDistance = distance;
                }
            }
            ordered.add(nearest);
            remaining.remove(nearest);
            latitude = nearest.getDestinationLatitude();
            longitude = nearest.getDestinationLongitude();
        }

        Parcel destination = ordered.get(ordered.size() - 1);
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1&origin=")
                .append(encode(start.getLatitude() + "," + start.getLongitude()))
                .append("&destination=")
                .append(encode(destination.getDestinationLatitude() + "," + destination.getDestinationLongitude()));
        if (ordered.size() > 1) {
            List<String> waypoints = ordered.subList(0, ordered.size() - 1).stream()
                    .map(parcel -> parcel.getDestinationLatitude() + "," + parcel.getDestinationLongitude())
                    .toList();
            url.append("&waypoints=").append(encode(String.join("|", waypoints)));
        }
        url.append("&travelmode=driving");
        try {
            if (!Desktop.isDesktopSupported()) {
                statusLabel.setText("A browser is not available to open turn-by-turn directions.");
                return;
            }
            Desktop.getDesktop().browse(URI.create(url.toString()));
            statusLabel.setText("Opened directions with " + ordered.size()
                    + " stops in nearest-neighbor order. Navigation runs in your maps app.");
        } catch (Exception exception) {
            statusLabel.setText("Could not open route: " + exception.getMessage());
        }
    }

    private static double routeDistance(double latitude, double longitude, Parcel destination) {
        double lat2 = Math.toRadians(destination.getDestinationLatitude());
        double lon2 = Math.toRadians(destination.getDestinationLongitude());
        double lat1 = Math.toRadians(latitude);
        double lon1 = Math.toRadians(longitude);
        double deltaLat = lat2 - lat1;
        double deltaLon = lon2 - lon1;
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6_371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static double parseAmount(String value) {
        String trimmed = trim(value);
        if (trimmed.isEmpty()) return 0.0;
        double amount = Double.parseDouble(trimmed.replace(',', '.'));
        if (!Double.isFinite(amount) || amount < 0) throw new NumberFormatException();
        return amount;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private <T> void run(String name, CheckedSupplier<T> action, java.util.function.Consumer<T> onSuccess) {
        run(name, action, onSuccess, error -> statusLabel.setText(PickupController.describe(error)));
    }

    private <T> void run(String name, CheckedSupplier<T> action, java.util.function.Consumer<T> onSuccess,
                         java.util.function.Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.get();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private <T> void runQueued(String name, CheckedSupplier<T> action, CourierUpdate update,
                               java.util.function.Consumer<T> onSuccess) {
        run(name, action, onSuccess, error -> {
            if (!isRetryable(error)) {
                statusLabel.setText(PickupController.describe(error));
                return;
            }
            try {
                offlineQueue.enqueue(update);
                statusLabel.setText("Connection unavailable. Update saved for retry when you load deliveries.");
            } catch (IOException queueError) {
                statusLabel.setText("Could not save offline update: " + queueError.getMessage());
            }
        });
    }

    private static boolean isRetryable(Throwable error) {
        return !(error instanceof ApiException apiError) || apiError.getStatusCode() >= 500;
    }

    private record DeliveryLoad(List<Parcel> deliveries, int synced) {
    }

    private void setActionsEnabled(boolean enabled) {
        statusField.setDisable(!enabled);
        statusButton.setDisable(!enabled);
        latitudeField.setDisable(!enabled);
        longitudeField.setDisable(!enabled);
        locationButton.setDisable(!enabled);
        acceptButton.setDisable(!enabled);
        rejectButton.setDisable(!enabled);
        proofButton.setDisable(!enabled);
        barcodeField.setDisable(!enabled);
        dropOffNoteField.setDisable(!enabled);
        tipAmountField.setDisable(!enabled);
        payoutAmountField.setDisable(!enabled);
        photoButton.setDisable(!enabled);
        clearSignatureButton.setDisable(!enabled);
        signatureCanvas.setDisable(!enabled);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
