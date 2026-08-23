package com.example.espoints.network;

import com.example.espoints.tile.TacticalMapTileService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** One encoded PNG tile. */
public final class SyncTacticalMapTileMessage {
    private static final String CLIENT_CACHE_CLASS =
        "com.example.espoints.client.ClientTacticalMapTileCache";
    private final long session;
    private final int level;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final byte[] bytes;

    private SyncTacticalMapTileMessage(long session, int level, int x, int y,
                                      int width, int height, byte[] bytes) {
        this.session = session;
        this.level = level;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.bytes = bytes;
    }

    public static void encode(SyncTacticalMapTileMessage message, FriendlyByteBuf buf) {
        buf.writeVarLong(message.session);
        buf.writeVarInt(message.level);
        buf.writeVarInt(message.x);
        buf.writeVarInt(message.y);
        buf.writeVarInt(message.width);
        buf.writeVarInt(message.height);
        buf.writeByteArray(message.bytes);
    }

    public static SyncTacticalMapTileMessage decode(FriendlyByteBuf buf) {
        long session = buf.readVarLong();
        int level = buf.readVarInt();
        int x = buf.readVarInt();
        int y = buf.readVarInt();
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        byte[] bytes = buf.readByteArray(TacticalMapTileService.MAX_ENCODED_TILE_BYTES);
        if (session <= 0L || level < 0
            || level >= com.example.espoints.tile.TacticalMapPyramidLayout.MAX_LEVELS
            || x < 0 || x >= 64 || y < 0 || y >= 64
            || width <= 0 || width > 512 || height <= 0 || height > 512
            || bytes.length < 8 || !isPng(bytes)) {
            throw new IllegalArgumentException("Invalid tactical tile payload");
        }
        return new SyncTacticalMapTileMessage(
            session, level, x, y, width, height, bytes);
    }

    private static boolean isPng(byte[] bytes) {
        return bytes[0] == (byte) 0x89 && bytes[1] == 0x50
            && bytes[2] == 0x4e && bytes[3] == 0x47
            && bytes[4] == 0x0d && bytes[5] == 0x0a
            && bytes[6] == 0x1a && bytes[7] == 0x0a;
    }

    public static void handle(SyncTacticalMapTileMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> runOnClientThread(message)));
        context.setPacketHandled(true);
    }

    private static void runOnClientThread(SyncTacticalMapTileMessage message) {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            minecraft.getMethod("execute", Runnable.class)
                .invoke(instance, (Runnable) () -> applyOnClient(message));
        } catch (ReflectiveOperationException error) {
            applyOnClient(message);
        }
    }

    private static void applyOnClient(SyncTacticalMapTileMessage message) {
        try {
            Class<?> type = Class.forName(CLIENT_CACHE_CLASS);
            Object cache = type.getMethod("get").invoke(null);
            type.getMethod("accept", long.class, int.class, int.class, int.class,
                    int.class, int.class, byte[].class)
                .invoke(cache, message.session, message.level, message.x, message.y,
                    message.width, message.height, message.bytes);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to apply tactical tile on client", error);
        }
    }

    public static void sendToPlayer(ServerPlayer player, long session,
                                    int level, int x, int y, byte[] bytes) {
        TacticalMapTileService service = TacticalMapTileService.get();
        if (service.descriptor().session() != session) {
            return;
        }
        int width = service.tileWidth(level, x);
        int height = service.tileHeight(level, y);
        if (width <= 0 || height <= 0 || bytes == null
            || bytes.length <= 0 || bytes.length > TacticalMapTileService.MAX_ENCODED_TILE_BYTES) {
            return;
        }
        SyncTacticalMapTileMessage message = new SyncTacticalMapTileMessage(
            session, level, x, y, width, height, bytes);
        if (player.connection != null && player.connection.connection.isMemoryConnection()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> runOnClientThread(message));
        }
        NetworkHandler.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> player), message);
    }
}
