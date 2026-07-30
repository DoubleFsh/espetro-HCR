package com.example.espoints.tactical;

import com.example.espoints.ESPointsMod;
import net.minecraft.resources.ResourceLocation;

/** 战术标点 type → 贴图（地图 2D 与世界 3D 共用）。 */
public final class TacticalMarkerIcons {

    public static final ResourceLocation EN_SOLDIER =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_soldier.png");
    /** 敌方单位统一红色贴图（轮盘 / 地图 / 3D 共用）。 */
    public static final ResourceLocation EN_TANK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_tank.png");
    public static final ResourceLocation EN_IFV =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_ifv.png");
    public static final ResourceLocation EN_TRUCK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_truck.png");
    public static final ResourceLocation EN_HELI =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/en_heli.png");
    public static final ResourceLocation MARK_ATTACK =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/mark_attack.png");
    public static final ResourceLocation MARK_DEFEND =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/mark_defend.png");
    public static final ResourceLocation ACP =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "textures/gui/map/acp.png");

    /** 敌方单位标点统一红（ARGB）。 */
    public static final int ENEMY_RED = 0xFFE05252;

    private TacticalMarkerIcons() {
    }

    public static ResourceLocation textureFor(TacticalMarkerType type) {
        if (type == null) {
            return EN_SOLDIER;
        }
        return switch (type) {
            case ENEMY_INFANTRY -> EN_SOLDIER;
            case ENEMY_TANK -> EN_TANK;
            case ENEMY_IFV -> EN_IFV;
            case ENEMY_LIGHT_VEHICLE -> EN_TRUCK;
            case ENEMY_HELICOPTER -> EN_HELI;
            case ATTACK_HERE -> MARK_ATTACK;
            case DEFEND_HERE -> MARK_DEFEND;
            case ARTILLERY_TARGET -> ACP;
        };
    }

    /** 是否为敌方单位类标点（不含进攻/防守指令标）。 */
    public static boolean isEnemyUnit(TacticalMarkerType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                 ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> true;
            default -> false;
        };
    }
}
