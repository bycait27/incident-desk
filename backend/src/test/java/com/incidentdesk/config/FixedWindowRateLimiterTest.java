package com.incidentdesk.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    @Test
    void startsANewWindowAfterOneMinute() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();

        assertTrue(limiter.tryAcquire("client", 1, 100L).allowed());
        FixedWindowRateLimiter.Decision limited = limiter.tryAcquire("client", 1, 200L);
        assertFalse(limited.allowed());
        assertTrue(limited.retryAfterSeconds() > 0);

        assertTrue(limiter.tryAcquire("client", 1, 60_000_000_100L).allowed());
    }
}
