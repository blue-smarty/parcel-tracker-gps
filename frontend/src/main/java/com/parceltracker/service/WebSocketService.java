package com.parceltracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.parceltracker.model.Location;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Connects to /ws/tracking/{parcelId} ("all" for every parcel) and reconnects when the connection drops. */
public class WebSocketService implements WebSocket.Listener {
    private static final long RECONNECT_DELAY_MS = 3000;

    private final String wsBaseUrl;
    private final String parcelId;
    private final BiConsumer<String, Location> onUpdate;
    private final Consumer<Boolean> onConnectionChange;
    private final HttpClient client = HttpClient.newHttpClient();
    private final StringBuilder buffer = new StringBuilder();
    private volatile boolean running;
    private volatile WebSocket socket;

    public WebSocketService(String wsBaseUrl, String parcelId, BiConsumer<String, Location> onUpdate,
                            Consumer<Boolean> onConnectionChange) {
        this.wsBaseUrl = wsBaseUrl.endsWith("/") ? wsBaseUrl.substring(0, wsBaseUrl.length() - 1) : wsBaseUrl;
        this.parcelId = parcelId;
        this.onUpdate = onUpdate;
        this.onConnectionChange = onConnectionChange;
    }

    public void connect() {
        running = true;
        client.newWebSocketBuilder()
                .buildAsync(URI.create(wsBaseUrl + "/ws/tracking/" + parcelId), this)
                .exceptionally(e -> {
                    scheduleReconnect();
                    return null;
                });
    }

    public void disconnect() {
        running = false;
        WebSocket s = socket;
        if (s != null) {
            s.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        }
    }

    private void scheduleReconnect() {
        onConnectionChange.accept(false);
        if (!running) return;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
            if (running) connect();
        }, "ws-reconnect");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        socket = webSocket;
        onConnectionChange.accept(true);
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            String json = buffer.toString();
            buffer.setLength(0);
            try {
                JsonNode node = ApiService.MAPPER.readTree(json);
                Location loc = ApiService.MAPPER.treeToValue(node.get("location"), Location.class);
                onUpdate.accept(node.get("parcelId").asText(), loc);
            } catch (Exception ignored) {
                // malformed message; ignore
            }
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        scheduleReconnect();
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        scheduleReconnect();
    }
}
