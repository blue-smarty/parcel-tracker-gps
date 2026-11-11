package com.parceltracker.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PublicQuoteRateLimiter {
    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    private record Window(long startedAt, int requests) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();

    public void check(String clientAddress) {
        String key = clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
        long now = System.currentTimeMillis();
        AtomicReference<Boolean> allowed = new AtomicReference<>(true);
        windows.compute(key, (address, window) -> {
            if (window == null || now - window.startedAt() >= WINDOW_MILLIS) {
                return new Window(now, 1);
            }
            if (window.requests() >= MAX_REQUESTS) {
                allowed.set(false);
                return window;
            }
            return new Window(window.startedAt(), window.requests() + 1);
        });
        if ((operations.incrementAndGet() & 255) == 0) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        }
        if (!allowed.get()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many quote requests. Please try again in a minute.");
        }
    }
}
