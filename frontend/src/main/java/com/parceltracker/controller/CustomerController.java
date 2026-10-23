package com.parceltracker.controller;

import com.parceltracker.model.Parcel;
import com.parceltracker.service.ApiService;
import com.parceltracker.service.SupportTicketStore;
import com.parceltracker.ui.MapPanel;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;

public class CustomerController {
    private static final Preferences PROFILE = Preferences.userNodeForPackage(CustomerController.class);
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    @FXML private TextField customerNameField;
    @FXML private TextField customerContactField;
    @FXML private Button saveProfileButton;
    @FXML private Button socialSignInButton;
    @FXML private Label profileStatusLabel;
    @FXML private TextField recipientField;
    @FXML private TextField pickupAddressField;
    @FXML private TextField deliveryAddressField;
    @FXML private TextField destinationLatitudeField;
    @FXML private TextField destinationLongitudeField;
    @FXML private TextField weightField;
    @FXML private ComboBox<String> packageSizeField;
    @FXML private TextField distanceField;
    @FXML private ComboBox<String> deliverySpeedField;
    @FXML private DatePicker scheduledDateField;
    @FXML private ComboBox<String> scheduledWindowField;
    @FXML private ComboBox<String> paymentMethodField;
    @FXML private Button estimateButton;
    @FXML private Label priceLabel;
    @FXML private Button bookButton;
    @FXML private Label bookingStatusLabel;
    @FXML private Label notificationLabel;
    @FXML private TableView<Parcel> ordersTable;
    @FXML private Label trackingDetailsLabel;
    @FXML private HBox customerMapContainer;
    @FXML private Button receiptButton;
    @FXML private Button ratingButton;
    @FXML private Button supportButton;
    @FXML private Button refreshOrdersButton;
    @FXML private Label trackingStatusLabel;

    private final MapPanel map = new MapPanel();
    private final Map<String, String> previousStatuses = new HashMap<>();
    private final SupportTicketStore supportTickets = new SupportTicketStore();
    private ApiService api;
    private Parcel selected;

