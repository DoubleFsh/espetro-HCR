package com.example.hcrpoints.network;

import com.example.hcrpoints.capturepoint.CapturePointManager;
import com.example.hcrpoints.util.ModLogger;
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
