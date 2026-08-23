package com.example.espoints.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Separates locally desired tiles from the bounded network-outstanding window. */
public final class ClientTileRequestScheduler<K> {
    public static final int DEFAULT_DESIRED_LIMIT = 256;
    public static final int DEFAULT_OUTSTANDING_LIMIT = 32;
    private static final long BASE_RETRY_MILLIS = 500L;
    private static final long MAX_RETRY_MILLIS = 8_000L;

    private final int desiredLimit;
    private final int outstandingLimit;
    private final LinkedHashSet<K> desired = new LinkedHashSet<>();
    private final Map<K, Attempt> outstanding = new HashMap<>();
    /** Responses being decoded/uploaded no longer consume the network window. */
    private final Set<K> processing = new HashSet<>();
    private final Map<K, Attempt> retry = new HashMap<>();

    public ClientTileRequestScheduler() {
        this(DEFAULT_DESIRED_LIMIT, DEFAULT_OUTSTANDING_LIMIT);
    }

    public ClientTileRequestScheduler(int desiredLimit, int outstandingLimit) {
        if (desiredLimit < 1 || outstandingLimit < 1
            || outstandingLimit > desiredLimit
            || outstandingLimit > FairTileRequestQueue.DEFAULT_PER_PLAYER_LIMIT) {
            throw new IllegalArgumentException("Invalid client tile request bounds");
        }
        this.desiredLimit = desiredLimit;
        this.outstandingLimit = outstandingLimit;
    }

    /** Replaces the render-produced ordered desire snapshot. */
    public synchronized void updateDesired(List<K> ordered) {
        desired.clear();
        if (ordered != null) {
            for (K key : ordered) {
                if (key != null && desired.size() < desiredLimit) {
                    desired.add(key);
                }
            }
        }
        outstanding.keySet().removeIf(key -> !desired.contains(key));
        processing.removeIf(key -> !desired.contains(key));
        retry.keySet().removeIf(key -> !desired.contains(key));
    }

    public synchronized void addDesired(K key) {
        if (key != null && desired.size() < desiredLimit) {
            desired.add(key);
        }
    }

    /** Polls a bounded, deterministic batch for the client tick to send. */
    public synchronized List<K> poll(long nowMillis, int maximum) {
        expire(nowMillis);
        int available = Math.min(Math.max(0, maximum),
            outstandingLimit - outstanding.size());
        if (available == 0) {
            return List.of();
        }
        List<K> result = new ArrayList<>(available);
        for (K key : desired) {
            if (result.size() >= available) {
                break;
            }
            if (outstanding.containsKey(key) || processing.contains(key)) {
                continue;
            }
            Attempt previous = retry.get(key);
            if (previous != null && nowMillis < previous.retryAtMillis) {
                continue;
            }
            int attempt = previous == null ? 1 : previous.number + 1;
            long retryAt = safeAdd(nowMillis, retryDelay(key, attempt));
            Attempt next = new Attempt(attempt, retryAt);
            outstanding.put(key, next);
            retry.put(key, next);
            result.add(key);
        }
        return List.copyOf(result);
    }

    public synchronized void complete(K key) {
        outstanding.remove(key);
        processing.remove(key);
        retry.remove(key);
        desired.remove(key);
    }

    /** Marks a response received while keeping it desired until decode/upload succeeds. */
    public synchronized void received(K key) {
        outstanding.remove(key);
        if (desired.contains(key)) {
            processing.add(key);
        }
    }

    public synchronized void reject(K key, long nowMillis) {
        Attempt attempt = outstanding.remove(key);
        processing.remove(key);
        int number = attempt == null ? 1 : attempt.number;
        if (desired.contains(key)) {
            Attempt previous = retry.get(key);
            if (previous != null) {
                number = Math.max(number, previous.number);
            }
            retry.put(key, new Attempt(number,
                safeAdd(nowMillis, retryDelay(key, number))));
        } else {
            retry.remove(key);
        }
    }

    public synchronized void clear() {
        desired.clear();
        outstanding.clear();
        processing.clear();
        retry.clear();
    }

    public synchronized int desiredSize() {
        return desired.size();
    }

    public synchronized int outstandingSize() {
        return outstanding.size();
    }

    public synchronized int processingSize() {
        return processing.size();
    }

    public synchronized boolean isOutstanding(K key) {
        return outstanding.containsKey(key);
    }

    public synchronized boolean isDesired(K key) {
        return desired.contains(key);
    }

    public synchronized boolean isProcessing(K key) {
        return processing.contains(key);
    }

    private void expire(long nowMillis) {
        outstanding.entrySet().removeIf(entry -> nowMillis >= entry.getValue().retryAtMillis);
    }

    private static long retryDelay(Object key, int attempt) {
        int shift = Math.min(4, Math.max(0, attempt - 1));
        long exponential = Math.min(MAX_RETRY_MILLIS, BASE_RETRY_MILLIS << shift);
        long jitter = Math.floorMod(key.hashCode(), 127);
        return Math.min(MAX_RETRY_MILLIS, exponential + jitter);
    }

    private static long safeAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private record Attempt(int number, long retryAtMillis) {
    }
}
