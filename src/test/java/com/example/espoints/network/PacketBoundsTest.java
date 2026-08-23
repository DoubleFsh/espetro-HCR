package com.example.espoints.network;

import com.example.espoints.tile.TacticalMapTileService;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void protocolFourteenTileRequestRetainsGoldenBytesAndBounds() {
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        RequestTacticalMapTileMessage.encode(
            new RequestTacticalMapTileMessage(1L, 2, 3, 4), encoded);
        byte[] bytes = new byte[encoded.readableBytes()];
        encoded.getBytes(0, bytes);
        assertArrayEquals(new byte[] {1, 2, 3, 4}, bytes);

        RequestTacticalMapTileMessage decoded = RequestTacticalMapTileMessage.decode(
            new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
        assertEquals(1L, decoded.session());
        assertEquals(2, decoded.level());
        assertEquals(3, decoded.x());
        assertEquals(4, decoded.y());

        FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
        forged.writeVarLong(1L);
        forged.writeVarInt(0);
        forged.writeVarInt(64);
        forged.writeVarInt(0);
        assertThrows(IllegalArgumentException.class,
            () -> RequestTacticalMapTileMessage.decode(forged));
    }

    @Test
    void configPacketCarriesTacticalMapDescriptor() {
        com.example.espoints.config.TacticalMapJsonConfig config =
            com.example.espoints.config.TacticalMapJsonConfig.createDefault();
        config.topLeftX = -2720;
        config.topLeftZ = -3744;
        config.bottomRightX = 3183;
        config.bottomRightZ = 2975;
        config.initialRange = 512;
        config.minimumRange = 64;
        config.backgroundImage = "map.png";
        config.backgroundImageWidth = 5904;
        config.backgroundImageHeight = 6720;
        TacticalMapTileService.Descriptor descriptor = new TacticalMapTileService.Descriptor(
            1L, "map.png", "a".repeat(64), 5904, 6720, 512, 4);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        SyncTacticalMapConfigMessage.encode(
            new SyncTacticalMapConfigMessage(config, "unit-test", descriptor), encoded);
        SyncTacticalMapConfigMessage decoded = SyncTacticalMapConfigMessage.decode(
            new FriendlyByteBuf(Unpooled.wrappedBuffer(encoded.copy())));
        assertEquals("map.png", decodedConfigBackground(decoded));
        assertEquals(1L, decodedDescriptor(decoded).session());
        assertEquals(5904, decodedDescriptor(decoded).width());
        assertEquals(4, decodedDescriptor(decoded).maxLevel());
        assertTrue(decodedDescriptor(decoded).present());
    }

    private static String decodedConfigBackground(SyncTacticalMapConfigMessage message) {
        try {
            var field = SyncTacticalMapConfigMessage.class.getDeclaredField("config");
            field.setAccessible(true);
            return ((com.example.espoints.config.TacticalMapJsonConfig) field.get(message))
                .backgroundImage;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static TacticalMapTileService.Descriptor decodedDescriptor(
            SyncTacticalMapConfigMessage message) {
        try {
            var field = SyncTacticalMapConfigMessage.class.getDeclaredField("descriptor");
            field.setAccessible(true);
            return (TacticalMapTileService.Descriptor) field.get(message);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }
}
