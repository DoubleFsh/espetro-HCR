package com.example.espoints.client;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.TacticalMapConfig;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.RequestTacticalMapTileMessage;
import com.example.espoints.tile.TacticalMapLodPlanner;
import com.example.espoints.tile.TacticalMapPyramidLayout;
import com.example.espoints.tile.TacticalMapTileKey;
import com.example.espoints.tile.TacticalMapTileService;
import com.example.espoints.tile.WeightedLruCache;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background PNG decode + render-thread-only registration with a weighted LRU.
 * The coarsest tile is touched as the fallback on every render, so it is retained.
 */
public final class ClientTacticalMapTileCache {
    private static final long REQUEST_RETRY_MILLIS = 1500L;
    private static final ClientTacticalMapTileCache INSTANCE =
        new ClientTacticalMapTileCache();

    private final ExecutorService decodeExecutor =
        Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "ESPoints-TacticalMapDecode");
            thread.setDaemon(true);
            return thread;
        });
    private final WeightedLruCache<TacticalMapTileKey, TextureEntry> textures =
        new WeightedLruCache<>(64L * 1024L * 1024L, TextureEntry::weight);
    private final Map<TacticalMapTileKey, Long> pending = new HashMap<>();
    private final Set<TacticalMapTileKey> decoding = new HashSet<>();
    private TacticalMapTileService.Descriptor descriptor =
        TacticalMapTileService.Descriptor.EMPTY;
    private TacticalMapPyramidLayout layout;

    private ClientTacticalMapTileCache() {
    }

    public static ClientTacticalMapTileCache get() {
        return INSTANCE;
    }

    public synchronized void applyDescriptor(TacticalMapTileService.Descriptor incoming) {
        TacticalMapTileService.Descriptor normalized = incoming == null
            ? TacticalMapTileService.Descriptor.EMPTY : incoming;
        if (descriptor.session() == normalized.session()
            && descriptor.sha256().equals(normalized.sha256())) {
            if (normalized.present()) {
                request(normalized.maxLevel(), 0, 0);
            }
            return;
        }
        releaseAll();
        descriptor = normalized;
        layout = normalized.present()
            ? new TacticalMapPyramidLayout(normalized.width(), normalized.height())
            : null;
        for (TextureEntry evicted : textures.setMaximumWeight(
                Math.max(16L, TacticalMapConfig.tileTextureCacheMiB.get())
                    * 1024L * 1024L)) {
            release(evicted);
        }
        if (normalized.present()) {
            request(normalized.maxLevel(), 0, 0);
        }
    }

    public synchronized TacticalMapTileService.Descriptor descriptor() {
        return descriptor;
    }

    public synchronized TacticalMapPyramidLayout layout() {
        return layout;
    }

    public synchronized TextureEntry texture(int level, int x, int y) {
        if (!descriptor.present()) {
            return null;
        }
        return textures.get(new TacticalMapTileKey(descriptor.session(), level, x, y));
    }

    public synchronized TacticalMapLodPlanner.TileState tileState(
            TacticalMapPyramidLayout.TileCoordinate tile) {
        if (layout == null || tile == null
            || !layout.isValid(tile.level(), tile.x(), tile.y())) {
            return TacticalMapLodPlanner.TileState.MISSING;
        }
        TacticalMapTileKey key =
            new TacticalMapTileKey(descriptor.session(), tile.level(), tile.x(), tile.y());
        if (textures.get(key) != null) {
            return TacticalMapLodPlanner.TileState.READY;
        }
        if (decoding.contains(key)) {
            return TacticalMapLodPlanner.TileState.REQUESTED;
        }
        Long requestedAt = pending.get(key);
        long now = System.currentTimeMillis();
        if (requestedAt != null && now - requestedAt < REQUEST_RETRY_MILLIS) {
            return TacticalMapLodPlanner.TileState.REQUESTED;
        }
        if (requestedAt != null) {
            pending.remove(key);
        }
        return TacticalMapLodPlanner.TileState.MISSING;
    }

    public long textureBudgetBytes() {
        return Math.max(16L, TacticalMapConfig.tileTextureCacheMiB.get())
            * 1024L * 1024L;
    }

    public synchronized void request(int level, int x, int y) {
        if (layout == null || !layout.isValid(level, x, y)) {
            return;
        }
        TacticalMapTileKey key =
            new TacticalMapTileKey(descriptor.session(), level, x, y);
        if (textures.get(key) != null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long requestedAt = pending.get(key);
        if (requestedAt != null && now - requestedAt < REQUEST_RETRY_MILLIS) {
            return;
        }
        if (pending.size() >= 256) {
            pending.entrySet().removeIf(entry -> now - entry.getValue() >= REQUEST_RETRY_MILLIS);
            if (pending.size() >= 256) {
                return;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        pending.put(key, now);
        NetworkHandler.INSTANCE.sendToServer(new RequestTacticalMapTileMessage(
            descriptor.session(), level, x, y));
    }

    public void accept(long session, int level, int x, int y,
                       int width, int height, byte[] encoded) {
        TacticalMapTileKey key = new TacticalMapTileKey(session, level, x, y);
        synchronized (this) {
            if (layout == null || descriptor.session() != session
                || !layout.isValid(level, x, y)
                || layout.tileWidth(level, x) != width
                || layout.tileHeight(level, y) != height) {
                return;
            }
            pending.remove(key);
            if (textures.get(key) != null || decoding.contains(key)) {
                return;
            }
            decoding.add(key);
        }

        CompletableFuture.supplyAsync(() -> decode(encoded, width, height), decodeExecutor)
            .thenAccept(image -> Minecraft.getInstance().execute(() -> register(key, image)))
            .exceptionally(error -> {
                markDecodeFailed(key);
                ESPointsMod.LOGGER.warn("战术地图瓦片解码失败: {}", key, error);
                return null;
            });
    }

    public synchronized void clear() {
        descriptor = TacticalMapTileService.Descriptor.EMPTY;
        layout = null;
        releaseAll();
    }

    private static NativeImage decode(byte[] encoded, int width, int height) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(encoded));
            if (image.getWidth() != width || image.getHeight() != height) {
                image.close();
                throw new IOException("Decoded tile dimensions do not match packet");
            }
            return image;
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private synchronized void register(TacticalMapTileKey key, NativeImage image) {
        decoding.remove(key);
        if (descriptor.session() != key.session() || layout == null) {
            image.close();
            return;
        }
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(true, false);
        ResourceLocation location = Minecraft.getInstance().getTextureManager().register(
            "espoints_tactical_tile_" + key.session() + "_"
                + key.level() + "_" + key.x() + "_" + key.y(),
            texture);
        TextureEntry entry = new TextureEntry(
            location, texture, image.getWidth(), image.getHeight());
        for (TextureEntry evicted : textures.put(key, entry)) {
            release(evicted);
        }
    }

    private void releaseAll() {
        pending.clear();
        decoding.clear();
        for (TextureEntry entry : textures.clear()) {
            release(entry);
        }
    }

    private synchronized void markDecodeFailed(TacticalMapTileKey key) {
        decoding.remove(key);
    }

    private static void release(TextureEntry entry) {
        if (entry != null) {
            Minecraft.getInstance().getTextureManager().release(entry.location);
        }
    }

    public static final class TextureEntry {
        private final ResourceLocation location;
        private final DynamicTexture texture;
        private final int width;
        private final int height;

        private TextureEntry(ResourceLocation location, DynamicTexture texture,
                             int width, int height) {
            this.location = location;
            this.texture = texture;
            this.width = width;
            this.height = height;
        }

        public ResourceLocation location() { return location; }
        public int width() { return width; }
        public int height() { return height; }
        private long weight() { return (long) width * height * 4L; }
    }
}
