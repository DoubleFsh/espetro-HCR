package com.example.espoints.tactical;

import com.example.espoints.client.PingWheelMarkerBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 客户端标点缓存：地图 HUD 与 3D 世界渲染共用。 */
public final class ClientTacticalMarkerState {

    private static List<TacticalMarker> markers = List.of();

    private ClientTacticalMarkerState() {
    }

    public static void setMarkers(List<TacticalMarker> next) {
        markers = next == null ? List.of() : List.copyOf(next);
        PingWheelMarkerBridge.replaceSnapshot(markers);
    }

    public static void applyDelta(boolean clear, List<UUID> removals,
                                  List<TacticalMarker> additions) {
        ArrayList<TacticalMarker> next = clear
            ? new ArrayList<>()
            : new ArrayList<>(markers);
        if (clear) {
            PingWheelMarkerBridge.clear();
        }
        if (removals != null && !removals.isEmpty()) {
            for (UUID id : removals) {
                next.removeIf(marker -> marker.id().equals(id));
                PingWheelMarkerBridge.remove(id);
            }
        }
        if (additions != null) {
            for (TacticalMarker marker : additions) {
                next.removeIf(existing -> existing.id().equals(marker.id()));
                next.add(marker);
                PingWheelMarkerBridge.add(marker, true);
            }
        }
        markers = List.copyOf(next);
    }

    public static List<TacticalMarker> getMarkers() {
        return markers;
    }

    public static void clear() {
        markers = List.of();
        PingWheelMarkerBridge.clear();
    }
}
