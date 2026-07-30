package com.example.espoints.network;

import com.example.espoints.client.ClientTacticalMapTileCache;
import com.example.espoints.tile.TacticalMapPyramidLayout;
import com.example.espoints.tile.TacticalMapTileService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Small map descriptor; full PNG bytes are never sent or decoded on open. */
public final class SyncTacticalMapBackgroundMessage {
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
                ClientTacticalMapTileCache.get().applyDescriptor(message.descriptor);
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player) {
        TacticalMapTileService.Descriptor descriptor =
            TacticalMapTileService.get().descriptor();
        NetworkHandler.INSTANCE.send(
            PacketDistributor.PLAYER.with(() -> player),
            new SyncTacticalMapBackgroundMessage(descriptor));
        if (descriptor.present()) {
            TacticalMapTileService.get()
                .request(descriptor.session(), descriptor.maxLevel(), 0, 0)
                .thenAccept(bytes -> {
                    var server = player.getServer();
                    if (server != null) {
                        server.execute(() -> {
                            TacticalMapTileService service = TacticalMapTileService.get();
                            if (service.descriptor().session() == descriptor.session()
                                && service.allowTransfer(player.getUUID(), bytes.length)) {
                                SyncTacticalMapTileMessage.sendToPlayer(
                                    player, descriptor.session(), descriptor.maxLevel(),
                                    0, 0, bytes);
                            }
                        });
                    }
                })
                .exceptionally(error -> null);
        }
    }

    public static void broadcastToAll() {
        NetworkHandler.INSTANCE.send(
            PacketDistributor.ALL.noArg(),
            new SyncTacticalMapBackgroundMessage(
                TacticalMapTileService.get().descriptor()));
    }
}
