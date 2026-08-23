package com.example.espoints.objective;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Objective layout from a Points preset / CapturePoints document.
 *
 * <p>{@code plannedPoints} remains the AAS route. RAAS adds a point pool and
 * named lanes. Each lane stage lists one or more point ids; <b>all</b> ids in a
 * stage are included (same frontline stage). Branching uses multiple lanes.
 * At round start RAAS reduces to plannedPoints with {@code batch = stage index}
 * and {@code raasFrontline: true} for bidirectional frontline runtime.</p>
 */
public final class ObjectiveLayout {

    private static final Gson GSON = new Gson();
    public static final int MIN_RAAS_STAGES = 3;
    public static final int MAX_RAAS_STAGES = 26;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]{1,64}");

    public enum Mode {
        AAS,
        RAAS
    }

    public record Selection(String mode, String laneId, long seed, String capturePointsJson) {
    }

    private record Lane(String id, List<List<String>> stages) {
    }

    private final JsonObject source;
    private final Mode mode;
    private final Map<String, JsonObject> points;
    private final List<Lane> lanes;

    private ObjectiveLayout(JsonObject source, Mode mode,
                            Map<String, JsonObject> points, List<Lane> lanes) {
        this.source = source.deepCopy();
        this.mode = mode;
        this.points = Map.copyOf(points);
        this.lanes = List.copyOf(lanes);
    }

    public static ObjectiveLayout parse(JsonObject source) {
        if (source == null) {
            throw new IllegalArgumentException("据点配置根节点不能为空");
        }

        Mode mode = readMode(source);
        if (mode != Mode.RAAS) {
            validateAas(source);
        }

        Map<String, JsonObject> points = new LinkedHashMap<>();
        List<Lane> lanes = new ArrayList<>();
        if (mode != Mode.AAS) {
            JsonObject raas = requireObject(source, "raas");
            readPointPool(raas, points);
            readLanes(raas, points.keySet(), lanes);
        }
        return new ObjectiveLayout(source, mode, points, lanes);
    }

    public Selection select(long seed) {
        Random random = new Random(seed);

        if (mode == Mode.AAS) {
            JsonObject round = source.deepCopy();
            stripRouteConfig(round);
            return new Selection("AAS", "", seed, GSON.toJson(round));
        }

        Lane lane = lanes.get(random.nextInt(lanes.size()));
        JsonArray planned = new JsonArray();
        int letter = 0;
        // 每个阶段内的全部据点进入本局；batch = 阶段号（1-based），供对向推线使用。
        int stageNumber = 1;
        for (List<String> stage : lane.stages) {
            for (String pointId : stage) {
                if (letter >= MAX_RAAS_STAGES) {
                    throw new IllegalStateException(
                        "RAAS 路线 " + lane.id + " 展开后超过 " + MAX_RAAS_STAGES + " 个据点");
                }
                JsonObject point = points.get(pointId).deepCopy();
                point.remove("id");
                point.addProperty("name", String.valueOf((char) ('A' + letter++)));
                point.addProperty("batch", stageNumber);
                planned.add(point);
            }
            stageNumber++;
        }

        JsonObject round = source.deepCopy();
        stripRouteConfig(round);
        round.addProperty("totalBatches", lane.stages.size());
        round.addProperty("raasFrontline", true);
        round.remove("raasSymmetric");
        round.add("plannedPoints", planned);
        return new Selection("RAAS", lane.id, seed, GSON.toJson(round));
    }

    public Mode mode() {
        return mode;
    }

    private static Mode readMode(JsonObject source) {
        if (!source.has("objectiveMode")) {
            return Mode.AAS;
        }
        String raw = source.get("objectiveMode").getAsString().trim().toUpperCase(Locale.ROOT);
        if ("AAS".equals(raw) || raw.isEmpty()) {
            return Mode.AAS;
        }
        if ("RAAS".equals(raw)) {
            return Mode.RAAS;
        }
        throw new IllegalArgumentException(
            "objectiveMode 只能是 AAS 或 RAAS（已移除 RANDOM）: " + raw);
    }

    private static void validateAas(JsonObject source) {
        JsonArray planned = requireArray(source, "plannedPoints");
        if (planned.isEmpty()) {
            throw new IllegalArgumentException("plannedPoints 不能为空");
        }
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < planned.size(); i++) {
            JsonObject point = requireObject(planned.get(i), "plannedPoints[" + i + "]");
            String name = requireString(point, "name", "plannedPoints[" + i + "]");
            if (!names.add(name)) {
                throw new IllegalArgumentException("据点配置包含重复据点名称: " + name);
            }
            validateArea(point, "plannedPoints[" + i + "]");
        }
    }

    private static void readPointPool(JsonObject raas, Map<String, JsonObject> points) {
        JsonArray array = requireArray(raas, "points");
        for (int i = 0; i < array.size(); i++) {
            JsonObject point = requireObject(array.get(i), "raas.points[" + i + "]");
            String id = requireString(point, "id", "raas.points[" + i + "]");
            if (!ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException("非法 RAAS 据点 id: " + id);
            }
            if (points.putIfAbsent(id, point.deepCopy()) != null) {
                throw new IllegalArgumentException("重复 RAAS 据点 id: " + id);
            }
            validateArea(point, "raas.points[" + i + "]");
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("raas.points 不能为空");
        }
    }

    private static void readLanes(JsonObject raas, Set<String> pointIds, List<Lane> lanes) {
        JsonArray array = requireArray(raas, "lanes");
        Set<String> laneIds = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            JsonObject object = requireObject(array.get(i), "raas.lanes[" + i + "]");
            String id = requireString(object, "id", "raas.lanes[" + i + "]");
            if (!ID_PATTERN.matcher(id).matches()) {
                throw new IllegalArgumentException("非法 RAAS 路线 id: " + id);
            }
            if (!laneIds.add(id)) {
                throw new IllegalArgumentException("重复 RAAS 路线 id: " + id);
            }

            JsonArray stageArray = requireArray(object, "stages");
            if (stageArray.size() < MIN_RAAS_STAGES || stageArray.size() > MAX_RAAS_STAGES) {
                throw new IllegalArgumentException("RAAS 路线 " + id + " 必须包含 "
                    + MIN_RAAS_STAGES + " 到 " + MAX_RAAS_STAGES + " 个阶段");
            }

            List<List<String>> stages = new ArrayList<>();
            Set<String> used = new LinkedHashSet<>();
            int totalPoints = 0;
            for (int stageIndex = 0; stageIndex < stageArray.size(); stageIndex++) {
                JsonElement stageElement = stageArray.get(stageIndex);
                if (!stageElement.isJsonArray() || stageElement.getAsJsonArray().isEmpty()) {
                    throw new IllegalArgumentException("RAAS 路线 " + id + " 的阶段 "
                        + (stageIndex + 1) + " 不能为空");
                }
                // 阶段内列出的据点全部纳入同一批次，不再随机抽一个。
                List<String> stagePoints = new ArrayList<>();
                for (JsonElement choice : stageElement.getAsJsonArray()) {
                    String pointId = choice.getAsString();
                    if (!pointIds.contains(pointId)) {
                        throw new IllegalArgumentException("RAAS 路线 " + id
                            + " 引用了不存在的据点: " + pointId);
                    }
                    if (!used.add(pointId)) {
                        throw new IllegalArgumentException("RAAS 路线 " + id
                            + " 重复引用据点: " + pointId);
                    }
                    stagePoints.add(pointId);
                }
                totalPoints += stagePoints.size();
                if (totalPoints > MAX_RAAS_STAGES) {
                    throw new IllegalArgumentException("RAAS 路线 " + id
                        + " 展开后据点数不能超过 " + MAX_RAAS_STAGES);
                }
                stages.add(List.copyOf(stagePoints));
            }
            lanes.add(new Lane(id, List.copyOf(stages)));
        }
        if (lanes.isEmpty()) {
            throw new IllegalArgumentException("raas.lanes 不能为空");
        }
    }

    private static void validateArea(JsonObject point, String path) {
        validatePosition(point.get("pos1"), path + ".pos1");
        validatePosition(point.get("pos2"), path + ".pos2");
    }

    private static void validatePosition(JsonElement element, String path) {
        if (element != null && element.isJsonArray()) {
            JsonArray position = element.getAsJsonArray();
            if (position.size() != 3) {
                throw new IllegalArgumentException(path + " 必须包含 x、y、z 三个坐标");
            }
            for (JsonElement coordinate : position) {
                if (!coordinate.isJsonPrimitive()
                    || !coordinate.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException(path + " 坐标必须是数字");
                }
            }
            return;
        }
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(path + " 必须是坐标对象或 [x,y,z] 数组");
        }
        JsonObject position = element.getAsJsonObject();
        for (String axis : List.of("x", "y", "z")) {
            if (!position.has(axis) || !position.get(axis).isJsonPrimitive()
                || !position.getAsJsonPrimitive(axis).isNumber()) {
                throw new IllegalArgumentException(path + "." + axis + " 必须是数字");
            }
        }
    }

    private static JsonObject requireObject(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            throw new IllegalArgumentException(key + " 必须是对象");
        }
        return parent.getAsJsonObject(key);
    }

    private static JsonObject requireObject(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(path + " 必须是对象");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonArray()) {
            throw new IllegalArgumentException(key + " 必须是数组");
        }
        return parent.getAsJsonArray(key);
    }

    private static String requireString(JsonObject object, String key, String path) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()
            || !object.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException(path + "." + key + " 必须是字符串");
        }
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + "." + key + " 不能为空");
        }
        return value;
    }

    private static void stripRouteConfig(JsonObject root) {
        root.remove("objectiveMode");
        root.remove("raas");
    }
}
