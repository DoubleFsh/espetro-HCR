package com.example.espoints.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 同步可见兵站信息的网络消息。
 */
public class SyncBastionsMessage {
    private static final String TACTICAL_MAP_HUD_CLASS = "com.example.espoints.hud.TacticalMapHUD";
    private final List<BastionInfo> bastions;
    private final List<BaseInfo> bases;
    private final List<VehicleSupplyStationInfo> vehicleSupplyStations;

    public SyncBastionsMessage(List<BastionInfo> bastions) {
        this(bastions, List.of());
    }

    public SyncBastionsMessage(List<BastionInfo> bastions, List<BaseInfo> bases) {
        this(bastions, bases, List.of());
    }

    public SyncBastionsMessage(List<BastionInfo> bastions, List<BaseInfo> bases,
                               List<VehicleSupplyStationInfo> vehicleSupplyStations) {
        this.bastions = List.copyOf(bastions);
        this.bases = List.copyOf(bases);
        this.vehicleSupplyStations = List.copyOf(vehicleSupplyStations);
    }

    public static class BastionInfo {
        private final String name;
        private final String team;
        private final BlockPos pos;
        private final String type;
        private final int construction;
        private final int ammunition;
        private final boolean operational;
        private final double buildRadius;
        private final double exclusionRadius;
        private final long nextWaveSeconds;

        public BastionInfo(String name, String team, BlockPos pos) {
            this(name, team, pos, "FOB", 0, 0, true, 150.0, 400.0, 0L);
        }

        public BastionInfo(String name, String team, BlockPos pos, String type,
                           int construction, int ammunition, boolean operational,
                           double buildRadius, double exclusionRadius, long nextWaveSeconds) {
            this.name = name;
            this.team = team;
            this.pos = pos;
            this.type = type == null ? "FOB" : type;
            this.construction = Math.max(0, construction);
            this.ammunition = Math.max(0, ammunition);
            this.operational = operational;
            this.buildRadius = Math.max(0.0, buildRadius);
            this.exclusionRadius = Math.max(0.0, exclusionRadius);
            this.nextWaveSeconds = Math.max(0L, nextWaveSeconds);
        }

        public String getName() {
            return name;
        }

        public String getTeam() {
            return team;
        }

        public BlockPos getPos() {
            return pos;
        }

        public String getType() {
            return type;
        }

        public boolean isRally() {
            return "RALLY".equalsIgnoreCase(type);
        }

        public int getConstruction() {
            return construction;
        }

        public int getAmmunition() {
            return ammunition;
        }

        public boolean isOperational() {
            return operational;
        }

        public double getBuildRadius() {
            return buildRadius;
        }

        public double getExclusionRadius() {
            return exclusionRadius;
        }

