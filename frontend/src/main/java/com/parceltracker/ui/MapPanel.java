package com.parceltracker.ui;

import com.parceltracker.model.Location;
import com.parceltracker.model.Parcel;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

/** Live map: OpenStreetMap tiles (Web Mercator) with parcel markers and the selected parcel's route. */
public class MapPanel extends Pane {
    private static final int TILE = 256;
    private static final int MAX_ZOOM = 18;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final Canvas canvas = new Canvas();
    private final Map<String, Parcel> parcels = new LinkedHashMap<>();
    private final Map<String, Image> tiles = new ConcurrentHashMap<>();
    private final Set<String> loading = ConcurrentHashMap.newKeySet();
    private String selectedId;
    private List<Location> route = new ArrayList<>();
    private double zoom = 1;
    private double centerX = 0.5, centerY = 0.5; // world coordinates in 0..1
    private boolean userMoved;
    private double dragX, dragY;

    public MapPanel() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(o -> { fitIfAuto(); redraw(); });
        canvas.heightProperty().addListener(o -> { fitIfAuto(); redraw(); });
        setMinSize(200, 200);
        canvas.setOnMousePressed(e -> { dragX = e.getX(); dragY = e.getY(); });
        canvas.setOnMouseDragged(e -> {
            double scale = TILE * Math.pow(2, zoom);
            centerX -= (e.getX() - dragX) / scale;
            centerY -= (e.getY() - dragY) / scale;
            dragX = e.getX();
            dragY = e.getY();
            userMoved = true;
            redraw();
        });
        canvas.setOnScroll(e -> {
            zoom = Math.max(1, Math.min(MAX_ZOOM, zoom + (e.getDeltaY() > 0 ? 1 : -1)));
            userMoved = true;
            redraw();
        });
        canvas.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) { userMoved = false; fitIfAuto(); redraw(); }
        });
    }

    public void setParcels(List<Parcel> list) {
        parcels.clear();
        list.forEach(p -> parcels.put(p.getId(), p));
        fitIfAuto();
        redraw();
    }

    public void setSelected(String id, List<Location> history) {
        selectedId = id;
        route = new ArrayList<>(history == null ? List.of() : history);
        userMoved = false;
        fitIfAuto();
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
        fitIfAuto();
        redraw();
    }

    private static boolean valid(Location l) {
        return l != null && l.getLatitude() != null && l.getLongitude() != null
                && Math.abs(l.getLatitude()) <= 85 && Math.abs(l.getLongitude()) <= 180;
    }

    private static double worldX(double lon) {
        return (lon + 180.0) / 360.0;
    }

    private static double worldY(double lat) {
        double r = Math.toRadians(lat);
        return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2;
    }

    private void fitIfAuto() {
        if (userMoved) return;
        double minX = 2, maxX = -1, minY = 2, maxY = -1;
        List<Location> points = new ArrayList<>(route);
        parcels.values().forEach(p -> points.add(p.getCurrentLocation()));
        for (Location l : points) {
            if (!valid(l)) continue;
            double wx = worldX(l.getLongitude()), wy = worldY(l.getLatitude());
            minX = Math.min(minX, wx); maxX = Math.max(maxX, wx);
            minY = Math.min(minY, wy); maxY = Math.max(maxY, wy);
        }
        if (maxX < minX) return;
        centerX = (minX + maxX) / 2;
        centerY = (minY + maxY) / 2;
        double w = Math.max(canvas.getWidth() - 80, 50), h = Math.max(canvas.getHeight() - 80, 50);
        double spanX = Math.max(maxX - minX, 1e-9), spanY = Math.max(maxY - minY, 1e-9);
        double z = Math.log(Math.min(w / spanX, h / spanY) / TILE) / Math.log(2);
        zoom = Math.max(1, Math.min(15, Math.floor(z)));
    }

    private double sx(double wx) {
        return canvas.getWidth() / 2 + (wx - centerX) * TILE * Math.pow(2, zoom);
    }

    private double sy(double wy) {
        return canvas.getHeight() / 2 + (wy - centerY) * TILE * Math.pow(2, zoom);
    }

    private void drawTiles(GraphicsContext g) {
        int z = (int) zoom;
        int n = 1 << z;
        double scale = TILE * Math.pow(2, zoom);
        int x0 = (int) Math.floor((centerX - canvas.getWidth() / 2 / scale) * n);
        int x1 = (int) Math.floor((centerX + canvas.getWidth() / 2 / scale) * n);
        int y0 = (int) Math.floor((centerY - canvas.getHeight() / 2 / scale) * n);
        int y1 = (int) Math.floor((centerY + canvas.getHeight() / 2 / scale) * n);
        double size = TILE * Math.pow(2, zoom - z);
        for (int tx = x0; tx <= x1; tx++) {
            for (int ty = Math.max(0, y0); ty <= Math.min(n - 1, y1); ty++) {
                int wrapped = Math.floorMod(tx, n);
                String key = z + "/" + wrapped + "/" + ty;
                Image img = tiles.get(key);
                double px = sx((double) tx / n), py = sy((double) ty / n);
                if (img != null) g.drawImage(img, px, py, size, size);
                else loadTile(key);
            }
        }
    }

    private void loadTile(String key) {
        if (!loading.add(key)) return;
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://tile.openstreetmap.org/" + key + ".png"))
                .header("User-Agent", "ParcelTrackerGPS/1.0 (desktop client)")
                .header("Referer", "https://github.com/blue-smarty/parcel-tracker-gps")
                .build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(response -> {
            if (response.statusCode() == 200) {
                Image image = new Image(new ByteArrayInputStream(response.body()));
                tiles.put(key, image);
                Platform.runLater(this::redraw);
            }
            loading.remove(key);
        }).exceptionally(e -> {
            loading.remove(key);
            return null;
        });
    }

    private void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        g.setFill(Color.web("#dbeafe"));
        g.fillRect(0, 0, w, h);
        drawTiles(g);

        if (route.size() > 1) {
            g.setStroke(Color.DARKORANGE);
            g.setLineWidth(3);
            for (int i = 1; i < route.size(); i++) {
                Location a = route.get(i - 1), b = route.get(i);
                if (!valid(a) || !valid(b)) continue;
                g.strokeLine(sx(worldX(a.getLongitude())), sy(worldY(a.getLatitude())),
                        sx(worldX(b.getLongitude())), sy(worldY(b.getLatitude())));
            }
        }
        g.setTextAlign(TextAlignment.LEFT);
        for (Parcel p : parcels.values()) {
            Location l = p.getCurrentLocation();
            if (!valid(l)) continue;
            boolean sel = p.getId().equals(selectedId);
            double px = sx(worldX(l.getLongitude())), py = sy(worldY(l.getLatitude()));
            g.setFill(sel ? Color.RED : Color.DODGERBLUE);
            g.setStroke(Color.WHITE);
            g.setLineWidth(2);
            double r = sel ? 8 : 6;
            g.fillOval(px - r, py - r, 2 * r, 2 * r);
            g.strokeOval(px - r, py - r, 2 * r, 2 * r);
            g.setFill(Color.BLACK);
            g.fillText(String.valueOf(p.getTrackingNumber()), px + r + 3, py + 4);
        }
        g.setFill(Color.web("#000000aa"));
        g.setTextAlign(TextAlignment.RIGHT);
        g.fillText("© OpenStreetMap contributors", w - 4, h - 4);
    }
}
