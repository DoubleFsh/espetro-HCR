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

/** 有界战术标点增量；普通放置/删除不再广播完整 64 项快照。 */
public final class TacticalMarkerDeltaMessage {
    private static final int MAX_CHANGES = 64;
    private static final double FIXED_POINT = 16.0D;

    private final boolean clear;
    private final List<UUID> removals;
    private final List<TacticalMarker> additions;

    public TacticalMarkerDeltaMessage(boolean clear, List<UUID> removals,
                                      List<TacticalMarker> additions) {
        this.clear = clear;
        this.removals = removals == null ? List.of() : List.copyOf(removals);
        this.additions = additions == null ? List.of() : List.copyOf(additions);
    }

    public static TacticalMarkerDeltaMessage add(List<TacticalMarker> additions,
                                                 List<UUID> removals) {
        return new TacticalMarkerDeltaMessage(false, removals, additions);
    }

    public static TacticalMarkerDeltaMessage clearAll() {
        return new TacticalMarkerDeltaMessage(true, List.of(), List.of());
    }

    public static void encode(TacticalMarkerDeltaMessage message, FriendlyByteBuf buf) {
        buf.writeBoolean(message.clear);
        buf.writeVarInt(message.removals.size());
        for (UUID id : message.removals) {
            buf.writeUUID(id);
        }
        buf.writeVarInt(message.additions.size());
        long now = System.currentTimeMillis();
        for (TacticalMarker marker : message.additions) {
            buf.writeUUID(marker.id());
            buf.writeByte(marker.type().ordinal());
            buf.writeInt(toFixed(marker.x()));
            buf.writeInt(toFixed(marker.y()));
            buf.writeInt(toFixed(marker.z()));
            buf.writeUtf(marker.team(), 16);
            buf.writeUUID(marker.ownerId());
            buf.writeUtf(marker.ownerName(), 64);
            buf.writeVarLong(Math.max(0L, now - marker.createdAtMillis()));
            buf.writeVarInt(marker.ownerSquadId() + 1);
            buf.writeBoolean(marker.ownerCommander());
        }
    }

    public static TacticalMarkerDeltaMessage decode(FriendlyByteBuf buf) {
        boolean clear = buf.readBoolean();
        int removeCount = bounded(buf.readVarInt());
        List<UUID> removals = new ArrayList<>(removeCount);
        for (int i = 0; i < removeCount; i++) {
            removals.add(buf.readUUID());
        }
        int addCount = bounded(buf.readVarInt());
        List<TacticalMarker> additions = new ArrayList<>(addCount);
        long now = System.currentTimeMillis();
        for (int i = 0; i < addCount; i++) {
            UUID id = buf.readUUID();
            TacticalMarkerType type = TacticalMarkerType.fromNetworkId(buf.readUnsignedByte());
            double x = buf.readInt() / FIXED_POINT;
            double y = buf.readInt() / FIXED_POINT;
            double z = buf.readInt() / FIXED_POINT;
            String team = buf.readUtf(16);
            UUID ownerId = buf.readUUID();
            String ownerName = buf.readUtf(64);
            long createdAt = now - Math.max(0L, buf.readVarLong());
            int squadId = buf.readVarInt() - 1;
            boolean commander = buf.readBoolean();
            if (type != null) {
                additions.add(new TacticalMarker(id, type, x, y, z, team,
                    ownerId, ownerName, createdAt, squadId, commander));
            }
        }
        return new TacticalMarkerDeltaMessage(clear, removals, additions);
    }

    private static int bounded(int value) {
        if (value < 0 || value > MAX_CHANGES) {
            throw new IllegalArgumentException("Invalid tactical marker delta size: " + value);
        }
        return value;
    }

    private static int toFixed(double coordinate) {
        PacketValidation.checkedCoordinate(coordinate, "marker");
        double scaled = coordinate * FIXED_POINT;
        if (scaled < Integer.MIN_VALUE || scaled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Marker coordinate exceeds fixed-point range");
        }
        return (int) Math.round(scaled);
    }

    public static void handle(TacticalMarkerDeltaMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (!context.getDirection().getReceptionSide().isClient()) {
                return;
            }
            ClientTacticalMarkerState.applyDelta(
                message.clear, message.removals, message.additions);
            syncMapHud(ClientTacticalMarkerState.getMarkers());
        });
        context.setPacketHandled(true);
    }

    /**
     * 网络消息类会在专用服务端被类加载；用反射隔离纯客户端 HUD，
     * 避免 DedicatedServer 上解析 net.minecraft.client 类型。
     */
    private static void syncMapHud(List<TacticalMarker> markers) {
        try {
            Class<?> hudClass = Class.forName("com.example.espoints.hud.TacticalMapHUD");
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            hudClass.getMethod("syncTacticalMarkersFromServer", List.class)
                .invoke(hud, markers);
        } catch (ReflectiveOperationException e) {
            ModLogger.syncError(
                "Failed to apply tactical marker delta to map HUD: " + e.getMessage());
        }
    }
}
