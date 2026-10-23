package com.parceltracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

public class SupportTicketStore {
    private static final String TICKETS_KEY = "tickets";
    private static final int MAX_TICKETS = 100;
    private static final Preferences STORE = Preferences.userNodeForPackage(SupportTicketStore.class);

    public record Ticket(String id, String trackingNumber, String message, String status, Instant createdAt) { }

    public List<Ticket> findAll() {
        String json = STORE.get(TICKETS_KEY, "[]");
        try {
            List<Ticket> tickets = ApiService.MAPPER.readValue(json, new TypeReference<>() { });
            tickets.sort(Comparator.comparing(Ticket::createdAt).reversed());
            return tickets;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read locally stored support tickets", e);
        }
    }

    public Ticket create(String trackingNumber, String message) {
        List<Ticket> tickets = new ArrayList<>(findAll());
        Ticket ticket = new Ticket(UUID.randomUUID().toString(), trackingNumber, message.trim(), "OPEN", Instant.now());
        tickets.add(ticket);
        if (tickets.size() > MAX_TICKETS) {
            tickets = new ArrayList<>(tickets.subList(tickets.size() - MAX_TICKETS, tickets.size()));
        }
        save(tickets);
        return ticket;
    }

    public void resolve(String id) {
        List<Ticket> tickets = new ArrayList<>(findAll());
        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            if (ticket.id().equals(id)) {
                tickets.set(i, new Ticket(ticket.id(), ticket.trackingNumber(), ticket.message(),
                        "RESOLVED", ticket.createdAt()));
                save(tickets);
                return;
            }
        }
        throw new IllegalArgumentException("Support ticket not found");
    }

    private void save(List<Ticket> tickets) {
        try {
            STORE.put(TICKETS_KEY, ApiService.MAPPER.writeValueAsString(tickets));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save locally stored support tickets", e);
        }
    }
}
