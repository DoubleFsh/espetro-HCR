package com.example.espoints.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPlayerPositionsMessageTest {
    @Test
    void roundTripsFixedPointCoordinatesAndPackedYaw() {
        Map<Integer, SyncPlayerPositionsMessage.PlayerPosition> positions =
            Map.of(7, SyncPlayerPositionsMessage.PlayerPosition.positionOnly(
                123.125, 0, -456.75, 91.0F));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncPlayerPositionsMessage.encode(
            new SyncPlayerPositionsMessage(9, positions), buffer);

        SyncPlayerPositionsMessage decoded =
            SyncPlayerPositionsMessage.decode(buffer);
        SyncPlayerPositionsMessage.PlayerPosition sample =
            decoded.positions().get(7);
        assertEquals(9, decoded.session());
        assertEquals(123.125, sample.getX());
        assertEquals(-456.75, sample.getZ());
        assertEquals(90.0F, sample.getYaw(), 1.5F);
    }

    @Test
    void fortyAndHundredPlayerWorstCaseFramesStayInsideTrafficBudget() {
        assertTrafficBudget(40);
        assertTrafficBudget(100);
    }

    private static void assertTrafficBudget(int playerCount) {
        Map<Integer, SyncPlayerPositionsMessage.PlayerPosition> positions =
            new LinkedHashMap<>();
        for (int player = 1; player <= playerCount; player++) {
            positions.put(player,
                SyncPlayerPositionsMessage.PlayerPosition.positionOnly(
                    player * 8.125, 64, player * -4.25, player * 3.6F));
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncPlayerPositionsMessage.encode(
            new SyncPlayerPositionsMessage(1, positions), buffer);

        int frameBytes = buffer.readableBytes();
        long perClientBytesPerSecond = frameBytes * 2L;
        // More conservative than the live team split: every client receives
        // every simulated player even though production frames are team-cropped.
        long totalBytesPerSecond = perClientBytesPerSecond * playerCount;
        assertTrue(perClientBytesPerSecond <= 3L * 1024L,
            () -> playerCount + " player per-client traffic was "
                + perClientBytesPerSecond);
        assertTrue(totalBytesPerSecond <= 250L * 1024L,
            () -> playerCount + " player total traffic was "
                + totalBytesPerSecond);
    }

    @Test
    void rejectsOversizedAndExpiredSessionFrames() {
        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeVarLong(1);
        oversized.writeVarInt(SyncPlayerPositionsMessage.MAX_PLAYERS + 1);
        assertThrows(IllegalArgumentException.class,
            () -> SyncPlayerPositionsMessage.decode(oversized));

        FriendlyByteBuf invalidSession = new FriendlyByteBuf(Unpooled.buffer());
        invalidSession.writeVarLong(0);
        invalidSession.writeVarInt(0);
        assertThrows(IllegalArgumentException.class,
            () -> SyncPlayerPositionsMessage.decode(invalidSession));
    }
}
