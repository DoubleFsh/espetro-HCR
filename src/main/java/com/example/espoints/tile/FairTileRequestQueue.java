package com.example.espoints.tile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Bounded, duplicate-free, player round-robin queue.
 *
 * <p>Only immutable tile keys are retained. Encoded PNG payloads remain in the
 * shared service cache and are never pinned by a slow or disconnected player.</p>
 */
public final class FairTileRequestQueue<K> {
    public static final int DEFAULT_PER_PLAYER_LIMIT = 64;
    public static final int DEFAULT_GLOBAL_LIMIT = 4096;

    private final int perPlayerLimit;
    private final int globalLimit;
    private final Map<UUID, LinkedHashSet<K>> pendingByPlayer = new HashMap<>();
    private final Deque<UUID> roundRobin = new ArrayDeque<>();
    private final Set<UUID> scheduledPlayers = new LinkedHashSet<>();
    private int size;

    public FairTileRequestQueue() {
        this(DEFAULT_PER_PLAYER_LIMIT, DEFAULT_GLOBAL_LIMIT);
    }

    public FairTileRequestQueue(int perPlayerLimit, int globalLimit) {
        if (perPlayerLimit < 1 || globalLimit < perPlayerLimit) {
            throw new IllegalArgumentException("Invalid tile queue bounds");
        }
        this.perPlayerLimit = perPlayerLimit;
        this.globalLimit = globalLimit;
    }

    public synchronized OfferResult offer(UUID playerId, K key) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(key, "key");
        LinkedHashSet<K> playerQueue = pendingByPlayer.computeIfAbsent(
            playerId, ignored -> new LinkedHashSet<>());
        if (playerQueue.contains(key)) {
            return OfferResult.DUPLICATE;
        }
        if (playerQueue.size() >= perPlayerLimit) {
            return OfferResult.PLAYER_FULL;
        }
        if (size >= globalLimit) {
            if (playerQueue.isEmpty()) {
                pendingByPlayer.remove(playerId);
            }
            return OfferResult.GLOBAL_FULL;
        }
        playerQueue.add(key);
        size++;
        schedule(playerId);
        return OfferResult.ACCEPTED;
    }

    /** Returns one player's oldest key and rotates that player behind peers. */
    public synchronized Entry<K> poll() {
        return poll(null);
    }

    /**
     * Same fairness rotation. {@code picker} may choose which of the current
     * player's pending keys to emit; null keeps FIFO.
     */
    public synchronized Entry<K> poll(
            java.util.function.BiFunction<UUID, java.util.Set<K>, K> picker) {
        while (!roundRobin.isEmpty()) {
            UUID playerId = roundRobin.removeFirst();
            scheduledPlayers.remove(playerId);
            LinkedHashSet<K> queue = pendingByPlayer.get(playerId);
            if (queue == null || queue.isEmpty()) {
                pendingByPlayer.remove(playerId);
                continue;
            }
            K key = picker == null ? null : picker.apply(playerId, queue);
            if (key == null || !queue.contains(key)) {
                key = queue.iterator().next();
            }
            queue.remove(key);
            size--;
            if (queue.isEmpty()) {
                pendingByPlayer.remove(playerId);
            } else {
                schedule(playerId);
            }
            return new Entry<>(playerId, key);
        }
        return null;
    }

    /** Requeues a not-yet-ready or rate-limited entry behind all current peers. */
    public synchronized void defer(Entry<K> entry) {
        if (entry != null) {
            offer(entry.playerId(), entry.key());
        }
    }

    public synchronized int removePlayer(UUID playerId) {
        LinkedHashSet<K> removed = pendingByPlayer.remove(playerId);
        roundRobin.removeIf(playerId::equals);
        scheduledPlayers.remove(playerId);
        int count = removed == null ? 0 : removed.size();
        size -= count;
        return count;
    }

    public synchronized int removeIf(Predicate<K> predicate) {
        int removed = 0;
        for (var iterator = pendingByPlayer.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<UUID, LinkedHashSet<K>> entry = iterator.next();
            int before = entry.getValue().size();
            entry.getValue().removeIf(predicate);
            removed += before - entry.getValue().size();
            if (entry.getValue().isEmpty()) {
                iterator.remove();
                roundRobin.removeIf(entry.getKey()::equals);
                scheduledPlayers.remove(entry.getKey());
            }
        }
        size -= removed;
        return removed;
    }

    public synchronized void clear() {
        pendingByPlayer.clear();
        roundRobin.clear();
        scheduledPlayers.clear();
        size = 0;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized int playerSize(UUID playerId) {
        Set<K> queue = pendingByPlayer.get(playerId);
        return queue == null ? 0 : queue.size();
    }

    private void schedule(UUID playerId) {
        if (scheduledPlayers.add(playerId)) {
            roundRobin.addLast(playerId);
        }
    }

    public enum OfferResult {
        ACCEPTED,
        DUPLICATE,
        PLAYER_FULL,
        GLOBAL_FULL
    }

    public record Entry<K>(UUID playerId, K key) {
    }
}
