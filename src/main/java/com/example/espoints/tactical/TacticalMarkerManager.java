package com.example.espoints.tactical;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.SyncTacticalMarkersMessage;
import com.example.espoints.network.TacticalMarkerDeltaMessage;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.espetro.api.EspetroAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 战术标点的服务端权威状态和同阵营同步入口（含 3D 高度 y）。 */
public final class TacticalMarkerManager {
    private static final int MAX_MARKERS_PER_TEAM = 64;
    private static final long PLACE_COOLDOWN_MS = 400L;
    /** 反作弊：客户端 raycast 合理上限，非显示距离。 */
    private static final double MAX_PLACE_DISTANCE = 256.0D;
    private static final Map<String, List<TacticalMarker>> MARKERS_BY_TEAM = new HashMap<>();
    private static final Map<UUID, Long> LAST_PLACE_MS = new ConcurrentHashMap<>();
    private static int cleanupTickCounter;

    private TacticalMarkerManager() {
    }

    /** 地图点击等仅知 x/z：y 取该水平坐标地表高度，保证 3D 不埋地/浮空离谱。 */
    public static void place(ServerPlayer player, TacticalMarkerType type, double x, double z) {
        double y = player != null ? player.getY() : 64.0D;
        if (player != null && player.serverLevel() != null) {
            int hx = (int) Math.floor(x);
            int hz = (int) Math.floor(z);
            BlockPos probe = new BlockPos(hx, player.serverLevel().getMinBuildHeight(), hz);
            // 战术地图可选全图坐标。未加载区块不能因一次标点同步强制加载；
            // 玩家靠近后 Ping Wheel 会按实际世界坐标正常显示。
            if (player.serverLevel().hasChunkAt(probe)) {
                int ground = player.serverLevel().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hx, hz);
                y = ground + 1.15D;
            }
        }
        place(player, type, x, y, z);
    }

    public static void place(ServerPlayer player, TacticalMarkerType type,
                             double x, double y, double z) {
        placeInternal(player, type, x, y, z, false);
    }

    /** Ping Wheel 世界射线入口：额外校验服务端视线方向与遮挡。 */
    public static void placeFromView(ServerPlayer player, TacticalMarkerType type,
                                     double x, double y, double z) {
        placeInternal(player, type, x, y, z, true);
    }

    private static void placeInternal(ServerPlayer player, TacticalMarkerType type,
                                      double x, double y, double z, boolean validateView) {
        if (type == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }
        if (player == null) {
            return;
        }
        if (!EspetroAPI.isActiveBattlefield(player.serverLevel())) {
            player.sendSystemMessage(Component.literal("§c请进入当前战场后再放置标点。"));
            return;
        }
        if (!EspetroTeamBridge.canPlaceTacticalMarker(player)) {
            player.sendSystemMessage(Component.literal(
                "§c只有指挥官、小队长、火力组组长或合法载具座位可以放置战术标点。"));
            return;
        }

        long now = System.currentTimeMillis();
        Long last = LAST_PLACE_MS.get(player.getUUID());
        if (last != null && now - last < PLACE_COOLDOWN_MS) {
            return;
        }

        if (validateView) {
            double dx = x - player.getX();
            double dy = y - player.getY();
            double dz = z - player.getZ();
            if (dx * dx + dy * dy + dz * dz > MAX_PLACE_DISTANCE * MAX_PLACE_DISTANCE) {
                player.sendSystemMessage(Component.literal("§c标点距离过远。"));
                return;
            }
            if (!isReasonableAim(player, x, y, z)) {
                player.sendSystemMessage(Component.literal("§c标点位置与当前视线不符。"));
                return;
            }
        }

        String team = EspetroTeamBridge.getServerPlayerTeam(player);
        if (team == null) {
            return;
        }

        TacticalMapJsonConfig.TacticalMapBounds bounds =
            TacticalMapJsonConfig.getInstance().getBounds();
        if (!bounds.contains(x, z)) {
            player.sendSystemMessage(Component.literal("§c标点位置超出战术地图允许范围。"));
            return;
        }

        LAST_PLACE_MS.put(player.getUUID(), now);
        List<TacticalMarker> markers =
            MARKERS_BY_TEAM.computeIfAbsent(team, ignored -> new ArrayList<>());
        List<UUID> removed = new ArrayList<>(1);
        while (markers.size() >= MAX_MARKERS_PER_TEAM) {
            removed.add(markers.remove(0).id());
        }
        TacticalMarker marker = new TacticalMarker(
            UUID.randomUUID(), type, x, y, z, team,
            player.getUUID(), player.getName().getString(), now,
            EspetroTeamBridge.getPlayerSquadId(player),
            EspetroTeamBridge.isCommander(player));
        markers.add(marker);
        sendDeltaToTeam(team, TacticalMarkerDeltaMessage.add(List.of(marker), removed));
        player.sendSystemMessage(
            Component.literal("§a已标记：" + type.getDisplayName()), true);
    }

    public static void removeOwn(ServerPlayer player, UUID markerId) {
        if (markerId == null || player == null) {
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
        TacticalMarker owned = markers.stream()
            .filter(marker -> marker.id().equals(markerId)
                && marker.ownerId().equals(player.getUUID()))
            .findFirst()
            .orElse(null);
        if (owned != null && markers.remove(owned)) {
            if (markers.isEmpty()) {
                MARKERS_BY_TEAM.remove(team);
            }
            sendDeltaToTeam(team,
                TacticalMarkerDeltaMessage.add(List.of(), List.of(markerId)));
        }
    }

    public static void sendTo(ServerPlayer player) {
        String team = EspetroTeamBridge.getServerPlayerTeam(player);
        List<TacticalMarker> markers = team == null
            || !EspetroAPI.isActiveBattlefield(player.serverLevel())
            ? List.of()
            : MARKERS_BY_TEAM.getOrDefault(team, List.of());
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new SyncTacticalMarkersMessage(markers));
    }

    private static void sendDeltaToTeam(String team, TacticalMarkerDeltaMessage message) {
        MinecraftServer server = ESPointsMod.getServer();
        if (server == null || team == null || message == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (EspetroAPI.isActiveBattlefield(player.serverLevel())
                && EspetroTeamBridge.isSameTeam(team,
                EspetroTeamBridge.getServerPlayerTeam(player))) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
            }
        }
    }

    /** 每秒清理一次过期标点，并向受影响阵营同步删除结果。 */
    public static void tick() {
        if (MARKERS_BY_TEAM.isEmpty()) {
            return;
        }
        if (++cleanupTickCounter < 20) {
            return;
        }
        cleanupTickCounter = 0;
        Map<String, List<UUID>> removals = removeExpiredMarkers();
        for (Map.Entry<String, List<UUID>> entry : removals.entrySet()) {
            sendDeltaToTeam(entry.getKey(),
                TacticalMarkerDeltaMessage.add(List.of(), entry.getValue()));
        }
    }

    private static Map<String, List<UUID>> removeExpiredMarkers() {
        long lifetime = TacticalMapJsonConfig.getInstance().getTacticalMarkerDurationMillis();
        long cutoff = System.currentTimeMillis() - lifetime;
        Map<String, List<UUID>> removedByTeam = new HashMap<>();
        Iterator<Map.Entry<String, List<TacticalMarker>>> iterator =
            MARKERS_BY_TEAM.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<TacticalMarker>> entry = iterator.next();
            Iterator<TacticalMarker> markers = entry.getValue().iterator();
            while (markers.hasNext()) {
                TacticalMarker marker = markers.next();
                if (!marker.type().isPersistentUntilRemoved()
                    && marker.createdAtMillis() <= cutoff) {
                    removedByTeam.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                        .add(marker.id());
                    markers.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
        return removedByTeam;
    }

    public static void reset() {
        boolean hadState = !MARKERS_BY_TEAM.isEmpty() || !LAST_PLACE_MS.isEmpty();
        MARKERS_BY_TEAM.clear();
        LAST_PLACE_MS.clear();
        cleanupTickCounter = 0;
        MinecraftServer server = ESPointsMod.getServer();
        if (server == null || !hadState) {
            return;
        }
        TacticalMarkerDeltaMessage empty = TacticalMarkerDeltaMessage.clearAll();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), empty);
        }
    }

    public static void clearPlayerCooldown(UUID id) {
        if (id != null) {
            LAST_PLACE_MS.remove(id);
        }
    }

    private static boolean isReasonableAim(ServerPlayer player,
                                           double x, double y, double z) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = new Vec3(x, y, z);
        Vec3 delta = target.subtract(eye);
        double distance = delta.length();
        if (distance < 0.25D || distance > MAX_PLACE_DISTANCE) {
            return false;
        }
        Vec3 direction = delta.scale(1.0D / distance);
        // 约 18° 容差，吸收轮盘选择和网络延迟期间的轻微视角变化。
        if (player.getLookAngle().dot(direction) < 0.95D) {
            return false;
        }
        HitResult hit = player.serverLevel().clip(new ClipContext(
            eye,
            target.add(direction.scale(0.5D)),
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            player));
        return hit.getType() == HitResult.Type.MISS
            || hit.getLocation().distanceToSqr(target) <= 3.0D * 3.0D;
    }
}
