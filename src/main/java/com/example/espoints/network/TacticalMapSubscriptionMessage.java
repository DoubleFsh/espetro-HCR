package com.example.espoints.network;

import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.tile.TacticalMapTileService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端战术地图可见性订阅。
 *
 * <p>客户端在地图持续渲染时发送低频心跳。服务端只向仍有有效订阅的玩家发送
 * 高频位置与兵站快照，避免地图关闭时继续产生广播流量。</p>
 */
public final class TacticalMapSubscriptionMessage {
    private final boolean active;
    private final double minX;
    private final double minY;
    private final double maxX;
    private final double maxY;
    private final int screenWidth;
    private final int screenHeight;

    public TacticalMapSubscriptionMessage(boolean active) {
        this(active, 0.0D, 0.0D, 1.0D, 1.0D, 256, 256);
    }

    public TacticalMapSubscriptionMessage(boolean active,
                                          double minX, double minY,
                                          double maxX, double maxY,
                                          int screenWidth, int screenHeight) {
        this.active = active;
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public static void encode(TacticalMapSubscriptionMessage message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.active);
        if (!message.active) {
            return;
        }
        buffer.writeDouble(message.minX);
        buffer.writeDouble(message.minY);
        buffer.writeDouble(message.maxX);
        buffer.writeDouble(message.maxY);
        buffer.writeVarInt(Math.max(1, message.screenWidth));
        buffer.writeVarInt(Math.max(1, message.screenHeight));
    }

    public static TacticalMapSubscriptionMessage decode(FriendlyByteBuf buffer) {
        boolean active = buffer.readBoolean();
        if (!active) {
            return new TacticalMapSubscriptionMessage(false);
        }
        return new TacticalMapSubscriptionMessage(
            true,
            buffer.readDouble(), buffer.readDouble(),
            buffer.readDouble(), buffer.readDouble(),
            buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(TacticalMapSubscriptionMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && RequestRateLimiter.allow(
                sender.getUUID(), "map_subscription",
                System.currentTimeMillis(), 250L)) {
            CapturePointManager.getInstance().setTacticalMapSubscription(sender, message.active);
            if (message.active) {
                SyncTacticalMapBackgroundMessage.sendDescriptorOnly(sender);
                TacticalMapTileService.get().updatePlayerViewport(
                    sender.getUUID(),
                    message.minX, message.minY, message.maxX, message.maxY,
                    message.screenWidth, message.screenHeight);
                TacticalMapTileService.get().enqueueViewport(sender.getUUID());
            } else {
                TacticalMapTileService.get().removePlayer(sender.getUUID());
            }
        }
        context.setPacketHandled(true);
    }
}
