package com.example.espoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 同步玩家位置的网络消息
 */
public class SyncPlayerPositionsMessage {
    private final Map<UUID, PlayerPosition> playerPositions;

    public SyncPlayerPositionsMessage(Map<UUID, PlayerPosition> playerPositions) {
        this(playerPositions, true);
    }

    private SyncPlayerPositionsMessage(Map<UUID, PlayerPosition> playerPositions, boolean copy) {
        this.playerPositions = copy ? Map.copyOf(playerPositions) : playerPositions;
    }

    /**
     * 玩家位置类
     */
    public static class PlayerPosition {
        private final double x;
        private final double y;
        private final double z;
        private final String name;
        private final String teamName;
        private final float yaw;
        private final boolean metadataIncluded;

        public PlayerPosition(double x, double y, double z, String name) {
            this(x, y, z, name, "", 0.0F);
        }

        public PlayerPosition(double x, double y, double z, String name, String teamName) {
            this(x, y, z, name, teamName, 0.0F);
        }

        public PlayerPosition(double x, double y, double z, String name, String teamName, float yaw) {
            this(x, y, z, name, teamName, yaw, true);
        }

        private PlayerPosition(double x, double y, double z, String name, String teamName, float yaw,
                               boolean metadataIncluded) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = Objects.requireNonNullElse(name, "");
            this.teamName = Objects.requireNonNullElse(teamName, "");
            this.yaw = yaw;
            this.metadataIncluded = metadataIncluded;
        }

        /** 创建不重复携带姓名和阵营的高频位置更新。 */
        public static PlayerPosition positionOnly(double x, double y, double z, float yaw) {
            return new PlayerPosition(x, y, z, "", "", yaw, false);
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public String getName() { return name; }
        public String getTeamName() { return teamName; }
        public float getYaw() { return yaw; }
        public boolean hasMetadata() { return metadataIncluded; }

        public PlayerPosition withMetadataFrom(PlayerPosition previous) {
            if (metadataIncluded || previous == null) {
                return this;
            }
            return new PlayerPosition(x, y, z, previous.name, previous.teamName, yaw, true);
        }
    }

    /**
     * 编码消息
     */
    public static void encode(SyncPlayerPositionsMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.playerPositions.size());
        for (Map.Entry<UUID, PlayerPosition> entry : msg.playerPositions.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerPosition pos = entry.getValue();
            buf.writeUUID(uuid);
            buf.writeDouble(pos.getX());
            buf.writeDouble(pos.getZ());
            buf.writeFloat(pos.getYaw());
            buf.writeBoolean(pos.hasMetadata());
            if (pos.hasMetadata()) {
                buf.writeUtf(pos.getName());
                buf.writeUtf(pos.getTeamName());
            }
        }
    }

    /**
     * 解码消息
     */
    public static SyncPlayerPositionsMessage decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Invalid player position count: " + size);
        }
        Map<UUID, PlayerPosition> positions = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            double x = buf.readDouble();
            double z = buf.readDouble();
            float yaw = buf.readFloat();
            boolean metadataIncluded = buf.readBoolean();
            String name = metadataIncluded ? buf.readUtf() : "";
            String teamName = metadataIncluded ? buf.readUtf() : "";
            positions.put(uuid, new PlayerPosition(x, 0.0D, z, name, teamName, yaw, metadataIncluded));
        }
        return new SyncPlayerPositionsMessage(positions, false);
    }

    /**
     * 处理消息
     */
    public static void handle(SyncPlayerPositionsMessage msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // 只在客户端处理
            if (context.getDirection().getReceptionSide().isClient()) {
                // 更新客户端的玩家位置数据
                com.example.espoints.hud.TacticalMapHUD.getInstance().syncPlayerPositionsFromServer(msg.playerPositions);
            }
        });
        context.setPacketHandled(true);
    }

    /**
     * 广播消息给所有玩家
     */
    public static void broadcastToAll(Map<UUID, PlayerPosition> playerPositions) {
        NetworkHandler.INSTANCE.send(
            PacketDistributor.ALL.noArg(),
            new SyncPlayerPositionsMessage(playerPositions)
        );
    }
}
