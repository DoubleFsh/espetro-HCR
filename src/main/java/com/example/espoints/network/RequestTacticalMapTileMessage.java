package com.example.espoints.network;

import com.example.espoints.tile.TacticalMapTileService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Validated/rate-limited visible-tile request. */
public record RequestTacticalMapTileMessage(long session, int level, int x, int y) {
    private static final Map<UUID, RequestWindow> REQUESTS = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_SECOND = 64;

    public static void encode(RequestTacticalMapTileMessage message, FriendlyByteBuf buf) {
        buf.writeVarLong(message.session);
        buf.writeVarInt(message.level);
        buf.writeVarInt(message.x);
        buf.writeVarInt(message.y);
    }

    public static RequestTacticalMapTileMessage decode(FriendlyByteBuf buf) {
        long session = buf.readVarLong();
        int level = buf.readVarInt();
        int x = buf.readVarInt();
        int y = buf.readVarInt();
        if (session <= 0L || level < 0
            || level >= com.example.espoints.tile.TacticalMapPyramidLayout.MAX_LEVELS
            || x < 0 || x >= 64 || y < 0 || y >= 64) {
            throw new IllegalArgumentException("Invalid tactical tile request");
        }
        return new RequestTacticalMapTileMessage(session, level, x, y);
    }

    public static void handle(RequestTacticalMapTileMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && allow(sender.getUUID(), System.currentTimeMillis())) {
            TacticalMapTileService.get().enqueue(
                sender.getUUID(), message.session, message.level, message.x, message.y);
        }
        context.setPacketHandled(true);
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) {
            REQUESTS.remove(playerId);
            TacticalMapTileService.get().removePlayer(playerId);
        }
    }

    public static void clearAll() {
        REQUESTS.clear();
    }

    private static boolean allow(UUID playerId, long now) {
        RequestWindow window = REQUESTS.computeIfAbsent(playerId, ignored -> new RequestWindow());
        synchronized (window) {
            if (now < window.startedAt || now - window.startedAt >= 1000L) {
                window.startedAt = now;
                window.count = 0;
            }
            return ++window.count <= MAX_REQUESTS_PER_SECOND;
        }
    }

    private static final class RequestWindow {
        private long startedAt;
        private int count;
    }
}
