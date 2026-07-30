package com.example.espoints.network;

import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端请求完整据点总览数据。
 */
public class RequestCapturePointOverviewMessage {
    public static void encode(RequestCapturePointOverviewMessage msg, FriendlyByteBuf buf) {
    }

    public static RequestCapturePointOverviewMessage decode(FriendlyByteBuf buf) {
        return new RequestCapturePointOverviewMessage();
    }

    public static void handle(RequestCapturePointOverviewMessage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) {
                return;
            }
            if (!RequestRateLimiter.allow(
                    sender.getUUID(), "capture_overview",
                    System.currentTimeMillis(), 1_000L)) {
                return;
            }

            try {
                SyncCapturePointOverviewMessage.sendOpenToPlayer(
                    sender,
                    CapturePointManager.getInstance().getOverviewSerializablePoints()
                );
            } catch (Exception e) {
                ModLogger.syncError("Failed to send capture point overview: " + e.getMessage());
                e.printStackTrace();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
