package com.example.espoints.network;

import com.example.espoints.tactical.TacticalMarkerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 打开嵌入式战术地图时请求当前阵营的完整标点快照。 */
public class RequestTacticalMarkersMessage {
    public static void encode(RequestTacticalMarkersMessage message, FriendlyByteBuf buf) {
    }

    public static RequestTacticalMarkersMessage decode(FriendlyByteBuf buf) {
        return new RequestTacticalMarkersMessage();
    }

    public static void handle(RequestTacticalMarkersMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                TacticalMarkerManager.sendTo(sender);
            }
        });
        context.setPacketHandled(true);
    }
}
