package com.example.espoints.network;

import com.example.espoints.client.ClientPlayerIdentityState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Compact 0.5-second position frame keyed by session-local unsigned shorts. */
public final class SyncPlayerPositionsMessage {
    public static final int MAX_PLAYERS = 256;
    private static final double FIXED_POINT_SCALE = 8.0D;

    private final long session;
    private final Map<Integer, PlayerPosition> positions;

    public SyncPlayerPositionsMessage(long session, Map<Integer, PlayerPosition> positions) {
        if (session <= 0L || positions == null || positions.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("Invalid tactical position frame");
        }
        this.session = session;
        this.positions = Map.copyOf(positions);
    }

    public long session() {
        return session;
    }

    public Map<Integer, PlayerPosition> positions() {
        return positions;
    }

    public static final class PlayerPosition {
        public static final int NO_SQUAD = -1;
        private final double x;
        private final double y;
        private final double z;
        private final String name;
        private final String teamName;
        private final float yaw;
        private final int squadId;
        private final boolean squadLeader;
        private final boolean commander;

        public PlayerPosition(double x, double y, double z, String name, String teamName,
                              float yaw, int squadId, boolean squadLeader, boolean commander) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name == null ? "" : name;
            this.teamName = teamName == null ? "" : teamName;
            this.yaw = yaw;
            this.squadId = squadId;
            this.squadLeader = squadLeader;
            this.commander = commander;
        }

        public static PlayerPosition positionOnly(double x, double y, double z, float yaw) {
            return new PlayerPosition(x, y, z, "", "", yaw, NO_SQUAD, false, false);
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public String getName() { return name; }
        public String getTeamName() { return teamName; }
        public float getYaw() { return yaw; }
        public int getSquadId() { return squadId; }
        public boolean isSquadLeader() { return squadLeader; }
        public boolean isCommander() { return commander; }
    }

    public static void encode(SyncPlayerPositionsMessage message, FriendlyByteBuf buf) {
        buf.writeVarLong(message.session);
        buf.writeVarInt(message.positions.size());
        for (Map.Entry<Integer, PlayerPosition> entry : message.positions.entrySet()) {
            int shortId = entry.getKey();
            if (shortId <= 0 || shortId > 0xffff) {
                throw new IllegalArgumentException("Invalid tactical player id: " + shortId);
            }
            PlayerPosition position = entry.getValue();
            buf.writeShort(shortId);
            buf.writeInt(toFixed(position.x));
            buf.writeInt(toFixed(position.z));
            buf.writeByte(toPackedYaw(position.yaw));
        }
    }

    public static SyncPlayerPositionsMessage decode(FriendlyByteBuf buf) {
        long session = buf.readVarLong();
        if (session <= 0L) {
            throw new IllegalArgumentException("Invalid tactical position session");
        }
        int size = PacketValidation.checkedCount(
            buf.readVarInt(), MAX_PLAYERS, "player position");
        Map<Integer, PlayerPosition> positions = new HashMap<>(size);
        for (int index = 0; index < size; index++) {
            int shortId = buf.readUnsignedShort();
            if (shortId == 0 || positions.containsKey(shortId)) {
                throw new IllegalArgumentException("Invalid/duplicate tactical player id: " + shortId);
            }
            double x = fromFixed(buf.readInt());
            double z = fromFixed(buf.readInt());
            float yaw = fromPackedYaw(buf.readUnsignedByte());
            positions.put(shortId, PlayerPosition.positionOnly(x, 0.0D, z, yaw));
        }
        return new SyncPlayerPositionsMessage(session, positions);
    }

    public static void handle(SyncPlayerPositionsMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (!context.getDirection().getReceptionSide().isClient()) {
                return;
            }
            Map<UUID, PlayerPosition> resolved =
                ClientPlayerIdentityState.get().resolve(message.session, message.positions);
            if (resolved != null) {
                com.example.espoints.hud.TacticalMapHUD.getInstance()
                    .syncPlayerPositionsFromServer(resolved);
            }
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayers(
            java.util.Collection<? extends ServerPlayer> players,
            long session,
            Map<Integer, PlayerPosition> positions) {
        if (players == null || players.isEmpty()) {
            return;
        }
        SyncPlayerPositionsMessage message =
            new SyncPlayerPositionsMessage(session, positions);
        for (ServerPlayer player : players) {
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player), message);
        }
    }

    public static int toFixed(double coordinate) {
        if (!Double.isFinite(coordinate)
            || coordinate < Integer.MIN_VALUE / FIXED_POINT_SCALE
            || coordinate > Integer.MAX_VALUE / FIXED_POINT_SCALE) {
            throw new IllegalArgumentException("Invalid tactical coordinate: " + coordinate);
        }
        return (int) Math.round(coordinate * FIXED_POINT_SCALE);
    }

    public static double fromFixed(int fixed) {
        return fixed / FIXED_POINT_SCALE;
    }

    public static int toPackedYaw(float yaw) {
        return Mth.floor(Mth.wrapDegrees(yaw) * 256.0F / 360.0F) & 0xff;
    }

    public static float fromPackedYaw(int packed) {
        return Mth.wrapDegrees((packed & 0xff) * 360.0F / 256.0F);
    }
}
