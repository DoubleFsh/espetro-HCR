package com.example.espoints.network;

import com.example.espoints.tactical.ClientTacticalMarkerState;
import com.example.espoints.tactical.TacticalMarker;
import com.example.espoints.tactical.TacticalMarkerType;
import com.example.espoints.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 服务端向客户端同步其阵营可见的战术标点（含 y，供 3D 渲染）。 */
public class SyncTacticalMarkersMessage {
    private static final int MAX_MARKERS = 64;
    private final List<TacticalMarker> markers;

    public SyncTacticalMarkersMessage(List<TacticalMarker> markers) {
        this.markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public static void encode(SyncTacticalMarkersMessage message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.markers.size());
        long now = System.currentTimeMillis();
        if (!message.markers.isEmpty()) {
            buf.writeUtf(message.markers.get(0).team(), 16);
        }
        for (TacticalMarker marker : message.markers) {
            buf.writeUUID(marker.id());
            buf.writeVarInt(marker.type().ordinal());
            buf.writeDouble(marker.x());
            buf.writeDouble(marker.y());
            buf.writeDouble(marker.z());
            buf.writeUUID(marker.ownerId());
            buf.writeUtf(marker.ownerName(), 64);
            buf.writeVarLong(Math.max(0L, now - marker.createdAtMillis()));
            buf.writeVarInt(marker.ownerSquadId());
            buf.writeBoolean(marker.ownerCommander());
        }
    }

    public static SyncTacticalMarkersMessage decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_MARKERS) {
            throw new IllegalArgumentException("Invalid tactical marker count: " + count);
        }
        String team = count == 0 ? "" : buf.readUtf(16);
        List<TacticalMarker> markers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            TacticalMarkerType type = TacticalMarkerType.fromNetworkId(buf.readVarInt());
            double x = PacketValidation.checkedCoordinate(buf.readDouble(), "marker x");
            double y = PacketValidation.checkedCoordinate(buf.readDouble(), "marker y");
            double z = PacketValidation.checkedCoordinate(buf.readDouble(), "marker z");
            UUID ownerId = buf.readUUID();
            String owner = buf.readUtf(64);
            long ageMillis = Math.max(0L, buf.readVarLong());
            int ownerSquadId = buf.readVarInt();
            boolean ownerCommander = buf.readBoolean();
            if (type != null) {
                markers.add(new TacticalMarker(id, type, x, y, z, team, ownerId, owner,
                    System.currentTimeMillis() - ageMillis, ownerSquadId, ownerCommander));
            }
        }
        return new SyncTacticalMarkersMessage(markers);
    }

    public static void handle(SyncTacticalMarkersMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleClient(message.markers);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleClient(List<TacticalMarker> markers) {
        ClientTacticalMarkerState.setMarkers(markers);
        try {
            Class<?> hudClass = Class.forName("com.example.espoints.hud.TacticalMapHUD");
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            hudClass.getMethod("syncTacticalMarkersFromServer", List.class).invoke(hud, markers);
        } catch (ReflectiveOperationException e) {
            ModLogger.syncError("Failed to sync tactical markers to map HUD: " + e.getMessage());
        }
    }
}
