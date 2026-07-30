package com.example.espoints.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestRateLimiterTest {
    @AfterEach
    void clear() {
        RequestRateLimiter.clearAll();
    }

    @Test
    void isolatesChannelsAndCleansPlayerLifecycleState() {
        UUID player = UUID.randomUUID();
        assertTrue(RequestRateLimiter.allow(player, "overview", 1_000, 500));
        assertFalse(RequestRateLimiter.allow(player, "overview", 1_499, 500));
        assertTrue(RequestRateLimiter.allow(player, "markers", 1_001, 500));
        assertEquals(2, RequestRateLimiter.size());

        RequestRateLimiter.clearPlayer(player);
        assertEquals(0, RequestRateLimiter.size());
        assertTrue(RequestRateLimiter.allow(player, "overview", 1_100, 500));
    }
}
