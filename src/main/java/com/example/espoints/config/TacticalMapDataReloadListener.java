package com.example.espoints.config;

import com.example.espoints.util.ModLogger;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;

/**
 * Loads the tactical map config from datapacks.
 */
public class TacticalMapDataReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public TacticalMapDataReloadListener() {
        super(GSON, TacticalMapJsonConfig.DATA_DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonFiles, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        JsonElement json = jsonFiles.get(TacticalMapJsonConfig.CONFIG_ID);
        if (json == null) {
            TacticalMapJsonConfig.apply(TacticalMapJsonConfig.createDefault(), "internal defaults");
            ModLogger.warn("未找到战术地图数据包配置 " + TacticalMapJsonConfig.CONFIG_ID + "，已使用内置默认值");
            return;
        }

        try {
            TacticalMapJsonConfig.apply(
                TacticalMapJsonConfig.fromJson(json),
                TacticalMapJsonConfig.CONFIG_ID.toString()
            );
        } catch (JsonParseException e) {
            TacticalMapJsonConfig.apply(TacticalMapJsonConfig.createDefault(), "internal defaults");
            ModLogger.error("解析战术地图数据包配置失败，已使用内置默认值: " + e.getMessage());
        }
    }
}
