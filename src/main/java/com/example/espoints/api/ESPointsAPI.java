package com.example.espoints.api;

import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.config.TeamfightJsonConfig;
import com.example.espoints.util.EspetroTeamBridge;

/**
 * Public coupling surface for Espetro ↔ ESPoints.
 * <p>
 * Occupancy / tactical-map authority lives here. Espetro supplies EsConfig path
 * + seed; ESPoints resolves AAS/RAAS and reports mode/victory/troops via
 * {@code EspetroAPI}.
 */
public final class ESPointsAPI {

    private static volatile String cachedObjectiveMode = "";

    private ESPointsAPI() {
    }

    /** Called after ESPoints resolves a round layout (or clears on battlefield end). */
    public static void refreshCachedMode(String mode) {
        cachedObjectiveMode = mode == null ? "" : mode.trim().toUpperCase();
    }

    /** Resolved objective mode for this round: {@code AAS}, {@code RAAS}, or empty. */
    public static String getObjectiveMode() {
        if (cachedObjectiveMode != null && !cachedObjectiveMode.isBlank()) {
            return cachedObjectiveMode;
        }
        return CapturePointManager.getInstance().isRaasFrontline() ? "RAAS" : "AAS";
    }

    public static boolean isRaasFrontline() {
        return CapturePointManager.getInstance().isRaasFrontline()
            || "RAAS".equalsIgnoreCase(getObjectiveMode());
    }

    public static String getSelectedLaneId() {
        String lane = TeamfightJsonConfig.getLastSelectedLaneId();
        return lane == null ? "" : lane;
    }

    public static String teamDisplayName(String team) {
        return EspetroTeamBridge.displayName(team);
    }

    public static boolean isOperationModeRunning() {
        return CapturePointManager.getInstance().isOperationModeRunning();
    }
}
