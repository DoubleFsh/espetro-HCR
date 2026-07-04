package com.example.espoints.network;

import com.example.espoints.tactical.TacticalMarkerManager;
import com.example.espoints.tactical.TacticalMarkerType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端放置请求；权限、阵营和距离均由服务端重新校验。 */
public class PlaceTacticalMarkerMessage {
    private final int typeId;
    private final double x;
    private final double z;

    public PlaceTacticalMarkerMessage(TacticalMarkerType type, double x, double z) {
        this(type.ordinal(), x, z);
    }

    private PlaceTacticalMarkerMessage(int typeId, double x, double z) {
        this.typeId = typeId;
        this.x = x;
        this.z = z;
    }

    public static void encode(PlaceTacticalMarkerMessage message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.typeId);
        buf.writeDouble(message.x);
        buf.writeDouble(message.z);
    }

    public static PlaceTacticalMarkerMessage decode(FriendlyByteBuf buf) {
        return new PlaceTacticalMarkerMessage(buf.readVarInt(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(PlaceTacticalMarkerMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null) {
                TacticalMarkerManager.place(sender,
                    TacticalMarkerType.fromNetworkId(message.typeId), message.x, message.z);
            }
        });
        context.setPacketHandled(true);
    }
}
