package com.example.espoints.network;

import com.example.espoints.tactical.TacticalMarkerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 请求取消一个由当前玩家本人放置的战术标点。 */
public class RemoveTacticalMarkerMessage {
    private final UUID markerId;

    public RemoveTacticalMarkerMessage(UUID markerId) {
        this.markerId = markerId;
    }

    public static void encode(RemoveTacticalMarkerMessage message, FriendlyByteBuf buf) {
        buf.writeUUID(message.markerId);
    }

    public static RemoveTacticalMarkerMessage decode(FriendlyByteBuf buf) {
        return new RemoveTacticalMarkerMessage(buf.readUUID());
    }

    public static void handle(RemoveTacticalMarkerMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                TacticalMarkerManager.removeOwn(sender, message.markerId);
            }
        });
        context.setPacketHandled(true);
    }
}
