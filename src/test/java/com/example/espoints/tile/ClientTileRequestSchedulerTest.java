package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientTileRequestSchedulerTest {
    @Test
    void desiredAndOutstandingHaveIndependentHardBounds() {
        ClientTileRequestScheduler<Integer> scheduler = new ClientTileRequestScheduler<>();
        scheduler.updateDesired(IntStream.range(0, 400).boxed().toList());
        assertEquals(256, scheduler.desiredSize());
        assertEquals(4, scheduler.poll(1_000L, 4).size());
        assertEquals(28, scheduler.poll(1_001L, 64).size());
        assertEquals(32, scheduler.outstandingSize());
        assertTrue(scheduler.poll(1_002L, 64).isEmpty());
    }

    @Test
    void responsesFreeWindowAndTimeoutUsesDeterministicBackoff() {
        ClientTileRequestScheduler<Integer> scheduler =
            new ClientTileRequestScheduler<>(8, 2);
        scheduler.updateDesired(List.of(3, 4, 5));
        assertEquals(List.of(3, 4), scheduler.poll(10_000L, 8));
        scheduler.complete(3);
        assertEquals(List.of(5), scheduler.poll(10_001L, 8));
        assertTrue(scheduler.poll(10_600L, 8).size() <= 2);
        assertTrue(scheduler.outstandingSize() <= 2);
    }

    @Test
    void receivedTileFreesNetworkWindowWithoutDuplicatingDuringDecode() {
        ClientTileRequestScheduler<Integer> scheduler =
            new ClientTileRequestScheduler<>(8, 2);
        scheduler.updateDesired(List.of(3, 4, 5));
        assertEquals(List.of(3, 4), scheduler.poll(10_000L, 8));

        scheduler.received(3);
        assertEquals(1, scheduler.processingSize());
        assertEquals(List.of(5), scheduler.poll(10_001L, 8));
        assertTrue(scheduler.poll(30_000L, 8).stream().noneMatch(key -> key == 3));

        scheduler.complete(3);
        assertEquals(0, scheduler.processingSize());
        assertEquals(2, scheduler.desiredSize());
    }
}
