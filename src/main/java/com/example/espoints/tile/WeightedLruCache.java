package com.example.espoints.tile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Small synchronized weighted LRU with deterministic eviction callbacks. */
public final class WeightedLruCache<K, V> {
    private final LinkedHashMap<K, V> values = new LinkedHashMap<>(16, 0.75F, true);
    private final ToLongFunction<V> weigh;
    private long maximumWeight;
    private long weight;

    public WeightedLruCache(long maximumWeight, ToLongFunction<V> weigh) {
        this.maximumWeight = Math.max(0L, maximumWeight);
        this.weigh = weigh;
    }

    public synchronized V get(K key) {
        return values.get(key);
    }

    public synchronized List<V> put(K key, V value) {
        List<V> evicted = new ArrayList<>();
        V previous = values.put(key, value);
        if (previous != null) {
            weight -= safeWeight(previous);
            evicted.add(previous);
        }
        weight += safeWeight(value);
        evictOverBudget(evicted);
        return evicted;
    }

    private void evictOverBudget(List<V> evicted) {
        while (weight > maximumWeight && !values.isEmpty()) {
            Map.Entry<K, V> eldest = values.entrySet().iterator().next();
            values.remove(eldest.getKey());
            weight -= safeWeight(eldest.getValue());
            evicted.add(eldest.getValue());
        }
    }

    public synchronized List<V> clear() {
        List<V> removed = new ArrayList<>(values.values());
        values.clear();
        weight = 0L;
        return removed;
    }

    public synchronized int size() { return values.size(); }
    public synchronized long weight() { return weight; }

    public synchronized List<V> setMaximumWeight(long maximumWeight) {
        this.maximumWeight = Math.max(0L, maximumWeight);
        List<V> evicted = new ArrayList<>();
        evictOverBudget(evicted);
        return evicted;
    }

    private long safeWeight(V value) {
        return Math.max(0L, weigh.applyAsLong(value));
    }
}
