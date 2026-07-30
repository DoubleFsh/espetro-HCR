package com.example.espoints.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketBoundsTest {
    @Test
    void rejectsOversizedCaptureListsBeforeAllocatingEntries() {
        FriendlyByteBuf capture = new FriendlyByteBuf(Unpooled.buffer());
        capture.writeVarInt(SyncCapturePointsMessage.MAX_CAPTURE_POINTS + 1);
        assertThrows(IllegalArgumentException.class,
            () -> SyncCapturePointsMessage.decode(capture));

        FriendlyByteBuf overview = new FriendlyByteBuf(Unpooled.buffer());
        overview.writeBoolean(false);
        overview.writeVarInt(SyncCapturePointsMessage.MAX_CAPTURE_POINTS + 1);
        assertThrows(IllegalArgumentException.class,
            () -> SyncCapturePointOverviewMessage.decode(overview));
    }

    @Test
    void rejectsNonFiniteAndOutOfWorldCoordinates() {
        assertThrows(IllegalArgumentException.class,
            () -> PacketValidation.checkedCoordinate(Double.NaN, "x"));
        assertThrows(IllegalArgumentException.class,
            () -> PacketValidation.checkedCoordinate(
                PacketValidation.MAX_WORLD_COORDINATE + 1, "x"));

        FriendlyByteBuf marker = new FriendlyByteBuf(Unpooled.buffer());
        marker.writeVarInt(0);
        marker.writeDouble(Double.POSITIVE_INFINITY);
        marker.writeDouble(64);
        marker.writeDouble(0);
        assertThrows(IllegalArgumentException.class,
            () -> PlaceTacticalMarkerMessage.decode(marker));
    }

    @Test
    void rejectsForgedTileDescriptorDimensions() {
        FriendlyByteBuf descriptor = new FriendlyByteBuf(Unpooled.buffer());
        descriptor.writeBoolean(true);
        descriptor.writeVarLong(1);
        descriptor.writeUtf("map.png", 256);
        descriptor.writeUtf("0".repeat(64), 64);
        descriptor.writeVarInt(40_000);
        descriptor.writeVarInt(1);
        descriptor.writeVarInt(512);
        descriptor.writeVarInt(0);
        assertThrows(IllegalArgumentException.class,
            () -> SyncTacticalMapBackgroundMessage.decode(descriptor));
    }
}
