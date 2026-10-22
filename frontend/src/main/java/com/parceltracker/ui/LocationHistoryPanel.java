package com.parceltracker.ui;

import com.parceltracker.model.Location;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

public class LocationHistoryPanel extends VBox {
    private final ObservableList<String> lines = FXCollections.observableArrayList();

    public LocationHistoryPanel() {
        setSpacing(4);
        ListView<String> list = new ListView<>(lines);
        getChildren().addAll(new Label("Location history"), list);
    }

    public void setHistory(List<Location> history) {
        lines.clear();
        history.forEach(this::add);
    }

    public void add(Location l) {
        lines.add(0, String.format("%s  (%.5f, %.5f)", l.getTimestamp(), l.getLatitude(), l.getLongitude()));
    }
}
