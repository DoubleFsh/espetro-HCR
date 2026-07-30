package com.example.espoints.network;

import com.example.espoints.capturepoint.CapturePointManager;
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

    public TacticalMapSubscriptionMessage(boolean active) {
        this.active = active;
    }

    public static void encode(TacticalMapSubscriptionMessage message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.active);
    }

    public static TacticalMapSubscriptionMessage decode(FriendlyByteBuf buffer) {
        return new TacticalMapSubscriptionMessage(buffer.readBoolean());
    }

    public static void handle(TacticalMapSubscriptionMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null && RequestRateLimiter.allow(
                sender.getUUID(), "map_subscription",
                System.currentTimeMillis(), 250L)) {
            CapturePointManager.getInstance().setTacticalMapSubscription(sender, message.active);
        }
        context.setPacketHandled(true);
    }
}
