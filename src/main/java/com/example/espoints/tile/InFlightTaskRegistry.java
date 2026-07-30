package com.example.espoints.tile;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Coalesces concurrent work for the same immutable cache key. */
public final class InFlightTaskRegistry<K, V> {
    private final Map<K, CompletableFuture<V>> tasks = new ConcurrentHashMap<>();

    public CompletableFuture<V> getOrStart(
            K key, Supplier<CompletableFuture<V>> starter) {
        CompletableFuture<V> task =
            tasks.computeIfAbsent(key, ignored -> starter.get());
        task.whenComplete((result, error) -> tasks.remove(key, task));
        return task;
    }

    public void clear() {
        tasks.clear();
    }

    public int size() {
        return tasks.size();
    }
}
