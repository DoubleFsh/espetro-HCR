package com.example.espoints.api;

import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.tactical.TacticalMarkerManager;
import com.example.espoints.tactical.TacticalMarkerType;
import com.example.espoints.util.EspetroTeamBridge;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Server-side bridge intended for Espetro KubeJS commander scripts.
 */
public final class ESPointsCommanderScriptAPI {
    private ESPointsCommanderScriptAPI() {
    }

    public static boolean isWithinTacticalMap(double x, double z) {
        return Double.isFinite(x)
            && Double.isFinite(z)
            && TacticalMapJsonConfig.getInstance().getBounds().contains(x, z);
    }

    public static double getMapMinX() {
        return TacticalMapJsonConfig.getInstance().getBounds().minX;
    }

    public static double getMapMinZ() {
        return TacticalMapJsonConfig.getInstance().getBounds().minZ;
    }

    public static double getMapMaxX() {
        return TacticalMapJsonConfig.getInstance().getBounds().maxX;
    }

    public static double getMapMaxZ() {
        return TacticalMapJsonConfig.getInstance().getBounds().maxZ;
    }

    public static double getMapWidth() {
        return TacticalMapJsonConfig.getInstance().getBounds().width();
    }

    public static double getMapHeight() {
        return TacticalMapJsonConfig.getInstance().getBounds().height();
    }

    public static String getPlayerTeam(ServerPlayer player) {
        return EspetroTeamBridge.getServerPlayerTeam(player);
    }

    public static boolean canPlaceTacticalMarker(ServerPlayer player) {
        return EspetroTeamBridge.canPlaceTacticalMarker(player);
    }

    public static boolean placeMarker(ServerPlayer player, String typeId, double x, double z) {
        TacticalMarkerType type = resolveMarkerType(typeId);
        if (player == null || type == null || !isWithinTacticalMap(x, z)
                || !EspetroTeamBridge.canPlaceTacticalMarker(player)) {
            return false;
        }

        TacticalMarkerManager.place(player, type, x, z);
        return true;
    }

    public static boolean placeArtilleryTarget(ServerPlayer player, double x, double z) {
        return placeMarker(player, "ARTILLERY_TARGET", x, z);
    }

    public static boolean submitArtillerySupportTarget(ServerPlayer player, double x, double z) {
        return submitCommanderSkillTarget(player, x, z);
    }

    public static boolean submitCommanderSkillTarget(ServerPlayer player, double x, double z) {
        if (player == null || !isWithinTacticalMap(x, z)) {
            return false;
        }
        return EspetroTeamBridge.submitCommanderSkillTarget(player, x, z);
    }

    private static TacticalMarkerType resolveMarkerType(String typeId) {
        if (typeId == null || typeId.isBlank()) {
            return null;
        }

        String normalized = typeId.trim().toUpperCase(Locale.ROOT);
        for (TacticalMarkerType type : TacticalMarkerType.values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
