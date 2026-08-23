package com.example.espoints.tile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FairTileRequestQueueTest {
    @Test
    void roundRobinPreventsOnePlayerFromStarvingPeers() {
        FairTileRequestQueue<Integer> queue = new FairTileRequestQueue<>(64, 4096);
        UUID first = new UUID(0L, 1L);
        UUID second = new UUID(0L, 2L);
        for (int key = 0; key < 64; key++) {
            assertEquals(FairTileRequestQueue.OfferResult.ACCEPTED,
                queue.offer(first, key));
        }
        queue.offer(second, 100);
        queue.offer(second, 101);

        List<UUID> order = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            order.add(queue.poll().playerId());
        }
        assertEquals(List.of(first, second, first, second), order);
    }

    @Test
    void duplicateBoundsAndDisconnectAreExact() {
        FairTileRequestQueue<Integer> queue = new FairTileRequestQueue<>(2, 3);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertEquals(FairTileRequestQueue.OfferResult.ACCEPTED, queue.offer(first, 1));
        assertEquals(FairTileRequestQueue.OfferResult.DUPLICATE, queue.offer(first, 1));
        assertEquals(FairTileRequestQueue.OfferResult.ACCEPTED, queue.offer(first, 2));
        assertEquals(FairTileRequestQueue.OfferResult.PLAYER_FULL, queue.offer(first, 3));
        assertEquals(FairTileRequestQueue.OfferResult.ACCEPTED, queue.offer(second, 3));
        assertEquals(FairTileRequestQueue.OfferResult.GLOBAL_FULL, queue.offer(second, 4));
        assertEquals(2, queue.removePlayer(first));
        assertEquals(1, queue.size());
        assertEquals(second, queue.poll().playerId());
        assertEquals(0, queue.size());
    }

    @Test
    void pickerCanPreferALaterKeyForTheCurrentPlayer() {
        FairTileRequestQueue<Integer> queue = new FairTileRequestQueue<>();
        UUID player = UUID.randomUUID();
        queue.offer(player, 1);
        queue.offer(player, 9);
        queue.offer(player, 3);
        FairTileRequestQueue.Entry<Integer> first = queue.poll(
            (ignored, pending) -> pending.stream().max(Integer::compareTo).orElse(null));
        assertEquals(9, first.key());
    }

    @Test
    void oneHundredPlayersEachReceiveServiceWithinOneRound() {
        FairTileRequestQueue<Integer> queue = new FairTileRequestQueue<>();
        List<UUID> players = java.util.stream.IntStream.range(0, 100)
            .mapToObj(index -> new UUID(7L, index + 1L)).toList();
        for (int index = 0; index < players.size(); index++) {
            queue.offer(players.get(index), index);
            queue.offer(players.get(index), index + 1_000);
        }
        Set<UUID> firstRound = new HashSet<>();
        for (int index = 0; index < 100; index++) {
            firstRound.add(queue.poll().playerId());
        }
        assertEquals(new HashSet<>(players), firstRound);
    }
}
