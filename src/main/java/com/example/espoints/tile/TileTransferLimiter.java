package com.example.espoints.tile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player and global token buckets with a bounded two-second burst.
 *
 * <p>A single legal PNG may exceed the 2s player burst (512 KiB by default)
 * but never the 2 MiB tile cap. Those oversized tiles wait until the player
 * bucket is full, then drain it instead of being permanently rejected.</p>
 */
public final class TileTransferLimiter {
    private static final int BURST_SECONDS = 2;
    private static final long FIRST_GLANCE_BYTES = 1536L * 1024L;
    private static final long FIRST_GLANCE_MILLIS = 3_000L;
    private final Map<UUID, Bucket> players = new HashMap<>();
    private final Map<UUID, Long> firstGlanceUntil = new HashMap<>();
    private final Bucket global = new Bucket();

    public synchronized void grantFirstGlance(UUID playerId, long nowMillis) {
        if (playerId != null) {
            firstGlanceUntil.put(playerId, nowMillis + FIRST_GLANCE_MILLIS);
        }
    }

    public synchronized boolean allow(UUID playerId, int bytes, long nowMillis,
                                      long playerBytesPerSecond, long globalBytesPerSecond) {
        if (playerId == null || bytes <= 0
            || playerBytesPerSecond <= 0L || globalBytesPerSecond <= 0L
            || bytes > TacticalMapTileService.MAX_ENCODED_TILE_BYTES) {
            return false;
        }
        long playerCapacity = saturatedMultiply(playerBytesPerSecond, BURST_SECONDS);
        Long glanceUntil = firstGlanceUntil.get(playerId);
        if (glanceUntil != null && nowMillis <= glanceUntil) {
            playerCapacity = Math.max(playerCapacity, FIRST_GLANCE_BYTES);
        }
        long globalCapacity = saturatedMultiply(globalBytesPerSecond, BURST_SECONDS);
        if (bytes > globalCapacity) {
            return false;
        }
        Bucket player = players.computeIfAbsent(playerId, ignored -> new Bucket());
        player.refill(nowMillis, playerBytesPerSecond, playerCapacity);
        global.refill(nowMillis, globalBytesPerSecond, globalCapacity);
        if (bytes <= playerCapacity) {
            if (player.tokens < bytes || global.tokens < bytes) {
                return false;
            }
            player.tokens -= bytes;
            global.tokens -= bytes;
            return true;
        }
        if (player.tokens + 0.5D < playerCapacity || global.tokens < bytes) {
            return false;
        }
        player.tokens = 0.0D;
        global.tokens -= bytes;
        return true;
    }

    public synchronized void clear() {
        players.clear();
        firstGlanceUntil.clear();
        global.reset();
    }

    public synchronized void removePlayer(UUID playerId) {
        if (playerId != null) {
            players.remove(playerId);
            firstGlanceUntil.remove(playerId);
        }
    }

    private static long saturatedMultiply(long value, int multiplier) {
        return value > Long.MAX_VALUE / multiplier
            ? Long.MAX_VALUE
            : value * multiplier;
    }

    private static final class Bucket {
        private long lastRefill;
        private double tokens;

        private void refill(long now, long rate, long capacity) {
            if (lastRefill == 0L || now < lastRefill) {
                lastRefill = now;
                tokens = capacity;
                return;
            }
            long elapsed = now - lastRefill;
            lastRefill = now;
            tokens = Math.min(capacity, tokens + elapsed * rate / 1000.0D);
        }

        private void reset() {
            lastRefill = 0L;
            tokens = 0.0D;
        }
    }
}