        public long getNextWaveSeconds() {
            return nextWaveSeconds;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof BastionInfo other)) return false;
            return construction == other.construction
                && ammunition == other.ammunition
                && operational == other.operational
                && Double.compare(buildRadius, other.buildRadius) == 0
                && Double.compare(exclusionRadius, other.exclusionRadius) == 0
                && nextWaveSeconds == other.nextWaveSeconds
                && name.equals(other.name) && team.equals(other.team)
                && pos.equals(other.pos) && type.equals(other.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, team, pos, type, construction, ammunition,
                operational, buildRadius, exclusionRadius, nextWaveSeconds);
        }
    }

    public static class BaseInfo {
        private final String name;
        private final String team;
        private final BlockPos pos;
        private final float yaw;

        public BaseInfo(String name, String team, BlockPos pos, float yaw) {
            this.name = name;
            this.team = team;
            this.pos = pos;
            this.yaw = yaw;
        }

        public String getName() {
            return name;
        }

        public String getTeam() {
            return team;
        }

        public BlockPos getPos() {
            return pos;
        }

        public float getYaw() {
            return yaw;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof BaseInfo other)) return false;
            return Float.compare(yaw, other.yaw) == 0
                && name.equals(other.name) && team.equals(other.team) && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, team, pos, yaw);
        }
    }

    public static class VehicleSupplyStationInfo {
        private final String name;
        private final String team;
        private final BlockPos pos;

        public VehicleSupplyStationInfo(String name, String team, BlockPos pos) {
            this.name = name;
            this.team = team;
            this.pos = pos;
        }

        public String getName() {
            return name;
        }

        public String getTeam() {
            return team;
        }

        public BlockPos getPos() {
            return pos;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof VehicleSupplyStationInfo other)) return false;
            return name.equals(other.name) && team.equals(other.team) && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, team, pos);
        }
    }

    public static void encode(SyncBastionsMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.bastions.size());
        for (BastionInfo bastion : msg.bastions) {
            buf.writeUtf(bastion.getName());
            buf.writeUtf(bastion.getTeam());
            buf.writeBlockPos(bastion.getPos());
            buf.writeUtf(bastion.getType());
            buf.writeVarInt(bastion.getConstruction());
            buf.writeVarInt(bastion.getAmmunition());
            buf.writeBoolean(bastion.isOperational());
            buf.writeDouble(bastion.getBuildRadius());
            buf.writeDouble(bastion.getExclusionRadius());
            buf.writeVarLong(bastion.getNextWaveSeconds());
        }

        buf.writeVarInt(msg.bases.size());
        for (BaseInfo base : msg.bases) {
            buf.writeUtf(base.getName());
            buf.writeUtf(base.getTeam());
            buf.writeBlockPos(base.getPos());
            buf.writeFloat(base.getYaw());
        }

        buf.writeVarInt(msg.vehicleSupplyStations.size());
        for (VehicleSupplyStationInfo station : msg.vehicleSupplyStations) {
            buf.writeUtf(station.getName());
            buf.writeUtf(station.getTeam());
            buf.writeBlockPos(station.getPos());
        }
    }

    public static SyncBastionsMessage decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Invalid bastion count: " + size);
        }
        List<BastionInfo> bastions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = buf.readUtf();
            String team = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            String type = buf.readUtf();
            int construction = buf.readVarInt();
            int ammunition = buf.readVarInt();
            boolean operational = buf.readBoolean();
            double buildRadius = buf.readDouble();
            double exclusionRadius = buf.readDouble();
            long nextWaveSeconds = buf.readVarLong();
            bastions.add(new BastionInfo(name, team, pos, type, construction, ammunition,
                operational, buildRadius, exclusionRadius, nextWaveSeconds));
        }

        int baseSize = buf.readVarInt();
        if (baseSize < 0 || baseSize > 4096) {
            throw new IllegalArgumentException("Invalid base count: " + baseSize);
        }
        List<BaseInfo> bases = new ArrayList<>(baseSize);
        for (int i = 0; i < baseSize; i++) {
            String name = buf.readUtf();
            String team = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            float yaw = buf.readFloat();
            bases.add(new BaseInfo(name, team, pos, yaw));
        }

        int stationSize = buf.readVarInt();
        if (stationSize < 0 || stationSize > 4096) {
            throw new IllegalArgumentException("Invalid vehicle supply station count: " + stationSize);
        }
        List<VehicleSupplyStationInfo> stations = new ArrayList<>(stationSize);
        for (int i = 0; i < stationSize; i++) {
            String name = buf.readUtf();
            String team = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            stations.add(new VehicleSupplyStationInfo(name, team, pos));
        }
        return new SyncBastionsMessage(bastions, bases, stations);
    }

    public static void handle(SyncBastionsMessage msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleOnClient(msg.bastions, msg.bases, msg.vehicleSupplyStations);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleOnClient(List<BastionInfo> bastions, List<BaseInfo> bases,
                                       List<VehicleSupplyStationInfo> vehicleSupplyStations) {
        try {
            Class<?> hudClass = Class.forName(TACTICAL_MAP_HUD_CLASS);
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            hudClass.getMethod("syncBastionsFromServer", List.class, List.class, List.class)
                .invoke(hud, bastions, bases, vehicleSupplyStations);
        } catch (ReflectiveOperationException e) {
            // 客户端 HUD 不可用时忽略兵站显示同步，避免影响主功能。
        }
    }

    public static void sendToPlayer(ServerPlayer player, List<BastionInfo> bastions) {
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncBastionsMessage(bastions)
        );
    }

    public static void sendToPlayer(ServerPlayer player, List<BastionInfo> bastions, List<BaseInfo> bases) {
        sendToPlayer(player, bastions, bases, List.of());
    }

    public static void sendToPlayer(ServerPlayer player, List<BastionInfo> bastions, List<BaseInfo> bases,
                                    List<VehicleSupplyStationInfo> vehicleSupplyStations) {
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncBastionsMessage(bastions, bases, vehicleSupplyStations)
        );
    }
}
