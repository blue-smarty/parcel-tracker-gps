package com.parceltracker.ui;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

/** Simple offline map: plots parcel markers and the selected parcel's route on an equirectangular canvas. */
public class MapPanel extends Pane {
    private final Canvas canvas = new Canvas();
    private final Map<String, Parcel> parcels = new LinkedHashMap<>();
    private String selectedId;
    private List<Location> route = new ArrayList<>();

    public MapPanel() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(o -> redraw());
        canvas.heightProperty().addListener(o -> redraw());
        setMinSize(200, 200);
    }

    public void setParcels(List<Parcel> list) {
        parcels.clear();
        list.forEach(p -> parcels.put(p.getId(), p));
        redraw();
    }

    public void setSelected(String id, List<Location> history) {
        selectedId = id;
        route = new ArrayList<>(history);
        redraw();
    }

    public void updateLocation(String parcelId, Location location) {
        Parcel p = parcels.get(parcelId);
        if (p != null) {
            p.setCurrentLocation(location);
        }
        if (parcelId.equals(selectedId)) {
            route.add(location);
        }
        redraw();
    }

    private double x(double lon) {
        return (lon + 180.0) / 360.0 * canvas.getWidth();
    }

    private double y(double lat) {
        return (90.0 - lat) / 180.0 * canvas.getHeight();
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        g.setFill(Color.web("#dbeafe"));
        g.fillRect(0, 0, w, h);
        g.setStroke(Color.web("#93c5fd"));
        g.setLineWidth(1);
        for (int lon = -180; lon <= 180; lon += 30) g.strokeLine(x(lon), 0, x(lon), h);
        for (int lat = -90; lat <= 90; lat += 30) g.strokeLine(0, y(lat), w, y(lat));

        if (route.size() > 1) {
            g.setStroke(Color.DARKORANGE);
            g.setLineWidth(2);
            for (int i = 1; i < route.size(); i++) {
                Location a = route.get(i - 1), b = route.get(i);
                g.strokeLine(x(a.getLongitude()), y(a.getLatitude()), x(b.getLongitude()), y(b.getLatitude()));
            }
        }
        g.setTextAlign(TextAlignment.LEFT);
        for (Parcel p : parcels.values()) {
            Location l = p.getCurrentLocation();
            if (l == null || l.getLatitude() == null || l.getLongitude() == null) continue;
            boolean sel = p.getId().equals(selectedId);
            double px = x(l.getLongitude()), py = y(l.getLatitude());
            g.setFill(sel ? Color.RED : Color.DODGERBLUE);
            double r = sel ? 7 : 5;
            g.fillOval(px - r, py - r, 2 * r, 2 * r);
            g.setFill(Color.BLACK);
            g.fillText(String.valueOf(p.getTrackingNumber()), px + r + 2, py + 4);
        }
    }
}
