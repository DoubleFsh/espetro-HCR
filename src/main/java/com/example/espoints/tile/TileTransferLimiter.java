package com.example.espoints.tile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player and global token buckets with a bounded eight-second burst. */
public final class TileTransferLimiter {
    private static final int BURST_SECONDS = 8;
    private final Map<UUID, Bucket> players = new HashMap<>();
    private final Bucket global = new Bucket();

    public synchronized boolean allow(UUID playerId, int bytes, long nowMillis,
                                      long playerBytesPerSecond, long globalBytesPerSecond) {
        if (playerId == null || bytes <= 0
            || playerBytesPerSecond <= 0L || globalBytesPerSecond <= 0L
            || bytes > saturatedMultiply(playerBytesPerSecond, BURST_SECONDS)
            || bytes > saturatedMultiply(globalBytesPerSecond, BURST_SECONDS)) {
            return false;
        }
        Bucket player = players.computeIfAbsent(playerId, ignored -> new Bucket());
        player.refill(nowMillis, playerBytesPerSecond);
        global.refill(nowMillis, globalBytesPerSecond);
        if (player.tokens < bytes || global.tokens < bytes) {
            return false;
        }
        player.tokens -= bytes;
        global.tokens -= bytes;
        return true;
    }

    public synchronized void clear() {
        players.clear();
        global.reset();
    }

    private static long saturatedMultiply(long value, int multiplier) {
        return value > Long.MAX_VALUE / multiplier
            ? Long.MAX_VALUE
            : value * multiplier;
    }

    private static final class Bucket {
        private long lastRefill;
        private double tokens;

        private void refill(long now, long rate) {
            long capacity = saturatedMultiply(rate, BURST_SECONDS);
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
