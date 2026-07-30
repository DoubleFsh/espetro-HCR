package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeightedLruCacheTest {
    @Test
    void evictsLeastRecentlyUsedAndImmediatelyHonorsReducedBudget() {
        WeightedLruCache<String, byte[]> cache =
            new WeightedLruCache<>(6, value -> value.length);
        cache.put("a", new byte[2]);
        cache.put("b", new byte[2]);
        cache.put("c", new byte[2]);
        cache.get("a");

        assertEquals(1, cache.put("d", new byte[2]).size());
        assertNull(cache.get("b"));
        assertEquals(6, cache.weight());

        List<byte[]> evicted = cache.setMaximumWeight(2);
        assertEquals(2, evicted.size());
        assertEquals(1, cache.size());
        assertEquals(2, cache.weight());
        assertEquals(1, cache.clear().size());
        assertEquals(0, cache.weight());
    }
}
