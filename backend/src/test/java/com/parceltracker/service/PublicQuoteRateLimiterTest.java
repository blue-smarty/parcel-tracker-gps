package com.parceltracker.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PublicQuoteRateLimiterTest {
    @Test
    void limitsEachClientToTenRequestsPerMinute() {
        PublicQuoteRateLimiter limiter = new PublicQuoteRateLimiter();
        for (int request = 0; request < 10; request++) {
            limiter.check("192.0.2.1");
        }

        assertThrows(ResponseStatusException.class, () -> limiter.check("192.0.2.1"));
        limiter.check("192.0.2.2");
    }
}
