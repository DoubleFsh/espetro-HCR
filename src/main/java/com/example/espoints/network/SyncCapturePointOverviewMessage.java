package com.example.espoints.network;

import com.example.espoints.capturepoint.CapturePoint;
import com.example.espoints.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 同步完整据点总览数据，不套用战术地图的攻守可见性过滤。
 */
public class SyncCapturePointOverviewMessage {
    private static final String OVERVIEW_SCREEN_CLASS = "com.example.espoints.client.gui.CapturePointDetailsScreen";
    private final List<CapturePoint.SerializableCapturePoint> capturePoints;
    private final boolean openScreen;

    public SyncCapturePointOverviewMessage(List<CapturePoint.SerializableCapturePoint> capturePoints) {
        this(capturePoints, false);
    }

    public SyncCapturePointOverviewMessage(List<CapturePoint.SerializableCapturePoint> capturePoints, boolean openScreen) {
        this.capturePoints = capturePoints;
        this.openScreen = openScreen;
    }

    public static void encode(SyncCapturePointOverviewMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.openScreen);
        buf.writeVarInt(msg.capturePoints.size());
        for (CapturePoint.SerializableCapturePoint point : msg.capturePoints) {
            point.toNetwork(buf);
        }
    }

    public static SyncCapturePointOverviewMessage decode(FriendlyByteBuf buf) {
        boolean openScreen = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > 4096) {
            throw new IllegalArgumentException("Invalid overview capture point count: " + size);
        }
        List<CapturePoint.SerializableCapturePoint> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CapturePoint.SerializableCapturePoint point = CapturePoint.SerializableCapturePoint.fromNetwork(buf);
            if (point != null) {
                points.add(point);
            }
        }
        return new SyncCapturePointOverviewMessage(points, openScreen);
    }

    public static void handle(SyncCapturePointOverviewMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                handleOnClient(msg.capturePoints, msg.openScreen);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleOnClient(List<CapturePoint.SerializableCapturePoint> points, boolean openScreen) {
        try {
            Class<?> screenClass = Class.forName(OVERVIEW_SCREEN_CLASS);
            if (openScreen) {
                screenClass.getMethod("openFromServer", List.class).invoke(null, points);
            } else {
                screenClass.getMethod("syncOverviewFromServer", List.class).invoke(null, points);
            }
        } catch (ReflectiveOperationException e) {
            ModLogger.syncError("Failed to handle capture point overview sync: " + e.getMessage());
        }
    }

    public static void sendToPlayer(ServerPlayer player, List<CapturePoint.SerializableCapturePoint> points) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncCapturePointOverviewMessage(points));
    }

    public static void sendOpenToPlayer(ServerPlayer player, List<CapturePoint.SerializableCapturePoint> points) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncCapturePointOverviewMessage(points, true));
    }

    public static void broadcastToAll(List<CapturePoint.SerializableCapturePoint> points) {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncCapturePointOverviewMessage(points));
    }
}
