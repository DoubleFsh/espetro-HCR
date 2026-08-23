package com.example.espoints.network;

import com.example.espoints.tile.TacticalMapPyramidLayout;
import com.example.espoints.tile.TacticalMapTileService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Small map descriptor; full PNG bytes are never sent or decoded on open. */
public final class SyncTacticalMapBackgroundMessage {
    private static final String CLIENT_CACHE_CLASS =
        "com.example.espoints.client.ClientTacticalMapTileCache";
    private final TacticalMapTileService.Descriptor descriptor;

    private SyncTacticalMapBackgroundMessage(TacticalMapTileService.Descriptor descriptor) {
        this.descriptor = descriptor == null
            ? TacticalMapTileService.Descriptor.EMPTY
            : descriptor;
    }

    public static void encode(SyncTacticalMapBackgroundMessage message, FriendlyByteBuf buf) {
        TacticalMapTileService.Descriptor descriptor = message.descriptor;
        buf.writeBoolean(descriptor.present());
        if (!descriptor.present()) {
            return;
        }
        buf.writeVarLong(descriptor.session());
        buf.writeUtf(descriptor.imagePath(), 256);
        buf.writeUtf(descriptor.sha256(), 64);
        buf.writeVarInt(descriptor.width());
        buf.writeVarInt(descriptor.height());
        buf.writeVarInt(descriptor.tileSize());
        buf.writeVarInt(descriptor.maxLevel());
    }

    public static SyncTacticalMapBackgroundMessage decode(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return new SyncTacticalMapBackgroundMessage(
                TacticalMapTileService.Descriptor.EMPTY);
        }
        long session = buf.readVarLong();
        String imagePath = buf.readUtf(256);
        String sha256 = buf.readUtf(64);
        int width = buf.readVarInt();
        int height = buf.readVarInt();
        int tileSize = buf.readVarInt();
        int maxLevel = buf.readVarInt();
        if (session <= 0L || !sha256.matches("[0-9a-f]{64}")
            || tileSize != TacticalMapPyramidLayout.TILE_SIZE
            || maxLevel < 0 || maxLevel >= TacticalMapPyramidLayout.MAX_LEVELS) {
            throw new IllegalArgumentException("Invalid tactical map descriptor");
        }
        TacticalMapPyramidLayout layout = new TacticalMapPyramidLayout(width, height);
        if (layout.maxLevel() != maxLevel) {
            throw new IllegalArgumentException("Inconsistent tactical map descriptor");
        }
        return new SyncTacticalMapBackgroundMessage(
            new TacticalMapTileService.Descriptor(
                session, imagePath, sha256, width, height, tileSize, maxLevel));
    }

    public static void handle(SyncTacticalMapBackgroundMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                applyOnClient(message.descriptor);
            }
        });
        context.setPacketHandled(true);
    }

    private static void applyOnClient(TacticalMapTileService.Descriptor descriptor) {
        try {
            Class<?> type = Class.forName(CLIENT_CACHE_CLASS);
            Object cache = type.getMethod("get").invoke(null);
            type.getMethod("applyDescriptor", TacticalMapTileService.Descriptor.class)
                .invoke(cache, descriptor);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                "Unable to apply tactical map descriptor on client", error);
        }
    }

    public static void applyDescriptorOnClient(TacticalMapTileService.Descriptor descriptor) {
        applyOnClient(descriptor);
    }

    public static void sendDescriptorOnly(ServerPlayer player) {
        TacticalMapTileService.Descriptor descriptor =
            TacticalMapTileService.get().descriptor();
        NetworkHandler.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncTacticalMapBackgroundMessage(descriptor));
        if (descriptor.present()) {
            com.example.espoints.ESPointsMod.LOGGER.info(
                "已向 {} 发送战术地图 descriptor session={} {}x{} preview={}",
                player.getGameProfile().getName(), descriptor.session(),
                descriptor.width(), descriptor.height(), descriptor.maxLevel());
        }
    }

    public static void sendToPlayer(ServerPlayer player) {
        sendDescriptorOnly(player);
        TacticalMapTileService.Descriptor descriptor =
            TacticalMapTileService.get().descriptor();
        if (player != null && descriptor.present()) {
            TacticalMapTileService.get().enqueuePreviewOnce(player.getUUID());
        }
    }

    public static void broadcastToAll() {
        TacticalMapTileService.Descriptor descriptor =
            TacticalMapTileService.get().descriptor();
        // PacketDistributor.ALL 与战术地图 JSON 配置走同一条已验证可达的本地/远程通道。
        NetworkHandler.INSTANCE.send(
            PacketDistributor.ALL.noArg(),
            new SyncTacticalMapBackgroundMessage(descriptor));
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null || !descriptor.present()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            TacticalMapTileService.get().enqueuePreviewOnce(player.getUUID());
        }
    }
}
