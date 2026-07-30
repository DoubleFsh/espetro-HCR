package com.example.espoints.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small per-player/per-request cooldown registry with explicit lifecycle cleanup. */
public final class RequestRateLimiter {
    private static final Map<Key, Long> LAST_ACCEPTED = new ConcurrentHashMap<>();

    private RequestRateLimiter() {
    }

    public static boolean allow(
            UUID playerId, String channel, long nowMillis, long minimumIntervalMillis) {
        if (playerId == null || channel == null || channel.isBlank()
            || minimumIntervalMillis < 0L) {
            return false;
        }
        Key key = new Key(playerId, channel);
        final boolean[] accepted = {false};
        LAST_ACCEPTED.compute(key, (ignored, previous) -> {
            if (previous == null || nowMillis < previous
                || nowMillis - previous >= minimumIntervalMillis) {
                accepted[0] = true;
                return nowMillis;
            }
            return previous;
        });
        return accepted[0];
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) {
            LAST_ACCEPTED.keySet().removeIf(key -> key.playerId.equals(playerId));
        }
    }

    public static void clearAll() {
        LAST_ACCEPTED.clear();
    }

    static int size() {
        return LAST_ACCEPTED.size();
    }

    private record Key(UUID playerId, String channel) {
    }
}
