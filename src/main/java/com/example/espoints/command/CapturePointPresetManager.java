package com.example.espoints.command;

import com.example.espoints.util.ModLogger;

/**
 * 普通据点预设管理器
 * 负责保存和加载普通据点的预设，包括据点位置、名称等信息
 */
public class CapturePointPresetManager {
    /**
     * 保存普通据点预设
     * @param presetId 预设ID
     * @return 是否保存成功
     */
    public static boolean savePreset(int presetId) {
        ModLogger.warn("普通据点预设已移除，请使用行动模式预设 /hcrpi teamfight save");
        return false;
    }
    
    /**
     * 加载普通据点预设
     * @param presetId 预设ID
     * @return 是否加载成功
     */
    public static boolean loadPreset(int presetId) {
        ModLogger.warn("普通据点预设已移除，请使用行动模式预设 /hcrpi teamfight load");
        return false;
    }
}
