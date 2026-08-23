package com.example.espoints.config;

import com.example.espoints.capturepoint.CapturePoint;
import com.example.espoints.capturepoint.CapturePointManager;
import com.example.espoints.objective.ObjectiveLayout;
import com.example.espoints.util.EspetroTeamBridge;
import com.example.espoints.util.ModLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Frozen per-map action-mode setup supplied by Espetro.
 */
public final class TeamfightJsonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_DIRECTORY = "espoints";
    private static final int DEFAULT_REINFORCEMENTS = 50;
    /** 进攻方占完一批次据点时的默认兵力增援。 */
    private static final int DEFAULT_ATTACK_BATCH_COMPLETION_REINFORCEMENT = 200;
    /** RAAS 每次占领成功时的默认兵力增援。 */
    private static final int DEFAULT_CAPTURE_REINFORCEMENT = 50;
    /** RAAS 一方占齐全部争夺点后，对方每秒扣兵默认值。 */
    private static final int DEFAULT_TICKET_BLEED_PER_SECOND = 1;
    private static final int MAX_POINTS_PER_BATCH = 7;
    private static final int MAX_RAAS_POINTS = ObjectiveLayout.MAX_RAAS_STAGES;
    private static String frozenMapId;
    private static String frozenJson;
    private static long frozenSeed;
    private static String lastSelectedLaneId = "";

    private TeamfightJsonConfig() {
    }

    public static LoadResult loadConfig() {
        return loadConfig(false);
    }

    public static LoadResult loadConfig(boolean allowWhileRunning) {
        CapturePointManager manager = CapturePointManager.getInstance();
        Path configPath = getConfigPath();

        if (manager.isOperationModeRunning() && !allowWhileRunning) {
            return LoadResult.failure(configPath, "行动正在进行，未加载行动模式JSON配置");
        }
        if (manager.isOperationModeRunning()) {
            manager.stopOperationMode();
        }

        if (frozenJson == null || frozenJson.isBlank()) {
            return LoadResult.failure(configPath, "无活动战场：据点配置仅来自 EsWorld/<map>/Points 预设快照，请先由 Espetro 装载地图");
        }

        try {
            TeamfightConfig config = parseJson(frozenJson, frozenSeed);
            applyConfig(manager, config);
            ModLogger.debug("已恢复地图 " + frozenMapId + " 的启动快照，计划据点 "
                + config.points.size() + " 个");
            return LoadResult.success(configPath, config.points.size(), config.totalBatches, config.endBehavior);
        } catch (RuntimeException e) {
            ModLogger.error("加载行动模式JSON配置失败: " + e.getMessage());
            return LoadResult.failure(configPath, e.getMessage());
        }
    }

    public static synchronized LoadResult loadFrozenSnapshot(String mapId, String json) {
        return loadFrozenSnapshot(mapId, json, ThreadLocalRandom.current().nextLong());
    }

    /**
     * Loads a map CapturePoints snapshot. {@code json} may be the raw
     * EsConfig file (with {@code raas}) or an already-reduced plannedPoints
     * document. When a raw RAAS layout is present, {@code seed} selects the
     * lane and stage points. Only {@code AAS}/{@code RAAS} are accepted.
     */
    public static synchronized LoadResult loadFrozenSnapshot(String mapId, String json, long seed) {
        Path source = snapshotPath(mapId);
        try {
            LayoutResolution resolution = resolveLayout(parseRootObject(json), seed);
            lastSelectedLaneId = resolution.laneId;
            TeamfightConfig config = parseConfig(resolution.root);
            CapturePointManager manager = CapturePointManager.getInstance();
            if (manager.isOperationModeRunning()) {
                manager.stopOperationMode();
            }
            frozenMapId = mapId == null || mapId.isBlank() ? "unknown" : mapId;
            frozenSeed = seed;
            frozenJson = GSON.toJson(resolution.root);
            applyConfig(manager, config);
            ModLogger.info("已载入地图 " + frozenMapId + " 的据点配置，共 "
                + config.points.size() + " 个计划据点"
                + (config.raasFrontline ? "（RAAS 对向推线）" : "")
                + (lastSelectedLaneId.isEmpty() ? "" : "，路线=" + lastSelectedLaneId));
            return LoadResult.success(source, config.points.size(),
                config.totalBatches, config.endBehavior);
        } catch (RuntimeException e) {
            return LoadResult.failure(source, e.getMessage());
        }
    }

    /** Lane id chosen by the last {@link #loadFrozenSnapshot} RAAS select, or empty. */
    public static synchronized String getLastSelectedLaneId() {
        return lastSelectedLaneId == null ? "" : lastSelectedLaneId;
    }

    public static synchronized void clearFrozenSnapshot() {
        frozenMapId = null;
        frozenJson = null;
        frozenSeed = 0L;
        lastSelectedLaneId = "";
    }

    public static LoadResult saveCurrentConfig() {
        CapturePointManager manager = CapturePointManager.getInstance();
        String exportName = (frozenMapId == null ? "inactive" : frozenMapId)
            .replaceAll("[^a-zA-Z0-9_-]", "_");
        Path configPath = getExportDirectory().resolve(exportName + "-CapturePoints.json");

        try {
            Files.createDirectories(configPath.getParent());

            TeamfightConfig config = createConfigFromManager(manager);
            writeConfig(configPath, config);
            ModLogger.info("当前据点状态已导出: " + configPath + "，计划据点 "
                + config.points.size() + " 个；活动地图模板未被修改");
            return LoadResult.success(configPath, config.points.size(), config.totalBatches, config.endBehavior);
        } catch (IOException e) {
            ModLogger.error("保存行动模式JSON配置失败: " + e.getMessage());
            return LoadResult.failure(configPath, e.getMessage());
        }
    }

    private static void applyConfig(CapturePointManager manager, TeamfightConfig config) {
        manager.clearPlannedCapturePoints();
        manager.clearTeamRoles();

        int attackReinforcements = config.teamReinforcements.getOrDefault(EspetroTeamBridge.ATTACK, DEFAULT_REINFORCEMENTS);
        int defendReinforcements = config.teamReinforcements.getOrDefault(EspetroTeamBridge.DEFEND, DEFAULT_REINFORCEMENTS);
        manager.setTeamRole(EspetroTeamBridge.ATTACK, "attacker", attackReinforcements);
        manager.setTeamRole(EspetroTeamBridge.DEFEND, "defender", defendReinforcements);
        manager.setTotalBatches(config.totalBatches);
        manager.setEndBehavior(config.endBehavior);
        manager.setRaasFrontline(config.raasFrontline);
        manager.setCaptureReinforcement(config.captureReinforcement);
        manager.setTicketBleedPerSecond(config.ticketBleedPerSecond);
        manager.setAttackBatchCompletionReinforcement(config.attackBatchCompletionReinforcement);

        for (PlannedPointConfig point : config.points) {
            if (!manager.addPlannedCapturePoint(point.name, point.pos1, point.pos2, point.batch)) {
                throw new IllegalArgumentException("计划据点添加失败: " + point.name);
            }
        }

        manager.syncToAllClients();
    }

    private static TeamfightConfig parseJson(String json, long seed) {
        return parseConfig(resolveLayout(parseRootObject(json), seed).root);
    }

    private static JsonObject parseRootObject(String json) {
        JsonElement rootElement = JsonParser.parseString(json);
        if (rootElement == null || !rootElement.isJsonObject()) {
            throw new JsonParseException("配置根节点必须是JSON对象");
        }
        return rootElement.getAsJsonObject();
    }

    private record LayoutResolution(JsonObject root, String laneId) {
    }

    /**
     * Raw RAAS layouts are reduced with {@code seed}. Already-reduced
     * documents with {@code raasFrontline}/{@code raasSymmetric} keep
     * plannedPoints and stage batches. AAS documents are unchanged.
     * {@code RANDOM} is rejected.
     */
    private static LayoutResolution resolveLayout(JsonObject root, long seed) {
        boolean hasRawRaas = root.has("raas") && root.get("raas").isJsonObject();
        boolean raasFrontline = isRaasFrontlineFlag(root);
        String mode = getOptionalString(root, "objectiveMode", "AAS");
        if (mode == null || mode.isBlank()) {
            mode = "AAS";
        }
        String normalized = mode.trim();
        if ("RANDOM".equalsIgnoreCase(normalized)) {
            throw new JsonParseException("objectiveMode 只能是 AAS 或 RAAS（已移除 RANDOM）");
        }
        boolean isRaas = "RAAS".equalsIgnoreCase(normalized);

        if (hasRawRaas && (isRaas || raasFrontline)) {
            try {
                ObjectiveLayout.Selection selection = ObjectiveLayout.parse(root).select(seed);
                JsonObject reduced = JsonParser.parseString(selection.capturePointsJson())
                    .getAsJsonObject();
                return new LayoutResolution(reduced,
                    selection.laneId() == null ? "" : selection.laneId());
            } catch (IllegalArgumentException e) {
                throw new JsonParseException(e.getMessage(), e);
            }
        }

        if (raasFrontline) {
            return new LayoutResolution(markRaasFrontlineReduced(root), "");
        }
        return new LayoutResolution(root, "");
    }

    /** Already-reduced RAAS: keep stage batches for frontline progression. */
    private static JsonObject markRaasFrontlineReduced(JsonObject root) {
        JsonObject copy = root.deepCopy();
        copy.addProperty("raasFrontline", true);
        copy.remove("raasSymmetric");
        copy.remove("objectiveMode");
        copy.remove("raas");
        return copy;
    }

    private static boolean isRaasFrontlineFlag(JsonObject root) {
        return getOptionalBoolean(root, "raasFrontline", false)
            || getOptionalBoolean(root, "raasSymmetric", false);
    }

    private static TeamfightConfig parseConfig(JsonObject root) {
        boolean raasFrontline = isRaasFrontlineFlag(root);
        List<PlannedPointConfig> points = parsePoints(root);
        validatePoints(points, raasFrontline);

        int calculatedBatches = points.stream()
                .mapToInt(point -> point.batch)
                .max()
                .orElse(1);
        int totalBatches = getOptionalInt(root, "totalBatches", calculatedBatches);
        // RAAS 对向推线：totalBatches = 阶段数，不再强制为 1。
        if (totalBatches < 1) {
            throw new JsonParseException("totalBatches 必须大于等于 1");
        }
        if (calculatedBatches > totalBatches) {
            throw new JsonParseException("totalBatches 小于计划据点最大批次: " + calculatedBatches);
        }

        String endBehavior = getOptionalString(root, "endBehavior", "terminate").toLowerCase(Locale.ROOT);
        if (!"terminate".equals(endBehavior) && !"loop".equals(endBehavior)) {
            throw new JsonParseException("endBehavior 只能是 terminate 或 loop");
        }

        int attackBatchReward = parseAttackBatchCompletionReinforcement(root);
        int captureReinforcement = parseCaptureReinforcement(root);
        int ticketBleed = parseTicketBleedPerSecond(root);
        return new TeamfightConfig(totalBatches, endBehavior, parseTeamReinforcements(root), points,
            attackBatchReward, raasFrontline, captureReinforcement, ticketBleed);
    }

    private static int parseTicketBleedPerSecond(JsonObject root) {
        JsonElement element = firstPresent(root, "ticketBleedPerSecond", "bleedPerSecond");
        if (element == null || element.isJsonNull()) {
            return DEFAULT_TICKET_BLEED_PER_SECOND;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("ticketBleedPerSecond 必须是数字");
        }
        return Math.max(0, element.getAsInt());
    }

    /**
     * 进攻方占完当前批次全部据点时的兵力增援。
     * 主字段 attackBatchCompletionReinforcement；兼容别名。
     */
    private static int parseAttackBatchCompletionReinforcement(JsonObject root) {
        JsonElement element = firstPresent(root,
            "attackBatchCompletionReinforcement",
            "batchCompletionAttackReinforcement",
            "attackCaptureReinforcement");
        if (element == null || element.isJsonNull()) {
            return DEFAULT_ATTACK_BATCH_COMPLETION_REINFORCEMENT;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("attackBatchCompletionReinforcement 必须是数字");
        }
        return Math.max(0, element.getAsInt());
    }

    /**
     * RAAS 每次占领成功发给占领方的兵力。
     * 主字段 captureReinforcement；回退 attackBatchCompletionReinforcement。
     */
    private static int parseCaptureReinforcement(JsonObject root) {
        JsonElement element = firstPresent(root,
            "captureReinforcement",
            "attackBatchCompletionReinforcement",
            "batchCompletionAttackReinforcement",
            "attackCaptureReinforcement");
        if (element == null || element.isJsonNull()) {
            return DEFAULT_CAPTURE_REINFORCEMENT;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException("captureReinforcement 必须是数字");
        }
        return Math.max(0, element.getAsInt());
    }

    private static List<PlannedPointConfig> parsePoints(JsonObject root) {
        JsonElement pointsElement = firstPresent(root, "plannedPoints", "points", "capturePoints");
        List<PlannedPointConfig> points = new ArrayList<>();
        if (pointsElement == null || pointsElement.isJsonNull()) {
            return points;
        }

        if (pointsElement.isJsonArray()) {
            for (JsonElement element : pointsElement.getAsJsonArray()) {
                points.add(parsePoint(null, element));
            }
            return points;
        }

        if (pointsElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : pointsElement.getAsJsonObject().entrySet()) {
                points.add(parsePoint(entry.getKey(), entry.getValue()));
            }
            return points;
        }

        throw new JsonParseException("plannedPoints 必须是数组或对象");
    }

    private static PlannedPointConfig parsePoint(String fallbackName, JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("计划据点必须是JSON对象");
        }

        JsonObject point = element.getAsJsonObject();
        String name = getOptionalString(point, "name", fallbackName);
        if (name != null) {
            name = name.trim().toUpperCase(Locale.ROOT);
        }

        int batch = getRequiredInt(point, "batch");
        BlockPos pos1 = parseBlockPos(firstPresent(point, "pos1", "from"));
        BlockPos pos2 = parseBlockPos(firstPresent(point, "pos2", "to"));
        return new PlannedPointConfig(name, batch, pos1, pos2);
    }

    private static void validatePoints(List<PlannedPointConfig> points, boolean raasFrontline) {
        Set<String> names = new HashSet<>();
        Map<Integer, Integer> pointsPerBatch = new HashMap<>();
        int maxPerBatch = raasFrontline ? MAX_RAAS_POINTS : MAX_POINTS_PER_BATCH;

        CapturePointManager manager = CapturePointManager.getInstance();
        for (PlannedPointConfig point : points) {
            if (!manager.isValidPointName(point.name)) {
                throw new JsonParseException("据点名称必须为单个大写字母(A-Z): " + point.name);
            }
            if (point.batch < 1) {
                throw new JsonParseException("据点 " + point.name + " 的 batch 必须大于等于 1");
            }
            if (!manager.isValidCoordinates(point.pos1, point.pos2)) {
                throw new JsonParseException("据点 " + point.name + " 的 pos1/pos2 必须构成有效长方体区域");
            }
            if (!names.add(point.name)) {
                throw new JsonParseException("存在重复据点名称: " + point.name);
            }

            int batchCount = pointsPerBatch.merge(point.batch, 1, Integer::sum);
            if (batchCount > maxPerBatch) {
                throw new JsonParseException("批次 " + point.batch + " 的据点数量超过上限 " + maxPerBatch);
            }
        }
    }

    private static Map<String, Integer> parseTeamReinforcements(JsonObject root) {
        Map<String, Integer> reinforcements = defaultReinforcements();
        JsonElement element = root.get("teamReinforcements");
        if (element != null && !element.isJsonNull()) {
            if (!element.isJsonObject()) {
                throw new JsonParseException("teamReinforcements 必须是JSON对象");
            }

            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                String team = EspetroTeamBridge.canonicalizeTeamName(entry.getKey());
                if (team == null) {
                    throw new JsonParseException("未知队伍名称: " + entry.getKey());
                }
                reinforcements.put(team, parsePositiveInt(entry.getValue(), "teamReinforcements." + entry.getKey()));
            }
        }

        putOptionalReinforcement(root, reinforcements, "attackReinforcements", EspetroTeamBridge.ATTACK);
        putOptionalReinforcement(root, reinforcements, "attackerReinforcements", EspetroTeamBridge.ATTACK);
        putOptionalReinforcement(root, reinforcements, "defendReinforcements", EspetroTeamBridge.DEFEND);
        putOptionalReinforcement(root, reinforcements, "defenderReinforcements", EspetroTeamBridge.DEFEND);
        return reinforcements;
    }

    private static void putOptionalReinforcement(JsonObject root, Map<String, Integer> reinforcements, String key, String team) {
        if (root.has(key) && !root.get(key).isJsonNull()) {
            reinforcements.put(team, parsePositiveInt(root.get(key), key));
        }
    }

    private static int parsePositiveInt(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(path + " 必须是数字");
        }
        int value = element.getAsInt();
        if (value <= 0) {
            throw new JsonParseException(path + " 必须大于 0");
        }
        return value;
    }

    private static TeamfightConfig createConfigFromManager(CapturePointManager manager) {
        List<PlannedPointConfig> points = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (CapturePoint.SerializableCapturePoint point : manager.getOverviewSerializablePoints()) {
            if (seenNames.add(point.name)) {
                points.add(new PlannedPointConfig(point.name, point.batch, point.pos1, point.pos2));
            }
        }
        points.sort(Comparator.comparingInt((PlannedPointConfig point) -> point.batch).thenComparing(point -> point.name));

        int totalBatches = manager.getTotalBatches();
        if (totalBatches <= 0) {
            totalBatches = Math.max(1, manager.calculateTotalBatches());
        }

        String endBehavior = manager.getEndBehavior();
        if (endBehavior == null || (!"terminate".equalsIgnoreCase(endBehavior) && !"loop".equalsIgnoreCase(endBehavior))) {
            endBehavior = "terminate";
        }

        Map<String, Integer> reinforcements = defaultReinforcements();
        reinforcements.put(EspetroTeamBridge.ATTACK, positiveOrDefault(
                manager.getTeamInitialReinforcements(EspetroTeamBridge.ATTACK),
                manager.getTeamReinforcements(EspetroTeamBridge.ATTACK)
        ));
        reinforcements.put(EspetroTeamBridge.DEFEND, positiveOrDefault(
                manager.getTeamInitialReinforcements(EspetroTeamBridge.DEFEND),
                manager.getTeamReinforcements(EspetroTeamBridge.DEFEND)
        ));

        return new TeamfightConfig(totalBatches, endBehavior.toLowerCase(Locale.ROOT), reinforcements, points,
            manager.getAttackBatchCompletionReinforcement(),
            manager.isRaasFrontline(),
            manager.getCaptureReinforcement(),
            manager.getTicketBleedPerSecond());
    }

    private static int positiveOrDefault(int preferred, int fallback) {
        if (preferred > 0) {
            return preferred;
        }
        if (fallback > 0) {
            return fallback;
        }
        return DEFAULT_REINFORCEMENTS;
    }

    private static void writeConfig(Path configPath, TeamfightConfig config) throws IOException {
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(toJsonObject(config), writer);
        }
    }

    private static JsonObject toJsonObject(TeamfightConfig config) {
        JsonObject root = new JsonObject();
        root.addProperty("totalBatches", config.totalBatches);
        root.addProperty("endBehavior", config.endBehavior);
        root.addProperty("attackBatchCompletionReinforcement",
            config.attackBatchCompletionReinforcement);
        root.addProperty("captureReinforcement", config.captureReinforcement);
        root.addProperty("ticketBleedPerSecond", config.ticketBleedPerSecond);
        if (config.raasFrontline) {
            root.addProperty("raasFrontline", true);
        }

        JsonObject reinforcements = new JsonObject();
        reinforcements.addProperty(EspetroTeamBridge.ATTACK,
                config.teamReinforcements.getOrDefault(EspetroTeamBridge.ATTACK, DEFAULT_REINFORCEMENTS));
        reinforcements.addProperty(EspetroTeamBridge.DEFEND,
                config.teamReinforcements.getOrDefault(EspetroTeamBridge.DEFEND, DEFAULT_REINFORCEMENTS));
        root.add("teamReinforcements", reinforcements);

        JsonArray points = new JsonArray();
        List<PlannedPointConfig> sortedPoints = new ArrayList<>(config.points);
        sortedPoints.sort(Comparator.comparingInt((PlannedPointConfig point) -> point.batch).thenComparing(point -> point.name));
        for (PlannedPointConfig point : sortedPoints) {
            JsonObject pointJson = new JsonObject();
            pointJson.addProperty("name", point.name);
            pointJson.addProperty("batch", point.batch);
            pointJson.add("pos1", toJsonObject(point.pos1));
            pointJson.add("pos2", toJsonObject(point.pos2));
            points.add(pointJson);
        }
        root.add("plannedPoints", points);
        return root;
    }

    private static JsonObject toJsonObject(BlockPos pos) {
        JsonObject object = new JsonObject();
        object.addProperty("x", pos.getX());
        object.addProperty("y", pos.getY());
        object.addProperty("z", pos.getZ());
        return object;
    }

    private static BlockPos parseBlockPos(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            throw new JsonParseException("坐标不能为空");
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() != 3) {
                throw new JsonParseException("坐标数组必须包含 x/y/z 三个数字");
            }
            return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return new BlockPos(getRequiredInt(object, "x"), getRequiredInt(object, "y"), getRequiredInt(object, "z"));
        }

        throw new JsonParseException("坐标必须是对象或数组");
    }

    private static JsonElement firstPresent(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return object.get(key);
            }
        }
        return null;
    }

    private static int getRequiredInt(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new JsonParseException("缺少必填数字字段: " + key);
        }
        return object.get(key).getAsInt();
    }

    private static int getOptionalInt(JsonObject object, String key, int defaultValue) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        return object.get(key).getAsInt();
    }

    private static String getOptionalString(JsonObject object, String key, String defaultValue) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        return object.get(key).getAsString();
    }

    private static boolean getOptionalBoolean(JsonObject object, String key, boolean defaultValue) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return defaultValue;
        }
        JsonElement element = object.get(key);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return Boolean.parseBoolean(element.getAsString());
        }
        return defaultValue;
    }

    public static Path getConfigPath() {
        return snapshotPath(frozenMapId);
    }

    private static Path snapshotPath(String mapId) {
        String safeMap = mapId == null || mapId.isBlank() ? "inactive"
            : mapId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return Paths.get("EsWorld", safeMap, "EsConfig", "CapturePoints.json");
    }

    private static Path getExportDirectory() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Path root = server == null ? Paths.get(".") : server.getServerDirectory().toPath();
        return root.resolve("config").resolve(CONFIG_DIRECTORY).resolve("exports");
    }

    private static Map<String, Integer> defaultReinforcements() {
        Map<String, Integer> reinforcements = new HashMap<>();
        reinforcements.put(EspetroTeamBridge.ATTACK, DEFAULT_REINFORCEMENTS);
        reinforcements.put(EspetroTeamBridge.DEFEND, DEFAULT_REINFORCEMENTS);
        return reinforcements;
    }

    private static final class TeamfightConfig {
        private final int totalBatches;
        private final String endBehavior;
        private final Map<String, Integer> teamReinforcements;
        private final List<PlannedPointConfig> points;
        private final int attackBatchCompletionReinforcement;
        private final boolean raasFrontline;
        private final int captureReinforcement;
        private final int ticketBleedPerSecond;

        private TeamfightConfig(int totalBatches, String endBehavior,
                                Map<String, Integer> teamReinforcements,
                                List<PlannedPointConfig> points,
                                int attackBatchCompletionReinforcement,
                                boolean raasFrontline,
                                int captureReinforcement,
                                int ticketBleedPerSecond) {
            this.totalBatches = totalBatches;
            this.endBehavior = endBehavior;
            this.teamReinforcements = teamReinforcements;
            this.points = points;
            this.attackBatchCompletionReinforcement = Math.max(0, attackBatchCompletionReinforcement);
            this.raasFrontline = raasFrontline;
            this.captureReinforcement = Math.max(0, captureReinforcement);
            this.ticketBleedPerSecond = Math.max(0, ticketBleedPerSecond);
        }
    }

    private static final class PlannedPointConfig {
        private final String name;
        private final int batch;
        private final BlockPos pos1;
        private final BlockPos pos2;

        private PlannedPointConfig(String name, int batch, BlockPos pos1, BlockPos pos2) {
            this.name = name;
            this.batch = batch;
            this.pos1 = pos1;
            this.pos2 = pos2;
        }
    }

    public static final class LoadResult {
        private final boolean success;
        private final Path path;
        private final String message;
        private final int plannedPointCount;
        private final int totalBatches;
        private final String endBehavior;

        private LoadResult(boolean success, Path path, String message,
                           int plannedPointCount, int totalBatches, String endBehavior) {
            this.success = success;
            this.path = path;
            this.message = message;
            this.plannedPointCount = plannedPointCount;
            this.totalBatches = totalBatches;
            this.endBehavior = endBehavior;
        }

        private static LoadResult success(Path path, int plannedPointCount, int totalBatches, String endBehavior) {
            return new LoadResult(true, path, "", plannedPointCount, totalBatches, endBehavior);
        }

        private static LoadResult failure(Path path, String message) {
            return new LoadResult(false, path, message == null ? "未知错误" : message, 0, 0, "");
        }

        public boolean isSuccess() {
            return success;
        }

        public Path getPath() {
            return path;
        }

        public String getMessage() {
            return message;
        }

        public int getPlannedPointCount() {
            return plannedPointCount;
        }

        public int getTotalBatches() {
            return totalBatches;
        }

        public String getEndBehavior() {
            return endBehavior;
        }
    }
}
