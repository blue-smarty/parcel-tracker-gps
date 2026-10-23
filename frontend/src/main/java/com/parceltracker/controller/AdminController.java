package com.parceltracker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.parceltracker.model.Parcel;
import com.parceltracker.service.ApiService;
import com.parceltracker.service.SupportTicketStore;
import com.parceltracker.service.SupportTicketStore.Ticket;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class AdminController extends BorderPane {
    private static final String FLEET_KEY = "fleet";
    private static final Preferences FLEET_STORE = Preferences.userNodeForPackage(AdminController.class);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
    private static final List<String> TERMINAL_STATUSES =
            List.of("DELIVERED", "FAILED", "RETURNED", "CANCELLED");

    private final ObservableList<Parcel> parcels = FXCollections.observableArrayList();
    private final ObservableList<FleetDriver> drivers = FXCollections.observableArrayList();
    private final ObservableList<Ticket> tickets = FXCollections.observableArrayList();
    private final TableView<Parcel> parcelTable = new TableView<>(parcels);
    private final TableView<FleetDriver> fleetTable = new TableView<>(drivers);
    private final TableView<Ticket> ticketTable = new TableView<>(tickets);
    private final StackPane mapContainer = new StackPane();
    private final ComboBox<FleetDriver> courierChoice = new ComboBox<>();
    private final TextField driverNameField = new TextField();
    private final TextField courierIdField = new TextField();
    private final TextField commissionField = new TextField();
    private final Label statusLabel = new Label("Loading operations…");
    private final Label revenueMetric = new Label("—");
    private final Label failedMetric = new Label("—");
    private final Label staleMetric = new Label("—");
    private final Label supportMetric = new Label("—");
    private final SupportTicketStore supportStore = new SupportTicketStore();

    private ApiService api;
    private MapController mapController;

    public AdminController() {
        buildInterface();
    }

    public void init(ApiService api, String wsBaseUrl) {
        this.api = api;
        loadFleet();
        refreshTicketList();
        mapController = new MapController(api, wsBaseUrl, this::onLiveUpdate,
                connected -> statusLabel.setText(connected ? "Live tracking connected" : "Live tracking reconnecting…"));
        mapContainer.getChildren().setAll(mapController.getView());
        mapController.start();
        reloadParcels();
    }

    public void shutdown() {
        if (mapController != null) mapController.stop();
    }

    private void buildInterface() {
        setPadding(new Insets(12));
        setTop(buildHeader());

        VBox operations = buildParcelOperations();
        Node map = buildMapView();
        SplitPane primary = new SplitPane(operations, map);
        primary.setDividerPositions(0.38);
        VBox.setVgrow(primary, Priority.ALWAYS);

        TabPane tabs = new TabPane(buildFleetTab(), buildSupportTab());
        tabs.setPrefHeight(280);
        VBox body = new VBox(10, primary, tabs);
        VBox.setVgrow(body, Priority.ALWAYS);
        setCenter(body);
    }

    private Node buildHeader() {
        Label title = new Label("Admin & Dispatcher Command Center");
        title.setStyle("-fx-font-size: 21px; -fx-font-weight: bold;");
        Label demo = new Label("Demo workspace · fleet, commission, documents and support data are stored locally");
        Button refresh = new Button("Refresh operations");
        refresh.setOnAction(event -> {
            reloadParcels();
            refreshTicketList();
        });
        HBox heading = new HBox(12, title, demo, refresh);
        heading.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(demo, Priority.ALWAYS);

        revenueMetric.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        failedMetric.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        staleMetric.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        supportMetric.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox metrics = new HBox(12,
                metricCard("Estimated delivered value", revenueMetric),
                metricCard("Failed deliveries", failedMetric),
                metricCard("Stale assignment escalations", staleMetric),
                metricCard("Open support requests", supportMetric));
        metrics.setPadding(new Insets(12, 0, 12, 0));
        VBox header = new VBox(6, heading, metrics, statusLabel);
        header.setPadding(new Insets(0, 0, 8, 0));
        return header;
    }

    private VBox metricCard(String title, Label value) {
        Label label = new Label(title);
        VBox card = new VBox(5, label, value);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #eef4fb; -fx-background-radius: 6;");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox buildParcelOperations() {
        Label heading = new Label("Active parcels");
        heading.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        addParcelColumn("Tracking #", 120, Parcel::getTrackingNumber);
        addParcelColumn("Recipient", 110, Parcel::getRecipient);
        addParcelColumn("Status", 110, Parcel::getStatus);
        addParcelColumn("Courier", 100, parcel -> emptyDash(parcel.getCourierId()));
        parcelTable.setPlaceholder(new Label("No active parcels"));
        VBox.setVgrow(parcelTable, Priority.ALWAYS);

        courierChoice.setPromptText("Select verified courier");
        courierChoice.setMaxWidth(Double.MAX_VALUE);
        courierChoice.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(FleetDriver driver, boolean empty) {
                super.updateItem(driver, empty);
                setText(empty || driver == null ? null : driver.name() + " · " + driver.courierId());
            }
        });
        courierChoice.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(FleetDriver driver, boolean empty) {
                super.updateItem(driver, empty);
                setText(empty || driver == null ? null : driver.name() + " · " + driver.courierId());
            }
        });
        Button assign = new Button("Assign selected");
        assign.setOnAction(event -> assignSelected());
        Button autoDispatch = new Button("Auto-dispatch unassigned");
        autoDispatch.setOnAction(event -> autoDispatch());
        VBox controls = new VBox(8, new Label("Manual assignment"), courierChoice, assign, autoDispatch,
                new Label("Select a parcel and an approved courier to update the assignment."));
        controls.setPadding(new Insets(8, 0, 0, 0));
        return new VBox(8, heading, parcelTable, controls);
    }

    private Node buildMapView() {
        Label label = new Label("Live map · active parcel locations (assigned parcels represent courier locations)");
        label.setPadding(new Insets(0, 0, 6, 0));
        VBox mapBox = new VBox(label);
        mapBox.setStyle("-fx-background-color: #f8fafc;");
        mapBox.setMinWidth(350);
        mapContainer.setMinSize(350, 250);
        VBox.setVgrow(mapContainer, Priority.ALWAYS);
        mapBox.setPadding(new Insets(8));
        mapBox.getChildren().add(mapContainer);
        return mapBox;
    }

    private Tab buildFleetTab() {
        addFleetColumn("Courier ID", "courierId", 150);
        addFleetColumn("Name", "name", 160);
        addFleetColumn("Commission %", "commissionPercent", 120);
        addFleetColumn("Documents", "documentStatus", 130);
        addFleetColumn("Attachments", "documentCount", 100);
        fleetTable.setPlaceholder(new Label("No demo drivers onboarded"));
        fleetTable.getSelectionModel().selectedItemProperty().addListener((observable, previous, driver) -> {
            if (driver == null) return;
            driverNameField.setText(driver.name());
            courierIdField.setText(driver.courierId());
            commissionField.setText(String.valueOf(driver.commissionPercent()));
        });

        driverNameField.setPromptText("Driver name");
        courierIdField.setPromptText("Courier ID");
        commissionField.setPromptText("Commission %");
        Button saveDriver = new Button("Add / update driver");
        saveDriver.setOnAction(event -> saveDriver());
        Button attach = new Button("Attach documents");
        attach.setOnAction(event -> attachDocuments());
        Button review = new Button("Review documents");
        review.setOnAction(event -> reviewDocuments());
        HBox fields = new HBox(8, driverNameField, courierIdField, commissionField, saveDriver, attach, review);
        fields.setAlignment(Pos.CENTER_LEFT);
        Tab tab = new Tab("Fleet management", new VBox(8, fleetTable, fields,
                new Label("Courier IDs and commission rates are local demo settings; commission is not used for real payouts.")));
        tab.setClosable(false);
        return tab;
    }

    private Tab buildSupportTab() {
        addTicketColumn("Created", ticket -> TIME_FORMAT.format(ticket.createdAt()), 130);
        addTicketColumn("Tracking #", ticket -> emptyDash(ticket.trackingNumber()), 130);
        addTicketColumn("Message", Ticket::message, 560);
        addTicketColumn("Status", Ticket::status, 100);
        ticketTable.setPlaceholder(new Label("No local demo support requests"));
        Button refresh = new Button("Refresh requests");
        refresh.setOnAction(event -> refreshTicketList());
        Button resolve = new Button("Resolve selected");
        resolve.setOnAction(event -> resolveSelectedTicket());
        HBox buttons = new HBox(8, refresh, resolve);
        Tab tab = new Tab("Support escalations", new VBox(8, ticketTable, buttons,
                new Label("Requests come from the customer demo and are stored only on this device; no support agent is connected.")));
        tab.setClosable(false);
        return tab;
    }

    private void addParcelColumn(String title, double width, java.util.function.Function<Parcel, String> value) {
        TableColumn<Parcel, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));
        parcelTable.getColumns().add(column);
    }

    private void addFleetColumn(String title, String property, double width) {
        TableColumn<FleetDriver, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        fleetTable.getColumns().add(column);
    }

    private void addTicketColumn(String title, java.util.function.Function<Ticket, String> value, double width) {
        TableColumn<Ticket, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));
        ticketTable.getColumns().add(column);
    }

    private void reloadParcels() {
        runAsync("admin-parcels", api::getParcels, list -> {
            parcels.setAll(list);
            parcelTable.setItems(FXCollections.observableArrayList(list.stream().filter(AdminController::isActive).toList()));
            if (mapController != null) mapController.showParcels(list.stream().filter(AdminController::isActive).toList());
            updateMetrics();
            refreshCourierChoices();
            statusLabel.setText("Loaded " + list.size() + " parcels");
        });
    }

    private void onLiveUpdate(String parcelId, com.parceltracker.model.Location location) {
        if (location == null) {
            reloadParcels();
            return;
        }
        for (Parcel parcel : parcels) {
            if (parcelId.equals(parcel.getId())) {
                parcel.setCurrentLocation(location);
                if (parcel.getStatus() == null || "CREATED".equals(parcel.getStatus())) {
                    parcel.setStatus("IN_TRANSIT");
                }
                parcelTable.refresh();
                updateMetrics();
                return;
            }
        }
        reloadParcels();
    }

    private void assignSelected() {
        Parcel parcel = parcelTable.getSelectionModel().getSelectedItem();
        FleetDriver driver = courierChoice.getValue();
        if (parcel == null || driver == null) {
            statusLabel.setText("Select an active parcel and an approved courier.");
            return;
        }
        runAsync("admin-assignment", () -> api.assignCourier(parcel.getId(), driver.courierId()), updated -> {
            statusLabel.setText("Assigned " + updated.getTrackingNumber() + " to " + driver.name());
            reloadParcels();
        });
    }

    private void autoDispatch() {
        List<Parcel> unassigned = parcels.stream()
                .filter(parcel -> "CREATED".equals(parcel.getStatus()) && isBlank(parcel.getCourierId()))
                .toList();
        List<FleetDriver> eligible = drivers.stream()
                .filter(driver -> "APPROVED".equals(driver.documentStatus()))
                .toList();
        if (unassigned.isEmpty()) {
            statusLabel.setText("No unassigned new parcels to dispatch.");
            return;
        }
        if (eligible.isEmpty()) {
            statusLabel.setText("Onboard a driver and approve their documents before dispatch.");
            return;
        }
        runAsync("admin-auto-dispatch", () -> {
            Map<String, Integer> loads = new HashMap<>();
            for (FleetDriver driver : eligible) loads.put(driver.courierId(), 0);
            for (Parcel parcel : parcels) {
                if (isActive(parcel) && loads.containsKey(parcel.getCourierId())) {
                    loads.compute(parcel.getCourierId(), (id, count) -> count + 1);
                }
            }
            int assigned = 0;
            for (Parcel parcel : unassigned) {
                FleetDriver driver = eligible.stream()
                        .min(Comparator.comparingInt(item -> loads.get(item.courierId())))
                        .orElseThrow();
                api.assignCourier(parcel.getId(), driver.courierId());
                loads.compute(driver.courierId(), (id, count) -> count + 1);
                assigned++;
            }
            return assigned;
        }, assigned -> {
            statusLabel.setText("Auto-dispatched " + assigned + " parcels using least-active-load balancing.");
            reloadParcels();
        });
    }

    private void saveDriver() {
        String name = driverNameField.getText() == null ? "" : driverNameField.getText().trim();
        String id = courierIdField.getText() == null ? "" : courierIdField.getText().trim();
        if (name.isEmpty() || id.isEmpty()) {
            statusLabel.setText("Enter both a driver name and courier ID.");
            return;
        }
        double commission;
        try {
            commission = Double.parseDouble(commissionField.getText().trim());
        } catch (RuntimeException e) {
            statusLabel.setText("Enter a commission percentage from 0 to 100.");
            return;
        }
        if (!Double.isFinite(commission) || commission < 0 || commission > 100) {
            statusLabel.setText("Enter a commission percentage from 0 to 100.");
            return;
        }
        FleetDriver existing = drivers.stream().filter(driver -> driver.courierId().equals(id)).findFirst().orElse(null);
        FleetDriver updated = new FleetDriver(id, name, commission,
                existing == null ? "PENDING" : existing.documentStatus(),
                existing == null ? List.of() : existing.documentPaths());
        if (existing == null) drivers.add(updated);
        else drivers.set(drivers.indexOf(existing), updated);
        saveFleet();
        refreshCourierChoices();
        statusLabel.setText("Saved local demo driver " + name + ".");
    }

    private void attachDocuments() {
        FleetDriver driver = fleetTable.getSelectionModel().getSelectedItem();
        if (driver == null) {
            statusLabel.setText("Select a driver before attaching documents.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Attach driver documents (local demo only)");
        List<File> files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files == null || files.isEmpty()) return;
        List<String> paths = new ArrayList<>(driver.documentPaths());
        files.stream().map(File::getAbsolutePath).forEach(paths::add);
        replaceDriver(driver, new FleetDriver(driver.courierId(), driver.name(), driver.commissionPercent(),
                "SUBMITTED", paths));
        statusLabel.setText("Attached " + files.size() + " local document(s) for review.");
    }

    private void reviewDocuments() {
        FleetDriver driver = fleetTable.getSelectionModel().getSelectedItem();
        if (driver == null || driver.documentPaths().isEmpty()) {
            statusLabel.setText("Select a driver with attached documents to review.");
            return;
        }
        String names = driver.documentPaths().stream()
                .map(path -> new File(path).getName())
                .reduce((first, second) -> first + ", " + second).orElse("");
        ChoiceDialog<String> dialog = new ChoiceDialog<>("APPROVED", List.of("APPROVED", "FOLLOW_UP"));
        dialog.setTitle("Review driver documents");
        dialog.setHeaderText(driver.name() + " · " + driver.courierId());
        dialog.setContentText("Attached files: " + names + "\nChoose review result:");
        dialog.showAndWait().ifPresent(result -> {
            replaceDriver(driver, new FleetDriver(driver.courierId(), driver.name(), driver.commissionPercent(),
                    result, driver.documentPaths()));
            statusLabel.setText(result.equals("APPROVED")
                    ? "Driver documents marked approved." : "Driver documents need follow-up.");
        });
    }

    private void replaceDriver(FleetDriver previous, FleetDriver updated) {
        drivers.set(drivers.indexOf(previous), updated);
        saveFleet();
        refreshCourierChoices();
    }

    private void loadFleet() {
        try {
            List<FleetDriver> saved = ApiService.MAPPER.readValue(FLEET_STORE.get(FLEET_KEY, "[]"),
                    new TypeReference<>() { });
            drivers.setAll(saved);
        } catch (Exception e) {
            statusLabel.setText("Could not load local fleet settings: " + e.getMessage());
        }
        refreshCourierChoices();
    }

    private void saveFleet() {
        try {
            FLEET_STORE.put(FLEET_KEY, ApiService.MAPPER.writeValueAsString(drivers));
        } catch (Exception e) {
            statusLabel.setText("Could not save local fleet settings: " + e.getMessage());
        }
    }

    private void refreshCourierChoices() {
        String selectedId = courierChoice.getValue() == null ? null : courierChoice.getValue().courierId();
        courierChoice.setItems(FXCollections.observableArrayList(drivers.stream()
                .filter(driver -> "APPROVED".equals(driver.documentStatus())).toList()));
        if (selectedId != null) {
            courierChoice.getItems().stream().filter(driver -> driver.courierId().equals(selectedId))
                    .findFirst().ifPresent(courierChoice::setValue);
        }
    }

    private void refreshTicketList() {
        try {
            tickets.setAll(supportStore.findAll());
            updateMetrics();
        } catch (RuntimeException e) {
            statusLabel.setText(e.getMessage());
        }
    }

    private void resolveSelectedTicket() {
        Ticket ticket = ticketTable.getSelectionModel().getSelectedItem();
        if (ticket == null || !"OPEN".equals(ticket.status())) {
            statusLabel.setText("Select an open support request.");
            return;
        }
        try {
            supportStore.resolve(ticket.id());
            refreshTicketList();
            statusLabel.setText("Support request resolved in the local demo queue.");
        } catch (RuntimeException e) {
            statusLabel.setText("Could not resolve support request: " + e.getMessage());
        }
    }

    private void updateMetrics() {
        double deliveredValue = parcels.stream()
                .filter(parcel -> "DELIVERED".equals(parcel.getStatus()))
                .map(Parcel::getEstimatedPrice).filter(value -> value != null)
                .mapToDouble(Double::doubleValue).sum();
        long failed = parcels.stream().filter(parcel -> "FAILED".equals(parcel.getStatus())).count();
        long stale = parcels.stream().filter(parcel -> parcel.getEscalatedAt() != null).count();
        long openSupport = tickets.stream().filter(ticket -> "OPEN".equals(ticket.status())).count();
        revenueMetric.setText(String.format(Locale.US, "$%.2f", deliveredValue));
        failedMetric.setText(String.valueOf(failed));
        staleMetric.setText(String.valueOf(stale));
        supportMetric.setText(String.valueOf(openSupport));
    }

    private static boolean isActive(Parcel parcel) {
        return parcel.getStatus() == null || !TERMINAL_STATUSES.contains(parcel.getStatus());
    }

    private static String emptyDash(String value) {
        return isBlank(value) ? "—" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> void runAsync(String name, CheckedSupplier<T> action,
                              java.util.function.Consumer<T> success) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.get();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> statusLabel.setText("Operation failed: " + task.getException().getMessage()));
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public record FleetDriver(String courierId, String name, double commissionPercent,
                              String documentStatus, List<String> documentPaths) {
        public FleetDriver {
            documentPaths = documentPaths == null ? List.of() : List.copyOf(documentPaths);
        }

        public String getCourierId() { return courierId; }
        public String getName() { return name; }
        public String getCommissionPercent() { return String.format(Locale.US, "%.1f%%", commissionPercent); }
        public String getDocumentStatus() { return documentStatus; }
        public String getDocumentCount() { return String.valueOf(documentPaths.size()); }
    }
}