    public void init(ApiService api) {
        this.api = api;
        customerNameField.setText(PROFILE.get("name", ""));
        customerContactField.setText(PROFILE.get("contact", ""));
        packageSizeField.getItems().setAll("Small", "Medium", "Large");
        deliverySpeedField.getItems().setAll("Scheduled", "Same-day");
        scheduledWindowField.getItems().setAll("08:00-10:00", "10:00-12:00", "12:00-14:00",
                "14:00-16:00", "16:00-18:00");
        paymentMethodField.getItems().setAll("Demo card", "Demo mobile wallet", "Cash on delivery");
        addColumn("Tracking #", 150, Parcel::getTrackingNumber);
        addColumn("Recipient", 145, Parcel::getRecipient);
        addColumn("Status", 125, Parcel::getStatus);
        ordersTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, parcel) -> selectOrder(parcel));
        saveProfileButton.setOnAction(event -> saveProfile());
        socialSignInButton.setOnAction(event -> useDemoSocialProfile());
        estimateButton.setOnAction(event -> updateEstimate());
        bookButton.setOnAction(event -> bookOrder());
        refreshOrdersButton.setOnAction(event -> refreshOrders());
        receiptButton.setOnAction(event -> showReceipt());
        ratingButton.setOnAction(event -> rateDelivery());
        supportButton.setOnAction(event -> openSupportChat());
        deliverySpeedField.valueProperty().addListener((observable, previous, value) ->
                scheduledDateField.setDisable(!"Scheduled".equals(value)));
        scheduledDateField.setDisable(true);
        customerMapContainer.getChildren().setAll(map);
        map.prefWidthProperty().bind(customerMapContainer.widthProperty());
        distanceField.textProperty().addListener((observable, previous, value) -> updateEstimate());
        weightField.textProperty().addListener((observable, previous, value) -> updateEstimate());
        deliverySpeedField.valueProperty().addListener((observable, previous, value) -> updateEstimate());
        refreshOrders();
    }

    private void addColumn(String title, double width, java.util.function.Function<Parcel, String> value) {
        TableColumn<Parcel, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));
        ordersTable.getColumns().add(column);
    }

    private void saveProfile() {
        String name = trim(customerNameField.getText());
        String contact = trim(customerContactField.getText());
        if (name.isEmpty() || contact.isEmpty()) {
            profileStatusLabel.setText("Enter your name and an email address or phone number.");
            return;
        }
        PROFILE.put("name", name);
        PROFILE.put("contact", contact);
        profileStatusLabel.setText("Demo profile saved on this device; this is not a verified account.");
        refreshOrders();
    }

    private void useDemoSocialProfile() {
        customerNameField.setText("Demo Customer");
        customerContactField.setText("demo-customer@local.test");
        saveProfile();
        profileStatusLabel.setText("Demo social sign-in simulated. No social provider was contacted.");
    }

    private void updateEstimate() {
        try {
            double distance = Double.parseDouble(trim(distanceField.getText()).replace(',', '.'));
            double weight = Double.parseDouble(trim(weightField.getText()).replace(',', '.'));
            String speed = deliverySpeedField.getValue();
            if (!Double.isFinite(distance) || distance <= 0 || !Double.isFinite(weight) || weight <= 0
                    || speed == null) {
                throw new NumberFormatException();
            }
            double multiplier = "Same-day".equals(speed) ? 1.75 : 1.0;
            double estimate = Math.round((5.0 + distance * 1.2 + weight * 0.5) * multiplier * 100.0) / 100.0;
            priceLabel.setText("Demo estimate: " + CURRENCY.format(estimate)
                    + " (based on entered distance, weight and speed; not a quote).");
        } catch (NumberFormatException ignored) {
            priceLabel.setText("Enter a positive route distance and weight, then select a delivery speed for a demo estimate.");
        }
    }

    private void bookOrder() {
        String name = trim(customerNameField.getText());
        String contact = trim(customerContactField.getText());
        String recipient = trim(recipientField.getText());
        String pickup = trim(pickupAddressField.getText());
        String delivery = trim(deliveryAddressField.getText());
        String destinationLatitudeText = trim(destinationLatitudeField.getText());
        String destinationLongitudeText = trim(destinationLongitudeField.getText());
        String size = packageSizeField.getValue();
        String speed = deliverySpeedField.getValue();
        String payment = paymentMethodField.getValue();
        double weight;
        double distance;
        try {
            weight = Double.parseDouble(trim(weightField.getText()).replace(',', '.'));
            distance = Double.parseDouble(trim(distanceField.getText()).replace(',', '.'));
        } catch (NumberFormatException exception) {
            bookingStatusLabel.setText("Enter a valid positive weight and route distance.");
            return;
        }
        if (name.isEmpty() || contact.isEmpty()) {
            bookingStatusLabel.setText("Save a demo profile before placing an order.");
            return;
        }
        if (recipient.isEmpty() || pickup.isEmpty() || delivery.isEmpty() || size == null
                || speed == null || payment == null || !Double.isFinite(weight) || weight <= 0
                || !Double.isFinite(distance) || distance <= 0) {
            bookingStatusLabel.setText("Complete all delivery details with positive weight and distance.");
            return;
        }
        if ("Scheduled".equals(speed)
                && (scheduledDateField.getValue() == null || scheduledDateField.getValue().isBefore(LocalDate.now())
                || scheduledWindowField.getValue() == null)) {
            bookingStatusLabel.setText("Choose a non-past date and time window for scheduled delivery.");
            return;
        }
        Double destinationLatitude;
        Double destinationLongitude;
        if (destinationLatitudeText.isEmpty() && destinationLongitudeText.isEmpty()) {
            destinationLatitude = null;
            destinationLongitude = null;
        } else {
            try {
                destinationLatitude = Double.parseDouble(destinationLatitudeText.replace(',', '.'));
                destinationLongitude = Double.parseDouble(destinationLongitudeText.replace(',', '.'));
                if (!Double.isFinite(destinationLatitude) || destinationLatitude < -90 || destinationLatitude > 90
                        || !Double.isFinite(destinationLongitude) || destinationLongitude < -180
                        || destinationLongitude > 180) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException exception) {
                bookingStatusLabel.setText("Enter both valid destination coordinates (latitude -90 to 90, longitude -180 to 180), or leave both blank.");
                return;
            }
        }

        Parcel parcel = new Parcel();
        parcel.setSender(name);
        parcel.setRecipient(recipient);
        parcel.setCustomerContact(contact);
        parcel.setPickupAddress(pickup);
        parcel.setDestination(delivery);
        parcel.setDestinationLatitude(destinationLatitude);
        parcel.setDestinationLongitude(destinationLongitude);
        parcel.setPackageSize(size.toUpperCase(Locale.ROOT));
        parcel.setWeightKg(weight);
        parcel.setDeliverySpeed("Same-day".equals(speed) ? "SAME_DAY" : "SCHEDULED");
        parcel.setDistanceKm(distance);
        parcel.setPaymentMethod(payment.toUpperCase(Locale.ROOT).replace(' ', '_'));
        parcel.setScheduledDate("Scheduled".equals(speed) ? scheduledDateField.getValue() : null);
        parcel.setScheduledWindow("Scheduled".equals(speed) ? scheduledWindowField.getValue() : null);
        bookButton.setDisable(true);
        bookingStatusLabel.setText("Creating demo order...");
        run("customer-order", () -> api.createParcel(parcel), created -> {
            bookButton.setDisable(false);
            bookingStatusLabel.setText("Order " + created.getTrackingNumber() + " created. "
                    + "Payment is simulated (" + payment + "); no charge was made.");
            refreshOrders();
            new Alert(Alert.AlertType.INFORMATION, "Demo order created\nTracking: "
                    + created.getTrackingNumber() + "\nStatus: " + created.getStatus()
                    + "\nEstimated price: " + CURRENCY.format(created.getEstimatedPrice() == null
                    ? 0.0 : created.getEstimatedPrice())
                    + "\nPayment: " + payment + " (simulation only)").showAndWait();
        }, error -> {
            bookButton.setDisable(false);
            bookingStatusLabel.setText("Could not create order: " + PickupController.describe(error));
        });
    }

    private void refreshOrders() {
        if (api == null) return;
        run("customer-orders", api::getParcels, this::onTrackingRefresh, error ->
                trackingStatusLabel.setText("Cannot load demo orders: " + PickupController.describe(error)));
    }

    public void onTrackingRefresh(List<Parcel> parcels) {
        String contact = trim(customerContactField.getText());
        List<Parcel> own = new ArrayList<>();
        for (Parcel parcel : parcels) {
            if (contact.isEmpty() || !contact.equalsIgnoreCase(trim(parcel.getCustomerContact()))) continue;
            own.add(parcel);
            String old = previousStatuses.put(parcel.getId(), parcel.getStatus());
            if (old != null && !old.equals(parcel.getStatus())) {
                notificationLabel.setText("Demo notification: " + parcel.getTrackingNumber()
                        + " changed from " + old + " to " + parcel.getStatus() + ".");
            }
        }
        ordersTable.setItems(FXCollections.observableArrayList(own));
        map.setParcels(own);
        if (selected != null) {
            Parcel updated = own.stream().filter(parcel -> selected.getId().equals(parcel.getId())).findFirst().orElse(null);
            if (updated == null) selectOrder(null);
            else selectOrder(updated);
        }
        trackingStatusLabel.setText("Showing " + own.size()
                + " order(s) for this local demo profile. Live events require the tracking feed to be connected.");
    }

    public void onLiveLocation(String parcelId, com.parceltracker.model.Location location) {
        map.updateLocation(parcelId, location);
    }

    private void selectOrder(Parcel parcel) {
        selected = parcel;
        if (parcel == null) {
            trackingDetailsLabel.setText("Select one of your demo customer orders.");
            receiptButton.setDisable(true);
            ratingButton.setDisable(true);
            return;
        }
        trackingDetailsLabel.setText("Tracking: " + parcel.getTrackingNumber()
                + "\nStatus: " + parcel.getStatus()
                + "\nPickup: " + valueOrUnknown(parcel.getPickupAddress())
                + "\nDelivery: " + valueOrUnknown(parcel.getDestination())
                + "\nCurrent location: " + (parcel.getCurrentLocation() == null ? "Awaiting courier GPS"
                : parcel.getCurrentLocation().getLatitude() + ", " + parcel.getCurrentLocation().getLongitude()));
        map.setSelected(parcel.getId(), parcel.getLocationHistory());
        receiptButton.setDisable(false);
        ratingButton.setDisable(!"DELIVERED".equals(parcel.getStatus()));
    }

    private void showReceipt() {
        if (selected == null) return;
        String receipt = "Digital receipt — DEMO ONLY\n"
                + "Tracking: " + selected.getTrackingNumber() + "\n"
                + "From: " + valueOrUnknown(selected.getPickupAddress()) + "\n"
                + "To: " + valueOrUnknown(selected.getDestination()) + "\n"
                + "Estimated total: " + CURRENCY.format(selected.getEstimatedPrice() == null
                ? 0.0 : selected.getEstimatedPrice()) + "\n"
                + "Payment: " + valueOrUnknown(selected.getPaymentMethod())
                + "\nDelivery proof: " + ("DELIVERED".equals(selected.getStatus())
                ? "status confirmation only (demo; no photo or signature stored)" : "not delivered")
                + "\nThis receipt is not proof of payment.";
        new Alert(Alert.AlertType.INFORMATION, receipt).showAndWait();
    }

    private void rateDelivery() {
        if (selected == null || !"DELIVERED".equals(selected.getStatus())) return;
        javafx.scene.control.ChoiceDialog<Integer> dialog =
                new javafx.scene.control.ChoiceDialog<>(5, List.of(1, 2, 3, 4, 5));
        dialog.setTitle("Demo delivery rating");
        dialog.setHeaderText("Rate delivery " + selected.getTrackingNumber());
        dialog.setContentText("Stars (local demo only):");
        dialog.showAndWait().ifPresent(rating ->
                trackingStatusLabel.setText("Thanks for the " + rating + "-star demo rating. It was not submitted."));
    }

    private void openSupportChat() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Demo support request");
        dialog.setHeaderText("Local demo support");
        dialog.setContentText("Enter a message for the local support queue:");
        dialog.showAndWait().filter(message -> !message.isBlank()).ifPresent(message -> {
            try {
                supportTickets.create(selected == null ? null : selected.getTrackingNumber(), message);
                trackingStatusLabel.setText("Support request added to the local demo queue.");
            } catch (RuntimeException e) {
                trackingStatusLabel.setText("Could not save support request: " + e.getMessage());
            }
        });
    }

    private <T> void run(String threadName, CheckedSupplier<T> action,
                         java.util.function.Consumer<T> success, java.util.function.Consumer<Throwable> failure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.get();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> failure.accept(task.getException()));
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
