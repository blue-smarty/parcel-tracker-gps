package com.parceltracker.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

public class SupportTicketStore {
    private static final String TICKET_IDS_KEY = "ticketIds";
    private static final int MAX_TICKETS = 100;
    private static final int MAX_MESSAGE_BYTES = 6000;
    private static final Preferences STORE = Preferences.userNodeForPackage(SupportTicketStore.class);

    public record Ticket(String id, String trackingNumber, String message, String status, Instant createdAt) { }

    public List<Ticket> findAll() {
        try {
            List<Ticket> tickets = new ArrayList<>();
            for (String id : ticketIds()) {
                Preferences ticketNode = STORE.node(id);
                tickets.add(new Ticket(id, ticketNode.get("trackingNumber", null),
                        ticketNode.get("message", ""), ticketNode.get("status", "OPEN"),
                        Instant.parse(ticketNode.get("createdAt", Instant.EPOCH.toString()))));
            }
            tickets.sort(Comparator.comparing(Ticket::createdAt).reversed());
            return tickets;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read locally stored support tickets", e);
        }
    }

    public Ticket create(String trackingNumber, String message) {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Support message is required");
        if (message.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Support message must be 6000 bytes or fewer");
        }
        Ticket ticket = new Ticket(UUID.randomUUID().toString(), trackingNumber, message.trim(), "OPEN", Instant.now());
        try {
            Preferences ticketNode = STORE.node(ticket.id());
            ticketNode.put("trackingNumber", ticket.trackingNumber() == null ? "" : ticket.trackingNumber());
            ticketNode.put("message", ticket.message());
            ticketNode.put("status", ticket.status());
            ticketNode.put("createdAt", ticket.createdAt().toString());
            List<String> ids = ticketIds();
            ids.add(ticket.id());
            while (ids.size() > MAX_TICKETS) {
                STORE.node(ids.remove(0)).removeNode();
            }
            STORE.put(TICKET_IDS_KEY, String.join(",", ids));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save locally stored support tickets", e);
        }
        return ticket;
    }

    public void resolve(String id) {
        try {
            if (!ticketIds().contains(id)) throw new IllegalArgumentException("Support ticket not found");
            STORE.node(id).put("status", "RESOLVED");
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalStateException("Cannot resolve locally stored support ticket", e);
        }
    }

    private List<String> ticketIds() {
        String stored = STORE.get(TICKET_IDS_KEY, "");
        if (stored.isBlank()) return new ArrayList<>();
        return new ArrayList<>(List.of(stored.split(",")));
    }
}
