package com.parceltracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.parceltracker.model.Pickup;
import com.parceltracker.model.PickupRequest;
import com.parceltracker.service.ApiException;
import com.parceltracker.service.ApiService;
import com.parceltracker.service.PickupFormValidator;
import com.parceltracker.service.PickupFormValidator.Form;
import java.time.LocalDate;
import java.util.Map;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class PickupController {
    @FXML private TextField contactNameField;
    @FXML private TextField contactPhoneField;
    @FXML private TextField streetField;
    @FXML private TextField cityField;
    @FXML private TextField postalCodeField;
    @FXML private TextField parcelDescriptionField;
    @FXML private TextField weightKgField;
    @FXML private TextField dimensionsField;
    @FXML private DatePicker pickupDateField;
    @FXML private ComboBox<String> timeWindowField;
    @FXML private TextArea notesField;
    @FXML private Label contactNameError;
    @FXML private Label contactPhoneError;
    @FXML private Label streetError;
    @FXML private Label cityError;
    @FXML private Label postalCodeError;
    @FXML private Label parcelDescriptionError;
    @FXML private Label weightKgError;
    @FXML private Label dimensionsError;
    @FXML private Label pickupDateError;
    @FXML private Label timeWindowError;
    @FXML private Label notesError;
    @FXML private Button submitButton;
    @FXML private Button resetButton;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator progress;
    @FXML private Label statusLabel;

    private ApiService api;
    private Runnable onClose = () -> { };
    private boolean touched;
    private boolean submitting;

    public void init(ApiService api, Runnable onClose) {
        this.api = api;
        this.onClose = onClose;
    }

    @FXML
    private void initialize() {
        timeWindowField.getItems().setAll("08:00-10:00", "10:00-12:00", "12:00-14:00", "14:00-16:00", "16:00-18:00");
        Runnable revalidate = this::refresh;
        for (TextField f : new TextField[]{contactNameField, contactPhoneField, streetField, cityField,
                postalCodeField, parcelDescriptionField, weightKgField}) {
            f.textProperty().addListener((o, a, b) -> { touched = true; revalidate.run(); });
        }
        pickupDateField.valueProperty().addListener((o, a, b) -> { touched = true; revalidate.run(); });
        timeWindowField.valueProperty().addListener((o, a, b) -> { touched = true; revalidate.run(); });
        resetButton.setOnAction(e -> clear());
        cancelButton.setOnAction(e -> onClose.run());
        submitButton.setOnAction(e -> submit());
        refresh();
    }

    private Map<String, String> errors() {
        return PickupFormValidator.validate(new Form(contactNameField.getText(), contactPhoneField.getText(),
                streetField.getText(), cityField.getText(), postalCodeField.getText(),
                parcelDescriptionField.getText(), weightKgField.getText(), pickupDateField.getValue(),
                timeWindowField.getValue()), LocalDate.now());
    }

    private void refresh() {
        Map<String, String> errors = errors();
        showError(contactNameError, touched ? errors.get("contactName") : null);
        showError(contactPhoneError, touched ? errors.get("contactPhone") : null);
        showError(streetError, touched ? errors.get("street") : null);
        showError(cityError, touched ? errors.get("city") : null);
        showError(postalCodeError, touched ? errors.get("postalCode") : null);
        showError(parcelDescriptionError, touched ? errors.get("parcelDescription") : null);
        showError(weightKgError, touched ? errors.get("weightKg") : null);
        showError(pickupDateError, touched ? errors.get("pickupDate") : null);
        showError(timeWindowError, touched ? errors.get("timeWindow") : null);
        submitButton.setDisable(!errors.isEmpty() || submitting);
    }

    private static void showError(Label label, String message) {
        label.setText(message == null ? "" : message);
        label.setVisible(message != null);
        label.setManaged(message != null);
    }

    private void clear() {
        contactNameField.clear();
        contactPhoneField.clear();
        streetField.clear();
        cityField.clear();
        postalCodeField.clear();
        parcelDescriptionField.clear();
        weightKgField.clear();
        dimensionsField.clear();
        pickupDateField.setValue(null);
        timeWindowField.setValue(null);
        notesField.clear();
        statusLabel.setText("");
        touched = false;
        refresh();
    }

    private void submit() {
        PickupRequest r = new PickupRequest();
        r.setContactName(contactNameField.getText().trim());
        r.setContactPhone(contactPhoneField.getText().trim());
        r.setStreet(streetField.getText().trim());
        r.setCity(cityField.getText().trim());
        r.setPostalCode(postalCodeField.getText().trim());
        r.setParcelDescription(parcelDescriptionField.getText().trim());
        r.setWeightKg(PickupFormValidator.parseWeight(weightKgField.getText()).value());
        r.setDimensions(emptyToNull(dimensionsField.getText()));
        r.setPickupDate(pickupDateField.getValue());
        r.setTimeWindow(timeWindowField.getValue());
        r.setNotes(emptyToNull(notesField.getText()));

        Task<Pickup> task = new Task<>() {
            @Override
            protected Pickup call() throws Exception {
                return api.createPickup(r);
            }
        };
        task.setOnSucceeded(e -> {
            setBusy(false);
            Pickup p = task.getValue();
            clear();
            statusLabel.setText("Pickup " + p.getId() + " created (status: " + p.getStatus() + ")");
            new Alert(Alert.AlertType.INFORMATION,
                    "Pickup requested.\nID: " + p.getId() + "\nStatus: " + p.getStatus()).showAndWait();
        });
        task.setOnFailed(e -> {
            setBusy(false);
            statusLabel.setText(describe(task.getException()));
        });
        setBusy(true);
        Thread t = new Thread(task, "pickup-submit");
        t.setDaemon(true);
        t.start();
    }

    private void setBusy(boolean busy) {
        submitting = busy;
        progress.setVisible(busy);
        progress.setManaged(busy);
        statusLabel.setText(busy ? "Submitting..." : "");
        refresh();
    }

    static String describe(Throwable ex) {
        if (ex instanceof ApiException api) {
            try {
                JsonNode body = ApiService.MAPPER.readTree(api.getBody());
                StringBuilder sb = new StringBuilder(body.path("error").asText("Request failed"));
                body.path("errors").fields().forEachRemaining(en ->
                        sb.append("\n- ").append(en.getKey()).append(": ").append(en.getValue().asText()));
                return sb.toString();
            } catch (Exception ignored) {
                return "Server error (HTTP " + api.getStatusCode() + ")";
            }
        }
        return "Cannot reach the server: " + (ex == null ? "unknown error" : ex.getMessage());
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
