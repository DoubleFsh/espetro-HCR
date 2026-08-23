package com.example.espoints.config;

import com.example.espoints.util.ModLogger;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Loads capture-point presets from {@code EsWorld/<map>/Points/*.json}.
 * <p>
 * Each file declares {@code modes: ["AAS","RAAS",...]}. The active map mode
 * (from {@code EsConfig/game.json}) filters candidates; {@code seed} picks one
 * stably. Falls back to legacy {@code EsConfig/CapturePoints.json} when the
 * Points pool is empty.
 */
public final class PointsPresetLoader {

    public static final String POINTS_DIR = "Points";
    public static final String LEGACY_CAPTURE_FILE = "CapturePoints.json";
    public static final String GAME_FILE = "game.json";

    public record Selection(String mode, String sourceName, String json, Path sourcePath) {
    }

    private PointsPresetLoader() {
    }

    /** Reads {@code game.json} → {@code game.objectiveMode}, default AAS. */
    public static String readConfiguredMode(Path esConfigDir) {
        Path gamePath = esConfigDir.resolve(GAME_FILE);
        if (!Files.isRegularFile(gamePath)) {
            return "AAS";
        }
        try {
            JsonObject root = JsonParser.parseString(
                Files.readString(gamePath, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject game = root.has("game") && root.get("game").isJsonObject()
                ? root.getAsJsonObject("game") : root;
            if (!game.has("objectiveMode")) {
                return "AAS";
            }
            String raw = game.get("objectiveMode").getAsString().trim().toUpperCase(Locale.ROOT);
            if ("RAAS".equals(raw)) {
                return "RAAS";
            }
            if ("AAS".equals(raw) || raw.isEmpty()) {
                return "AAS";
            }
            throw new IllegalArgumentException(
                "game.objectiveMode 只能是 AAS 或 RAAS: " + raw);
        } catch (IOException e) {
            ModLogger.warn("读取 game.json 失败，objectiveMode 回退 AAS: " + e.getMessage());
            return "AAS";
        }
    }

    /**
     * @param esConfigDir {@code EsWorld/<map>/EsConfig}
     * @param preferredMode AAS or RAAS from game.json (may be blank → AAS)
     * @param seed round seed for stable random among matching presets
     */
    public static Selection select(Path esConfigDir, String preferredMode, long seed)
        throws IOException {
        String mode = preferredMode == null || preferredMode.isBlank()
            ? "AAS" : preferredMode.trim().toUpperCase(Locale.ROOT);
        if (!"AAS".equals(mode) && !"RAAS".equals(mode)) {
            throw new IllegalArgumentException("objectiveMode 只能是 AAS 或 RAAS: " + mode);
        }

        Path mapRoot = esConfigDir.getParent();
        Path pointsDir = mapRoot == null ? null : mapRoot.resolve(POINTS_DIR);
        List<Path> candidates = listMatchingPresets(pointsDir, mode);

        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparing(p -> p.getFileName().toString()));
            Path chosen = candidates.get(new Random(seed).nextInt(candidates.size()));
            String json = Files.readString(chosen, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            // Ensure objectiveMode matches the map mode for ObjectiveLayout.
            root.addProperty("objectiveMode", mode);
            ModLogger.info("Points 预设已选择: " + chosen.getFileName()
                + " (mode=" + mode + ", candidates=" + candidates.size() + ")");
            return new Selection(mode, chosen.getFileName().toString(),
                root.toString(), chosen);
        }

        Path legacy = esConfigDir.resolve(LEGACY_CAPTURE_FILE);
        if (Files.isRegularFile(legacy)) {
            ModLogger.warn("Points/ 无匹配 " + mode + " 的预设，回退 "
                + LEGACY_CAPTURE_FILE);
            String json = Files.readString(legacy, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            root.addProperty("objectiveMode", mode);
            return new Selection(mode, LEGACY_CAPTURE_FILE, root.toString(), legacy);
        }

        throw new IOException("地图缺少 Points/ 中适用于 " + mode
            + " 的据点预设，且无 " + LEGACY_CAPTURE_FILE);
    }

    private static List<Path> listMatchingPresets(Path pointsDir, String mode)
        throws IOException {
        List<Path> matched = new ArrayList<>();
        if (pointsDir == null || !Files.isDirectory(pointsDir)) {
            return matched;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pointsDir, "*.json")) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    JsonObject root = JsonParser.parseString(
                        Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                    if (modesContain(root, mode)) {
                        matched.add(file);
                    }
                } catch (RuntimeException e) {
                    ModLogger.warn("跳过损坏的 Points 预设 " + file.getFileName()
                        + ": " + e.getMessage());
                }
            }
        }
        return matched;
    }

    private static boolean modesContain(JsonObject root, String mode) {
        if (!root.has("modes")) {
            // Legacy file without modes: treat as both if it has matching content.
            if ("RAAS".equals(mode)) {
                return root.has("raas") || "RAAS".equalsIgnoreCase(
                    getString(root, "objectiveMode", ""));
            }
            return root.has("plannedPoints") || "AAS".equalsIgnoreCase(
                getString(root, "objectiveMode", "AAS"));
        }
        JsonElement element = root.get("modes");
        if (!element.isJsonArray()) {
            return false;
        }
        JsonArray array = element.getAsJsonArray();
        for (JsonElement entry : array) {
            if (entry.isJsonPrimitive()
                && mode.equalsIgnoreCase(entry.getAsString().trim())) {
                return true;
            }
        }
        return false;
    }

    private static String getString(JsonObject root, String key, String fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return root.get(key).getAsString();
    }
}
