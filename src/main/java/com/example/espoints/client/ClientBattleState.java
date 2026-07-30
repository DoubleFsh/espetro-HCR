package com.example.espoints.client;

import com.example.espoints.capturepoint.CapturePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-only logical state. It deliberately never consults whether the JVM
 * also hosts an integrated server, so singleplayer and dedicated clients use
 * identical packet-driven behavior.
 */
public final class ClientBattleState {
    private static final ClientBattleState INSTANCE = new ClientBattleState();

    private final Map<String, CapturePoint> capturePoints = new LinkedHashMap<>();
    private boolean operationModeRunning;
    private int currentBatch = 1;
    private int totalBatches;
    private String endBehavior = "terminate";
    private Map<String, String> teamRoles = Map.of();
    private Map<String, Integer> reinforcements = Map.of();
    private Map<String, Integer> initialReinforcements = Map.of();

    private ClientBattleState() {
    }

    public static ClientBattleState get() {
        return INSTANCE;
    }

    public synchronized List<CapturePoint> replaceCapturePoints(
            List<CapturePoint.SerializableCapturePoint> snapshots) {
        Map<String, CapturePoint> next = new LinkedHashMap<>();
        for (CapturePoint.SerializableCapturePoint snapshot : snapshots) {
            CapturePoint point = capturePoints.get(snapshot.name);
            if (point == null
                || point.getBatch() != snapshot.batch
                || !point.getPos1().equals(snapshot.pos1)
                || !point.getPos2().equals(snapshot.pos2)) {
                point = new CapturePoint(
                    snapshot.name, snapshot.pos1, snapshot.pos2, snapshot.batch);
            }
            point.restoreFromSerializable(snapshot);
            next.put(snapshot.name, point);
        }
        capturePoints.clear();
        capturePoints.putAll(next);
        return orderedPoints();
    }

    public synchronized List<CapturePoint> points() {
        return orderedPoints();
    }

    public synchronized CapturePoint point(String name) {
        return capturePoints.get(name);
    }

    public synchronized CapturePoint pointContaining(Player player) {
        if (player == null) {
            return null;
        }
        BlockPos pos = player.blockPosition();
        for (CapturePoint point : capturePoints.values()) {
            if ((!operationModeRunning || point.getBatch() == currentBatch)
                && point.isPositionInside(pos)) {
                return point;
            }
        }
        return null;
    }

    public synchronized void applyOperation(
            boolean running,
            int batch,
            int batches,
            String behavior,
            Map<String, String> roles,
            Map<String, Integer> current,
            Map<String, Integer> initial) {
        operationModeRunning = running;
        currentBatch = Math.max(1, batch);
        totalBatches = Math.max(0, batches);
        endBehavior = behavior == null ? "terminate" : behavior;
        teamRoles = Map.copyOf(roles);
        reinforcements = Map.copyOf(current);
        initialReinforcements = Map.copyOf(initial);
    }

    public synchronized String attackerTeam() {
        return teamForRole("attacker");
    }

    public synchronized String defenderTeam() {
        return teamForRole("defender");
    }

    public synchronized int reinforcements(String team) {
        return reinforcements.getOrDefault(team, 0);
    }

    public synchronized int initialReinforcements(String team) {
        return initialReinforcements.getOrDefault(team, 0);
    }

    public synchronized int currentBatch() {
        return currentBatch;
    }

    public synchronized int totalBatches() {
        return totalBatches;
    }

    public synchronized void clear() {
        capturePoints.clear();
        operationModeRunning = false;
        currentBatch = 1;
        totalBatches = 0;
        endBehavior = "terminate";
        teamRoles = Map.of();
        reinforcements = Map.of();
        initialReinforcements = Map.of();
    }

    private String teamForRole(String expectedRole) {
        return teamRoles.entrySet().stream()
            .filter(entry -> expectedRole.equals(
                entry.getValue() == null ? "" : entry.getValue().toLowerCase(Locale.ROOT)))
            .map(Map.Entry::getKey)
            .sorted()
            .findFirst()
            .orElse(null);
    }

    private List<CapturePoint> orderedPoints() {
        List<CapturePoint> result = new ArrayList<>(capturePoints.values());
        result.sort(Comparator.comparingInt(CapturePoint::getBatch)
            .thenComparing(CapturePoint::getName));
        return List.copyOf(result);
    }
}
