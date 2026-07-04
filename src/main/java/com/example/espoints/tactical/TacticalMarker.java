package com.example.espoints.tactical;

import java.util.UUID;

/** 服务端生成、仅向同阵营同步的战术标点。 */
public record TacticalMarker(UUID id, TacticalMarkerType type, double x, double z,
                             String team, UUID ownerId, String ownerName, long createdAtMillis) {
}
