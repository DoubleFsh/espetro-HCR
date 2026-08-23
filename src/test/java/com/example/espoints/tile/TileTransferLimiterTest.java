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
            player, 512 * 1024, now, playerRate, globalRate));
        assertFalse(limiter.allow(player, 1, now, playerRate, globalRate));
        assertTrue(limiter.allow(
            player, 256 * 1024, now + 1_000L, playerRate, globalRate));
        assertFalse(limiter.allow(
            player, 256 * 1024 + 1, now + 1_000L, playerRate, globalRate));
    }

    @Test
    void firstGlanceAllowsAnOpeningBurstThenReturnsToAverage() {
        TileTransferLimiter limiter = new TileTransferLimiter();
        UUID player = UUID.randomUUID();
        long playerRate = 256L * 1024L;
        long globalRate = 4L * 1024L * 1024L;
        limiter.grantFirstGlance(player, 1L);
        assertTrue(limiter.allow(player, 1536 * 1024, 1L, playerRate, globalRate));
        assertFalse(limiter.allow(player, 1, 1L, playerRate, globalRate));
        assertTrue(limiter.allow(
            player, 256 * 1024, 1L + 3_000L, playerRate, globalRate));
    }

    @Test
    void oversizedLegalTileSendsOnlyFromAFullPlayerBurst() {
        TileTransferLimiter limiter = new TileTransferLimiter();
        UUID player = UUID.randomUUID();
        long playerRate = 256L * 1024L;
        long globalRate = 4L * 1024L * 1024L;
        int oversized = 512 * 1024 + 1;

        assertTrue(limiter.allow(player, oversized, 1L, playerRate, globalRate));
        assertFalse(limiter.allow(player, oversized, 1L, playerRate, globalRate));
        assertFalse(limiter.allow(player, oversized, 1L + 500L, playerRate, globalRate));
        assertTrue(limiter.allow(player, oversized, 1L + 2_000L, playerRate, globalRate));
        assertFalse(limiter.allow(
            player, TacticalMapTileService.MAX_ENCODED_TILE_BYTES + 1,
            1L + 4_000L, playerRate, globalRate));
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
        assertTrue(acceptedAtOnce <= 32);
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
