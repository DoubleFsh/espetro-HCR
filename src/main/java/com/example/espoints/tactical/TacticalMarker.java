package com.example.espoints.tactical;

import java.util.UUID;

/** 服务端生成、仅向同阵营同步的战术标点（含 3D 世界高度 y）。 */
public record TacticalMarker(
        UUID id,
        TacticalMarkerType type,
        double x,
        double y,
        double z,
        String team,
        UUID ownerId,
        String ownerName,
        long createdAtMillis,
        /** 放置者小队编号；无小队为 -1。 */
        int ownerSquadId,
        /** 放置时是否为指挥官。 */
        boolean ownerCommander
) {
    public static final int NO_SQUAD = -1;

    /** 兼容旧构造：无 y / 小队信息。 */
    public TacticalMarker(UUID id, TacticalMarkerType type, double x, double z,
                          String team, UUID ownerId, String ownerName, long createdAtMillis) {
        this(id, type, x, 0.0D, z, team, ownerId, ownerName, createdAtMillis, NO_SQUAD, false);
    }

    public TacticalMarker(UUID id, TacticalMarkerType type, double x, double z,
                          String team, UUID ownerId, String ownerName, long createdAtMillis,
                          int ownerSquadId, boolean ownerCommander) {
        this(id, type, x, 0.0D, z, team, ownerId, ownerName, createdAtMillis, ownerSquadId, ownerCommander);
    }
}
