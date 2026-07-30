package com.example.espoints.capturepoint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chunk-keyed point candidates; oversized regions fall back without forcing chunks. */
final class CapturePointSpatialIndex {
    private static final long MAX_CHUNKS_PER_POINT = 65_536L;
    private final Map<Long, List<CapturePoint>> byChunk = new HashMap<>();
    private List<CapturePoint> oversized = List.of();

    void rebuild(Collection<CapturePoint> points) {
        byChunk.clear();
        List<CapturePoint> large = new ArrayList<>();
        for (CapturePoint point : points) {
            int minChunkX = Math.min(point.getPos1().getX(), point.getPos2().getX()) >> 4;
            int maxChunkX = Math.max(point.getPos1().getX(), point.getPos2().getX()) >> 4;
            int minChunkZ = Math.min(point.getPos1().getZ(), point.getPos2().getZ()) >> 4;
            int maxChunkZ = Math.max(point.getPos1().getZ(), point.getPos2().getZ()) >> 4;
            long chunkCount = (long) maxChunkX - minChunkX + 1L;
            chunkCount *= (long) maxChunkZ - minChunkZ + 1L;
            if (chunkCount <= 0L || chunkCount > MAX_CHUNKS_PER_POINT) {
                large.add(point);
                continue;
            }
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    byChunk.computeIfAbsent(
                        ChunkPos.asLong(chunkX, chunkZ), ignored -> new ArrayList<>())
                        .add(point);
                }
            }
        }
        oversized = List.copyOf(large);
    }

    List<CapturePoint> candidates(BlockPos pos) {
        List<CapturePoint> local = byChunk.get(
            ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (oversized.isEmpty()) {
            return local == null ? List.of() : local;
        }
        if (local == null || local.isEmpty()) {
            return oversized;
        }
        List<CapturePoint> result = new ArrayList<>(local.size() + oversized.size());
        result.addAll(local);
        result.addAll(oversized);
        return result;
    }
}
