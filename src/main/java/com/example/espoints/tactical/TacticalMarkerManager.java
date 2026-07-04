package com.example.espoints.tactical;

import com.example.espoints.ESPointsMod;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.SyncTacticalMarkersMessage;
import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 战术标点的服务端权威状态和同阵营同步入口。 */
public final class TacticalMarkerManager {
    private static final int MAX_MARKERS_PER_TEAM = 64;
    private static final Map<String, List<TacticalMarker>> MARKERS_BY_TEAM = new HashMap<>();
    private static int cleanupTickCounter;

    private TacticalMarkerManager() {
    }

    public static void place(ServerPlayer player, TacticalMarkerType type, double x, double z) {
        if (type == null || !Double.isFinite(x) || !Double.isFinite(z)) {
            return;
        }
        if (!EspetroTeamBridge.canPlaceTacticalMarker(player)) {
            player.sendSystemMessage(Component.literal("§c只有指挥官和队长可以放置战术标点。"));
            return;
        }

        String team = EspetroTeamBridge.getServerPlayerTeam(player);
        if (team == null) {
            return;
        }

        TacticalMapJsonConfig.TacticalMapBounds bounds = TacticalMapJsonConfig.getInstance().getBounds();
        if (!bounds.contains(x, z)) {
            player.sendSystemMessage(Component.literal("§c标点位置超出战术地图允许范围。"));
            return;
        }

        List<TacticalMarker> markers = MARKERS_BY_TEAM.computeIfAbsent(team, ignored -> new ArrayList<>());
        while (markers.size() >= MAX_MARKERS_PER_TEAM) {
            markers.remove(0);
        }
        markers.add(new TacticalMarker(UUID.randomUUID(), type, x, z, team,
            player.getUUID(), player.getName().getString(), System.currentTimeMillis()));
        syncTeam(team);
    }

    public static void removeOwn(ServerPlayer player, UUID markerId) {
        if (markerId == null) {
            return;
        }
        String team = EspetroTeamBridge.getServerPlayerTeam(player);
        if (team == null) {
            return;
        }
        List<TacticalMarker> markers = MARKERS_BY_TEAM.get(team);
        if (markers == null) {
            return;
        }
        boolean removed = markers.removeIf(marker -> marker.id().equals(markerId)
            && marker.ownerId().equals(player.getUUID()));
        if (removed) {
            if (markers.isEmpty()) {
                MARKERS_BY_TEAM.remove(team);
            }
            syncTeam(team);
        }
    }

    public static void sendTo(ServerPlayer player) {
        String team = EspetroTeamBridge.getServerPlayerTeam(player);
        List<TacticalMarker> markers = team == null
            ? List.of()
            : MARKERS_BY_TEAM.getOrDefault(team, List.of());
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncTacticalMarkersMessage(markers));
    }

    public static void syncTeam(String team) {
        MinecraftServer server = ESPointsMod.getServer();
        if (server == null || team == null) {
            return;
        }
        SyncTacticalMarkersMessage message = new SyncTacticalMarkersMessage(
            MARKERS_BY_TEAM.getOrDefault(team, List.of()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (EspetroTeamBridge.isSameTeam(team, EspetroTeamBridge.getServerPlayerTeam(player))) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
            }
        }
    }

    /** 每秒清理一次过期标点，并向受影响阵营同步删除结果。 */
    public static void tick() {
        if (++cleanupTickCounter < 20) {
            return;
        }
        cleanupTickCounter = 0;
        List<String> changedTeams = removeExpiredMarkers();
        for (String team : changedTeams) {
            syncTeam(team);
        }
    }

    private static List<String> removeExpiredMarkers() {
        long lifetime = TacticalMapJsonConfig.getInstance().getTacticalMarkerDurationMillis();
        long cutoff = System.currentTimeMillis() - lifetime;
        List<String> changedTeams = new ArrayList<>();
        Iterator<Map.Entry<String, List<TacticalMarker>>> iterator = MARKERS_BY_TEAM.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<TacticalMarker>> entry = iterator.next();
            if (entry.getValue().removeIf(marker -> marker.createdAtMillis() <= cutoff)) {
                changedTeams.add(entry.getKey());
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }
        return changedTeams;
    }

    public static void reset() {
        MARKERS_BY_TEAM.clear();
        cleanupTickCounter = 0;
        MinecraftServer server = ESPointsMod.getServer();
        if (server == null) {
            return;
        }
        SyncTacticalMarkersMessage empty = new SyncTacticalMarkersMessage(List.of());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), empty);
        }
    }
}
