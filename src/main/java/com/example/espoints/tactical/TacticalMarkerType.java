package com.example.espoints.tactical;

import java.util.Arrays;

/** 指挥官、小队/火力组组长及合法载具席位可放置的战术标点类型。 */
public enum TacticalMarkerType {
    ENEMY_INFANTRY("敌方步兵", 0xFFE05252),
    ENEMY_TANK("敌方坦克", 0xFFD83A3A),
    ENEMY_IFV("敌方步战", 0xFFE66A42),
    ENEMY_LIGHT_VEHICLE("敌方轻型载具", 0xFFF08A4B),
    ENEMY_HELICOPTER("敌方直升机", 0xFFE44F87),
    /** 进攻/防守标：不随时间过期，仅放置者可删。 */
    ATTACK_HERE("进攻该处", 0xFFFFB52E, true, true),
    DEFEND_HERE("防守该处", 0xFF4D9DFF, true, true),
    ARTILLERY_TARGET("155炮击点", 0xFFFF3B30, false, false);

    private final String displayName;
    private final int color;
    private final boolean selectableFromMenu;
    private final boolean persistentUntilRemoved;

    TacticalMarkerType(String displayName, int color) {
        this(displayName, color, true, false);
    }

    TacticalMarkerType(String displayName, int color, boolean selectableFromMenu) {
        this(displayName, color, selectableFromMenu, false);
    }

    TacticalMarkerType(String displayName, int color, boolean selectableFromMenu,
                       boolean persistentUntilRemoved) {
        this.displayName = displayName;
        this.color = color;
        this.selectableFromMenu = selectableFromMenu;
        this.persistentUntilRemoved = persistentUntilRemoved;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    public boolean isSelectableFromMenu() {
        return selectableFromMenu;
    }

    /** 不参与 lifetime 清理，仅放置者手动删除（或战场 reset）。 */
    public boolean isPersistentUntilRemoved() {
        return persistentUntilRemoved;
    }

    public static TacticalMarkerType[] selectableValues() {
        return Arrays.stream(values())
            .filter(TacticalMarkerType::isSelectableFromMenu)
            .toArray(TacticalMarkerType[]::new);
    }

    public static TacticalMarkerType fromNetworkId(int id) {
        TacticalMarkerType[] values = values();
        return id >= 0 && id < values.length ? values[id] : null;
    }
}
