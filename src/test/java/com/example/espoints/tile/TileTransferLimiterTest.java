package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileTransferLimiterTest {
    @Test
    void permitsBoundedLargeTileBurstThenEnforcesConfiguredAverage() {
        TileTransferLimiter limiter = new TileTransferLimiter();
        UUID player = UUID.randomUUID();
        long playerRate = 256L * 1024L;
        long globalRate = 4L * 1024L * 1024L;
        long now = 10_000L;

        assertTrue(limiter.allow(
            player, 2 * 1024 * 1024, now, playerRate, globalRate));
        assertFalse(limiter.allow(player, 1, now, playerRate, globalRate));
        assertTrue(limiter.allow(
            player, 256 * 1024, now + 1_000L, playerRate, globalRate));
        assertFalse(limiter.allow(
            player, 256 * 1024 + 1, now + 1_000L, playerRate, globalRate));
    }

    @Test
    void rejectsPayloadLargerThanBoundedBurst() {
        TileTransferLimiter limiter = new TileTransferLimiter();
        assertFalse(limiter.allow(
            UUID.randomUUID(), 2 * 1024 * 1024 + 1, 1L,
            256L * 1024L, 4L * 1024L * 1024L));
    }

    @Test
    void oneHundredSubscribersRemainBoundByOriginalGlobalTokenBucket() {
        TileTransferLimiter limiter = new TileTransferLimiter();
        UUID[] subscribers = IntStream.range(0, 100)
            .mapToObj(ignored -> UUID.randomUUID()).toArray(UUID[]::new);
        long playerRate = 256L * 1024L;
        long globalRate = 4L * 1024L * 1024L;
        int tileBytes = 256 * 1024;
        long now = 20_000L;

        int acceptedAtOnce = 0;
        for (int round = 0; round < 2; round++) {
            for (UUID subscriber : subscribers) {
                if (limiter.allow(subscriber, tileBytes, now, playerRate, globalRate)) {
                    acceptedAtOnce++;
                }
            }
        }
        assertTrue(acceptedAtOnce <= 128);
        assertFalse(limiter.allow(
            subscribers[0], tileBytes, now, playerRate, globalRate));

        int acceptedAfterOneSecond = 0;
        for (UUID subscriber : subscribers) {
            if (limiter.allow(
                    subscriber, tileBytes, now + 1_000L, playerRate, globalRate)) {
                acceptedAfterOneSecond++;
            }
        }
        assertTrue(acceptedAfterOneSecond <= 16);
    }
}
