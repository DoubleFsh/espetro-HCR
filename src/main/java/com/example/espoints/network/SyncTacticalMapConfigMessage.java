package com.example.espoints.network;

import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Syncs the active Espetro map's frozen tactical configuration to clients.
 */
public class SyncTacticalMapConfigMessage {
    private static final String TACTICAL_MAP_HUD_CLASS = "com.example.espoints.hud.TacticalMapHUD";

    private final TacticalMapJsonConfig config;
    private final String source;

    public SyncTacticalMapConfigMessage() {
        this(TacticalMapJsonConfig.getInstance().copy(), TacticalMapJsonConfig.getInstance().getSource());
    }

    public SyncTacticalMapConfigMessage(TacticalMapJsonConfig config, String source) {
        this.config = config == null ? TacticalMapJsonConfig.createDefault() : config.copy();
        this.source = source == null ? "EsWorld EsConfig" : source;
    }

    public SyncTacticalMapConfigMessage(FriendlyByteBuf buf) {
        TacticalMapJsonConfig decoded = TacticalMapJsonConfig.createDefault();
        decoded.topLeftX = buf.readInt();
        decoded.topLeftZ = buf.readInt();
        decoded.bottomRightX = buf.readInt();
        decoded.bottomRightZ = buf.readInt();
        decoded.initialRange = buf.readInt();
        decoded.minimumRange = buf.readInt();
        decoded.backgroundImage = buf.readUtf(256);
        decoded.backgroundImageWidth = buf.readInt();
        decoded.backgroundImageHeight = buf.readInt();
        decoded.showGrid = buf.readBoolean();
        decoded.showLabels = buf.readBoolean();
        decoded.tacticalMarkerDurationSeconds = buf.readVarInt();
        decoded.tacticalMarkerFadeSeconds = buf.readVarInt();
        decoded.tacticalMarkerMaxRenderDistance = buf.readVarInt();
        validate(decoded);
        this.config = decoded;
        this.source = buf.readUtf(256);
    }

    public static void encode(SyncTacticalMapConfigMessage msg, FriendlyByteBuf buf) {
        TacticalMapJsonConfig config = msg.config;
        validate(config);
        buf.writeInt(config.topLeftX);
        buf.writeInt(config.topLeftZ);
        buf.writeInt(config.bottomRightX);
        buf.writeInt(config.bottomRightZ);
        buf.writeInt(config.initialRange);
        buf.writeInt(config.minimumRange);
        buf.writeUtf(config.backgroundImage == null ? "" : config.backgroundImage, 256);
        buf.writeInt(config.backgroundImageWidth);
        buf.writeInt(config.backgroundImageHeight);
        buf.writeBoolean(config.showGrid);
        buf.writeBoolean(config.showLabels);
        buf.writeVarInt(config.tacticalMarkerDurationSeconds);
        buf.writeVarInt(config.tacticalMarkerFadeSeconds);
        buf.writeVarInt(config.tacticalMarkerMaxRenderDistance);
        buf.writeUtf(msg.source, 256);
    }

    public static SyncTacticalMapConfigMessage decode(FriendlyByteBuf buf) {
        return new SyncTacticalMapConfigMessage(buf);
    }

    private static void validate(TacticalMapJsonConfig config) {
        if (config.bottomRightX <= config.topLeftX
            || config.bottomRightZ <= config.topLeftZ
            || config.initialRange <= 0 || config.initialRange > 60_000_000
            || config.minimumRange <= 0 || config.minimumRange > config.initialRange
            || config.backgroundImageWidth < 0 || config.backgroundImageWidth > 32_768
            || config.backgroundImageHeight < 0 || config.backgroundImageHeight > 32_768
            || config.tacticalMarkerDurationSeconds <= 0
            || config.tacticalMarkerDurationSeconds > 86_400
            || config.tacticalMarkerFadeSeconds <= 0
            || config.tacticalMarkerFadeSeconds > 86_400
            || config.tacticalMarkerMaxRenderDistance < 0
            || config.tacticalMarkerMaxRenderDistance > 1_000_000) {
            throw new IllegalArgumentException("Invalid tactical map configuration payload");
        }
    }

    public static void handle(SyncTacticalMapConfigMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (!context.getDirection().getReceptionSide().isClient()) {
                return;
            }

            TacticalMapJsonConfig.apply(msg.config, "server map: " + msg.source);
            notifyHudConfigSynced();
            ModLogger.debug("客户端战术地图配置已同步");
        });
        context.setPacketHandled(true);
    }

    private static void notifyHudConfigSynced() {
        try {
            Class<?> hudClass = Class.forName(TACTICAL_MAP_HUD_CLASS);
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            hudClass.getMethod("onTacticalMapConfigSynced").invoke(hud);
        } catch (ReflectiveOperationException e) {
            // 客户端 HUD 不可用时忽略，配置本身已经更新。
        }
    }

    public static void sendToPlayer(ServerPlayer player) {
        try {
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SyncTacticalMapConfigMessage()
            );
        } catch (Exception e) {
            ModLogger.error("向玩家发送战术地图配置同步消息失败: " + e.getMessage());
        }
    }

    public static void broadcastToAll() {
        try {
            NetworkHandler.INSTANCE.send(
                PacketDistributor.ALL.noArg(),
                new SyncTacticalMapConfigMessage()
            );
            ModLogger.debug("已向所有玩家广播战术地图配置同步消息");
        } catch (Exception e) {
            ModLogger.error("广播战术地图配置同步消息失败: " + e.getMessage());
        }
    }
}
