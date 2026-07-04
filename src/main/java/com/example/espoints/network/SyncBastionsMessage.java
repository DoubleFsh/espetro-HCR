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

    public SyncBastionsMessage(List<BastionInfo> bastions) {
        this(bastions, List.of());
    }

    public SyncBastionsMessage(List<BastionInfo> bastions, List<BaseInfo> bases) {
        this.bastions = List.copyOf(bastions);
        this.bases = List.copyOf(bases);
    }

    public static class BastionInfo {
        private final String name;
        private final String team;
        private final BlockPos pos;

        public BastionInfo(String name, String team, BlockPos pos) {
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
            if (!(object instanceof BastionInfo other)) return false;
            return name.equals(other.name) && team.equals(other.team) && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, team, pos);
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

    public static void encode(SyncBastionsMessage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.bastions.size());
        for (BastionInfo bastion : msg.bastions) {
            buf.writeUtf(bastion.getName());
            buf.writeUtf(bastion.getTeam());
            buf.writeBlockPos(bastion.getPos());
        }

        buf.writeVarInt(msg.bases.size());
        for (BaseInfo base : msg.bases) {
            buf.writeUtf(base.getName());
            buf.writeUtf(base.getTeam());
            buf.writeBlockPos(base.getPos());
            buf.writeFloat(base.getYaw());
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
            bastions.add(new BastionInfo(name, team, pos));
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
        return new SyncBastionsMessage(bastions, bases);
    }

    public static void handle(SyncBastionsMessage msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleOnClient(msg.bastions, msg.bases);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleOnClient(List<BastionInfo> bastions, List<BaseInfo> bases) {
        try {
            Class<?> hudClass = Class.forName(TACTICAL_MAP_HUD_CLASS);
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            hudClass.getMethod("syncBastionsFromServer", List.class, List.class).invoke(hud, bastions, bases);
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
        NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncBastionsMessage(bastions, bases)
        );
    }
}
