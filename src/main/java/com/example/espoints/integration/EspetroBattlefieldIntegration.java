package com.example.espoints.integration;

import com.example.espoints.ESPointsMod;
import com.example.espoints.api.ESPointsAPI;
import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.config.PointsPresetLoader;
import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.config.TeamfightJsonConfig;
import com.example.espoints.network.SyncTacticalMapBackgroundMessage;
import com.example.espoints.network.SyncTacticalMapConfigMessage;
import com.example.espoints.network.RequestTacticalMapTileMessage;
import com.example.espoints.network.RequestRateLimiter;
import com.example.espoints.tile.TacticalMapTileService;
import com.google.gson.JsonParser;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.espetro.api.ActiveBattlefieldSnapshot;
import org.espetro.api.EspetroAPI;
import org.espetro.api.event.BattlefieldLifecycleEvent;
import org.espetro.api.event.GamePhaseChangedEvent;
import org.espetro.team.GamePhase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Applies and clears ESPoints state when Espetro activates a disposable battlefield.
 * <p>
 * ESPoints reads {@code TacticalMap.json} from EsConfig and picks a capture preset
 * from {@code ../Points/*.json} (modes-filtered). Espetro only supplies the path
 * and round seed.
 */
public final class EspetroBattlefieldIntegration {

    @SubscribeEvent
    public void onBattlefieldActivated(BattlefieldLifecycleEvent.Activated event) {
        ActiveBattlefieldSnapshot snapshot = event.snapshot();
        TacticalMapTileService.get().activate(snapshot);
        CapturePointManager manager = CapturePointManager.getInstance();
        manager.onBattlefieldActivated();

        Path esConfig = resolveEsConfig(snapshot);
        if (esConfig == null) {
            ESPointsMod.LOGGER.error("活动地图缺少 EsConfig 路径: {}", snapshot.mapId());
            return;
        }

        try {
            Path tacticalPath = esConfig.resolve("TacticalMap.json");
            String tacticalJson = Files.readString(tacticalPath, StandardCharsets.UTF_8);
            TacticalMapJsonConfig.apply(
                TacticalMapJsonConfig.fromJson(JsonParser.parseString(tacticalJson)),
                tacticalPath.toString());
        } catch (Exception e) {
            ESPointsMod.LOGGER.error("活动地图战术地图配置无法应用: {}", snapshot.mapId(), e);
            return;
        }

        try {
            long seed = snapshot.objectiveSeed();
            String configuredMode = PointsPresetLoader.readConfiguredMode(esConfig);
            PointsPresetLoader.Selection preset =
                PointsPresetLoader.select(esConfig, configuredMode, seed);
            TeamfightJsonConfig.LoadResult points =
                TeamfightJsonConfig.loadFrozenSnapshot(snapshot.mapId(), preset.json(), seed);
            if (!points.isSuccess()) {
                ESPointsMod.LOGGER.error("活动地图据点配置无法应用: {} ({})",
                    snapshot.mapId(), points.getMessage());
                return;
            }
            String mode = CapturePointManager.getInstance().isRaasFrontline()
                ? "RAAS" : preset.mode();
            String lane = TeamfightJsonConfig.getLastSelectedLaneId();
            try {
                EspetroAPI.setResolvedObjectiveMode(mode, lane == null ? "" : lane);
            } catch (Throwable t) {
                ESPointsMod.LOGGER.debug("回写 objectiveMode 失败: {}", t.toString());
            }
            ESPointsAPI.refreshCachedMode(mode);
            ESPointsMod.LOGGER.info("据点预设: {} (mode={})", preset.sourceName(), mode);
        } catch (Exception e) {
            ESPointsMod.LOGGER.error("活动地图据点配置无法应用: {}", snapshot.mapId(), e);
            return;
        }

        SyncTacticalMapConfigMessage.broadcastToAll();
        SyncTacticalMapBackgroundMessage.broadcastToAll();
        ESPointsMod.LOGGER.info("ESPoints 已从 EsConfig/Points 装载地图: {} ({})",
            snapshot.mapId(), esConfig);
    }

    private static Path resolveEsConfig(ActiveBattlefieldSnapshot snapshot) {
        if (snapshot.esConfigPath() != null && !snapshot.esConfigPath().isBlank()) {
            Path path = Path.of(snapshot.esConfigPath());
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        // Fallback: conventional relative path from game dir
        Path fallback = Path.of("EsWorld", snapshot.mapId(), "EsConfig");
        return Files.isDirectory(fallback) ? fallback : null;
    }

    @SubscribeEvent
    public void onBattlefieldCleared(BattlefieldLifecycleEvent.Cleared event) {
        CapturePointManager.getInstance().onBattlefieldCleared();
        TacticalMapTileService.get().clear();
        RequestTacticalMapTileMessage.clearAll();
        RequestRateLimiter.clearAll();
        TeamfightJsonConfig.clearFrozenSnapshot();
        TacticalMapJsonConfig.apply(
            TacticalMapJsonConfig.createDefault(), "no active Espetro battlefield");
        SyncTacticalMapConfigMessage.broadcastToAll();
        SyncTacticalMapBackgroundMessage.broadcastToAll();
        ESPointsAPI.refreshCachedMode("");
        ESPointsMod.LOGGER.info("ESPoints 已清除地图状态: {}", event.snapshot().mapId());
    }

    @SubscribeEvent
    public void onGamePhaseChanged(GamePhaseChangedEvent event) {
        if (event.current() == GamePhase.DEPLOYING) {
            CapturePointManager.getInstance().onEspetroDeployingStarted();
        } else if (event.current() == GamePhase.CLEANUP) {
            CapturePointManager.getInstance().onBattlefieldCleared();
        }
    }
}
