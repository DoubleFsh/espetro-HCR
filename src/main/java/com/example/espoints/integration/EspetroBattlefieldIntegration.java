package com.example.espoints.integration;

import com.example.espoints.ESPointsMod;
import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.config.TeamfightJsonConfig;
import com.example.espoints.network.SyncTacticalMapBackgroundMessage;
import com.example.espoints.network.SyncTacticalMapConfigMessage;
import com.example.espoints.network.RequestTacticalMapTileMessage;
import com.example.espoints.network.RequestRateLimiter;
import com.example.espoints.tactical.TacticalMarkerManager;
import com.example.espoints.tile.TacticalMapTileService;
import com.google.gson.JsonParser;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.espetro.api.ActiveBattlefieldSnapshot;
import org.espetro.api.event.BattlefieldLifecycleEvent;
import org.espetro.api.event.GamePhaseChangedEvent;
import org.espetro.team.GamePhase;

/** Applies and clears ESPoints state atomically with Espetro's disposable battlefield. */
public final class EspetroBattlefieldIntegration {

    @SubscribeEvent
    public void onBattlefieldActivated(BattlefieldLifecycleEvent.Activated event) {
        ActiveBattlefieldSnapshot snapshot = event.snapshot();
        TacticalMapTileService.get().activate(snapshot);
        CapturePointManager manager = CapturePointManager.getInstance();
        manager.onBattlefieldActivated();

        try {
            TacticalMapJsonConfig.apply(
                TacticalMapJsonConfig.fromJson(
                    JsonParser.parseString(snapshot.tacticalMapJson())),
                "EsWorld/" + snapshot.mapId() + "/EsConfig/TacticalMap.json");
        } catch (RuntimeException e) {
            ESPointsMod.LOGGER.error("活动地图战术地图配置无法应用: {}", snapshot.mapId(), e);
            return;
        }

        TeamfightJsonConfig.LoadResult points = TeamfightJsonConfig.loadFrozenSnapshot(
            snapshot.mapId(), snapshot.capturePointsJson());
        if (!points.isSuccess()) {
            ESPointsMod.LOGGER.error("活动地图据点配置无法应用: {} ({})",
                snapshot.mapId(), points.getMessage());
            return;
        }

        SyncTacticalMapConfigMessage.broadcastToAll();
        SyncTacticalMapBackgroundMessage.broadcastToAll();
        ESPointsMod.LOGGER.info("ESPoints 已切换到 Espetro 地图: {}", snapshot.mapId());
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
