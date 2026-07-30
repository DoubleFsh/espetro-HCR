package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InFlightTaskRegistryTest {
    @Test
    void coalescesConcurrentRequestsAndDropsCompletedTask() {
        InFlightTaskRegistry<String, byte[]> registry =
            new InFlightTaskRegistry<>();
        AtomicInteger starts = new AtomicInteger();
        CompletableFuture<byte[]> source = new CompletableFuture<>();
        List<CompletableFuture<byte[]>> callers = new ArrayList<>();

        for (int index = 0; index < 100; index++) {
            callers.add(registry.getOrStart("same-tile", () -> {
                starts.incrementAndGet();
                return source;
            }));
        }
        assertEquals(1, starts.get());
        callers.forEach(call -> assertSame(source, call));
        assertEquals(1, registry.size());

        source.complete(new byte[]{1});
        assertEquals(0, registry.size());
        registry.getOrStart("same-tile", () -> {
            starts.incrementAndGet();
            return CompletableFuture.completedFuture(new byte[]{2});
        });
        assertEquals(2, starts.get());
        assertEquals(0, registry.size());
    }
}
