package com.example.espoints.config;

import com.example.espoints.util.ModLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * Runtime settings frozen from the active Espetro map.
 */
public final class TacticalMapJsonConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static TacticalMapJsonConfig instance;

    public int topLeftX = -512;
    public int topLeftZ = -512;
    public int bottomRightX = 512;
    public int bottomRightZ = 512;
    public int initialRange = 512;
    public int minimumRange = 64;
    public String backgroundImage = "";
    public int backgroundImageWidth = 0;
    public int backgroundImageHeight = 0;
    public boolean showGrid = true;
    public boolean showLabels = true;
    /** 非 persistent 标点存活秒数（3D/地图共用）。 */
    public int tacticalMarkerDurationSeconds = 10;
    /** 末尾淡出秒数（opacity = remaining/fade）。 */
    public int tacticalMarkerFadeSeconds = 3;
    /** 3D 世界最大渲染距离（方块）；超出不绘制。 */
    public int tacticalMarkerMaxRenderDistance = 128;

    private transient String source = "internal defaults";

    private TacticalMapJsonConfig() {
    }

    public static synchronized TacticalMapJsonConfig getInstance() {
        if (instance == null) {
            instance = createDefault();
        }
        return instance;
    }

    public static TacticalMapJsonConfig createDefault() {
        return new TacticalMapJsonConfig();
    }

    public static TacticalMapJsonConfig fromJson(JsonElement json) {
        try {
            TacticalMapJsonConfig parsed = GSON.fromJson(json, TacticalMapJsonConfig.class);
            TacticalMapJsonConfig config = createDefault();
            if (parsed != null) {
                config.copyFrom(parsed);
            }
            return config;
        } catch (JsonParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JsonParseException("Invalid tactical map config", e);
        }
    }

    public static synchronized void apply(TacticalMapJsonConfig loadedConfig, String source) {
        TacticalMapJsonConfig config = getInstance();
        config.copyFrom(loadedConfig);
        config.source = source == null || source.isBlank() ? "unknown" : source;
        ModLogger.info("战术地图JSON配置已应用: " + config.source);
    }

    public TacticalMapJsonConfig copy() {
        TacticalMapJsonConfig copy = createDefault();
        copy.copyFrom(this);
        copy.source = this.source;
        return copy;
    }

    public void reloadIfChanged() {
        // Per-map settings are immutable for the lifetime of a match.
    }

    public void loadConfig() {
        ModLogger.warn("战术地图由当前 Espetro 地图提供，只能在下一局切换");
    }

    public void saveConfig() {
        ModLogger.warn("活动地图配置为只读，未修改 EsWorld 模板");
    }

    public String getSource() {
        return source;
    }

    public TacticalMapBounds getBounds() {
        double minX = Math.min(topLeftX, bottomRightX);
        double maxX = Math.max(topLeftX, bottomRightX);
        double minZ = Math.min(topLeftZ, bottomRightZ);
        double maxZ = Math.max(topLeftZ, bottomRightZ);

        if (maxX <= minX) {
            maxX = minX + 1.0D;
        }
        if (maxZ <= minZ) {
            maxZ = minZ + 1.0D;
        }

        return new TacticalMapBounds(minX, minZ, maxX, maxZ);
    }

    public double getInitialRange(TacticalMapBounds bounds) {
        return clampRange(initialRange <= 0 ? bounds.size() : initialRange, bounds);
    }

    public double getMinimumRange(TacticalMapBounds bounds) {
        return clampRange(Math.max(1, minimumRange), bounds);
    }

    public long getTacticalMarkerDurationMillis() {
        return Math.max(1, tacticalMarkerDurationSeconds) * 1000L;
    }

    public long getTacticalMarkerFadeMillis() {
        return Math.min(getTacticalMarkerDurationMillis(),
            Math.max(1, tacticalMarkerFadeSeconds) * 1000L);
    }

    public double getTacticalMarkerMaxRenderDistance() {
        return Math.max(16, tacticalMarkerMaxRenderDistance);
    }

    private double clampRange(double range, TacticalMapBounds bounds) {
        return Math.max(1.0D, Math.min(bounds.size(), range));
    }

    private void copyFrom(TacticalMapJsonConfig loadedConfig) {
        if (loadedConfig == null) {
            return;
        }
        this.topLeftX = loadedConfig.topLeftX;
        this.topLeftZ = loadedConfig.topLeftZ;
        this.bottomRightX = loadedConfig.bottomRightX;
        this.bottomRightZ = loadedConfig.bottomRightZ;
        this.initialRange = loadedConfig.initialRange;
        this.minimumRange = loadedConfig.minimumRange;
        this.backgroundImage = loadedConfig.backgroundImage == null ? "" : loadedConfig.backgroundImage;
        this.backgroundImageWidth = Math.max(0, loadedConfig.backgroundImageWidth);
        this.backgroundImageHeight = Math.max(0, loadedConfig.backgroundImageHeight);
        this.showGrid = loadedConfig.showGrid;
        this.showLabels = loadedConfig.showLabels;
        this.tacticalMarkerDurationSeconds = Math.max(1, loadedConfig.tacticalMarkerDurationSeconds);
        this.tacticalMarkerFadeSeconds = Math.max(1, loadedConfig.tacticalMarkerFadeSeconds);
        this.tacticalMarkerMaxRenderDistance = Math.max(16, loadedConfig.tacticalMarkerMaxRenderDistance);
    }

    public static final class TacticalMapBounds {
        public final double minX;
        public final double minZ;
        public final double maxX;
        public final double maxZ;

        private TacticalMapBounds(double minX, double minZ, double maxX, double maxZ) {
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
        }

        public double size() {
            return Math.max(width(), height());
        }

        public double width() {
            return maxX - minX;
        }

        public double height() {
            return maxZ - minZ;
        }

        public double aspectRatio() {
            return width() / height();
        }

        public double centerX() {
            return (minX + maxX) / 2.0D;
        }

        public double centerZ() {
            return (minZ + maxZ) / 2.0D;
        }

        public boolean contains(double x, double z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public TacticalMapBounds expandToInclude(double x, double z, double padding) {
            double safePadding = Math.max(0.0D, padding);
            return new TacticalMapBounds(
                Math.min(minX, x - safePadding),
                Math.min(minZ, z - safePadding),
                Math.max(maxX, x + safePadding),
                Math.max(maxZ, z + safePadding)
            );
        }
    }
}
