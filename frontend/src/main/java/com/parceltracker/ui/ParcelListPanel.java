package com.parceltracker.ui;

import com.parceltracker.model.Parcel;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ParcelListPanel extends VBox {
    private final ObservableList<Parcel> items = FXCollections.observableArrayList();
    private final FilteredList<Parcel> filtered = new FilteredList<>(items, p -> true);
    private final TableView<Parcel> table = new TableView<>(filtered);
    private final TextField search = new TextField();

    public ParcelListPanel(Consumer<Parcel> onSelect) {
        search.setPromptText("Search tracking no., sender, recipient, status...");
        search.textProperty().addListener((o, a, text) -> filtered.setPredicate(p -> matches(p, text)));

        table.getColumns().add(column("Tracking #", 120, Parcel::getTrackingNumber));
        table.getColumns().add(column("Sender", 100, Parcel::getSender));
        table.getColumns().add(column("Recipient", 100, Parcel::getRecipient));
        table.getColumns().add(column("Status", 100, Parcel::getStatus));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> {
            if (p != null) onSelect.accept(p);
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        setSpacing(6);
        getChildren().addAll(search, table);
    }

    private static TableColumn<Parcel, String> column(String title, double width,
                                                       java.util.function.Function<Parcel, String> getter) {
        TableColumn<Parcel, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(getter.apply(d.getValue())));
        return c;
    }

    static boolean matches(Parcel p, String text) {
        if (text == null || text.isBlank()) return true;
        String q = text.toLowerCase(Locale.ROOT).trim();
        for (String s : new String[]{p.getTrackingNumber(), p.getSender(), p.getRecipient(), p.getStatus(), p.getDestination()}) {
            if (s != null && s.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    public void setParcels(List<Parcel> parcels) {
        Parcel selected = table.getSelectionModel().getSelectedItem();
        items.setAll(parcels);
        if (selected != null) {
            items.stream().filter(p -> p.getId().equals(selected.getId())).findFirst()
                    .ifPresent(table.getSelectionModel()::select);
        }
    }

    public void refresh() {
        table.refresh();
    }
}
