package com.example.espoints.tile;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Generation-scoped per-key ownership and atomic readiness publication. */
public final class ProgressiveTileReadiness<K, V> {
    private final long generation;
    private final Map<K, CompletableFuture<V>> futures = new ConcurrentHashMap<>();
    private final Set<K> owners = ConcurrentHashMap.newKeySet();
    private volatile Throwable terminalFailure;

    public ProgressiveTileReadiness(long generation) {
        this.generation = generation;
    }

    public CompletableFuture<V> future(K key) {
        CompletableFuture<V> future = futures.computeIfAbsent(
            key, ignored -> new CompletableFuture<>());
        Throwable failure = terminalFailure;
        if (failure != null) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    public boolean claim(long expectedGeneration, K key) {
        return expectedGeneration == generation
            && terminalFailure == null
            && !future(key).isDone()
            && owners.add(key);
    }

    public void release(K key) {
        owners.remove(key);
    }

    public boolean publish(long expectedGeneration, K key, V value) {
        if (expectedGeneration != generation || terminalFailure != null
            || !owners.contains(key)) {
            return false;
        }
        return future(key).complete(value);
    }

    public boolean isReady(K key) {
        CompletableFuture<V> future = futures.get(key);
        return future != null && future.isDone() && !future.isCompletedExceptionally();
    }

    public void fail(Throwable error) {
        if (terminalFailure != null) {
            return;
        }
        terminalFailure = error;
        owners.clear();
        for (CompletableFuture<V> future : futures.values()) {
            future.completeExceptionally(error);
        }
    }

    public int ownerCount() {
        return owners.size();
    }
}
