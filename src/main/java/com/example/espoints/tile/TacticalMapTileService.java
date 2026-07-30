package com.example.espoints.tile;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.espetro.api.ActiveBattlefieldSnapshot;
import org.espetro.api.EspetroAPI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async SHA-addressed server pyramid. Decode, resize and disk IO are confined
 * to daemon workers; the server thread only validates requests and sends bytes.
 */
public final class TacticalMapTileService {
    public static final int MAX_ENCODED_TILE_BYTES = 2 * 1024 * 1024;
    static final String PYRAMID_CACHE_VERSION = "p2";
    private static final TacticalMapTileService INSTANCE = new TacticalMapTileService();

    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ESPoints-TacticalMapTile");
        thread.setDaemon(true);
        return thread;
    });
    private final InFlightTaskRegistry<TacticalMapTileKey, byte[]> inFlight =
        new InFlightTaskRegistry<>();
    private final WeightedLruCache<TacticalMapTileKey, byte[]> memory =
        new WeightedLruCache<>(32L * 1024L * 1024L, bytes -> bytes.length);
    private final TileTransferLimiter transferLimiter = new TileTransferLimiter();
    private volatile ActiveState active;

    private TacticalMapTileService() {
    }

    public static TacticalMapTileService get() {
        return INSTANCE;
    }

    public synchronized void activate(ActiveBattlefieldSnapshot snapshot) {
        clear();
        if (snapshot == null
            || snapshot.backgroundSha256().isBlank()
            || !snapshot.backgroundSha256().matches("[0-9a-f]{64}")
            || snapshot.backgroundWidth() <= 0
            || snapshot.backgroundHeight() <= 0) {
            return;
        }
        TacticalMapPyramidLayout layout =
            new TacticalMapPyramidLayout(snapshot.backgroundWidth(), snapshot.backgroundHeight());
        long session = EspetroAPI.getTacticalMapStateSnapshot().battlefieldSession();
        Path root = FMLPaths.CONFIGDIR.get()
            .resolve("espoints").resolve("cache").resolve("tactical-map");
        Path mapDirectory = root.resolve(cacheDirectoryName(snapshot.backgroundSha256()));
        ActiveState state = new ActiveState(
            new Descriptor(session, snapshot.backgroundImage(), snapshot.backgroundSha256(),
                layout.width(), layout.height(), TacticalMapPyramidLayout.TILE_SIZE,
                layout.maxLevel()),
            layout,
            mapDirectory,
            snapshot.backgroundBytes());
        active = state;
        memory.setMaximumWeight(
            Math.max(8L, ModConfig.tacticalMapServerMemoryMiB.get()) * 1024L * 1024L);
        state.build = CompletableFuture.runAsync(() -> buildPyramid(state, root), executor);
        // Coarsest preview is generated first by buildPyramid.
        request(session, layout.maxLevel(), 0, 0);
    }

    public synchronized void clear() {
        active = null;
        inFlight.clear();
        memory.clear();
        transferLimiter.clear();
    }

    public Descriptor descriptor() {
        ActiveState state = active;
        return state == null ? Descriptor.EMPTY : state.descriptor;
    }

    public CompletableFuture<byte[]> request(long session, int level, int x, int y) {
        ActiveState state = active;
        if (state == null || state.descriptor.session() != session
            || !state.layout.isValid(level, x, y)) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid/stale tactical tile request"));
        }
        TacticalMapTileKey key = new TacticalMapTileKey(session, level, x, y);
        byte[] cached = memory.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return inFlight.getOrStart(key, () ->
            CompletableFuture.supplyAsync(() -> {
                state.build.join();
                if (active != state) {
                    throw new IllegalStateException("Stale tactical map session");
                }
                Path tile = tilePath(state.mapDirectory, level, x, y);
                try {
                    byte[] bytes = Files.readAllBytes(tile);
                    if (bytes.length <= 0 || bytes.length > MAX_ENCODED_TILE_BYTES) {
                        throw new IOException("Encoded tactical tile size is invalid");
                    }
                    memory.put(key, bytes);
                    return bytes;
                } catch (IOException error) {
                    throw new IllegalStateException("Unable to read tactical tile", error);
                }
            }, executor));
    }

    public boolean allowTransfer(java.util.UUID playerId, int bytes) {
        long playerBudget =
            ModConfig.tacticalMapPlayerTransferKiBps.get() * 1024L;
        long globalBudget =
            ModConfig.tacticalMapGlobalTransferKiBps.get() * 1024L;
        return transferLimiter.allow(
            playerId, bytes, System.currentTimeMillis(), playerBudget, globalBudget);
    }

    public int tileWidth(int level, int x) {
        ActiveState state = active;
        return state == null ? 0 : state.layout.tileWidth(level, x);
    }

    public int tileHeight(int level, int y) {
        ActiveState state = active;
        return state == null ? 0 : state.layout.tileHeight(level, y);
    }

    private void buildPyramid(ActiveState state, Path root) {
        try {
            Files.createDirectories(state.mapDirectory);
            Path complete = state.mapDirectory.resolve("complete");
            if (Files.isRegularFile(complete)) {
                state.sourceBytes = new byte[0];
                return;
            }
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(state.sourceBytes));
            state.sourceBytes = new byte[0];
            if (source == null
                || source.getWidth() != state.layout.width()
                || source.getHeight() != state.layout.height()) {
                throw new IOException("Decoded tactical map dimensions do not match descriptor");
            }

            // Preview first, then progressively sharper levels.
            for (int level = state.layout.maxLevel(); level >= 0; level--) {
                if (active != state) {
                    return;
                }
                BufferedImage scaled = level == 0
                    ? source
                    : TacticalMapImageScaler.scale(source, state.layout.levelWidth(level),
                        state.layout.levelHeight(level));
                writeLevel(state, scaled, level);
                if (scaled != source) {
                    scaled.flush();
                }
            }
            Files.writeString(complete, state.descriptor.sha256());
            Files.setLastModifiedTime(state.mapDirectory, FileTime.fromMillis(System.currentTimeMillis()));
            pruneDiskCache(root,
                ModConfig.tacticalMapDiskCacheMiB.get() * 1024L * 1024L,
                state.mapDirectory);
            source.flush();
        } catch (IOException error) {
            ESPointsMod.LOGGER.error("战术地图瓦片金字塔生成失败: {}",
                state.descriptor.imagePath(), error);
            throw new IllegalStateException(error);
        }
    }

    private void writeLevel(ActiveState state, BufferedImage image, int level)
            throws IOException {
        for (int y = 0; y < state.layout.rows(level); y++) {
            for (int x = 0; x < state.layout.columns(level); x++) {
                int width = state.layout.tileWidth(level, x);
                int height = state.layout.tileHeight(level, y);
                BufferedImage tile = image.getSubimage(
                    x * TacticalMapPyramidLayout.TILE_SIZE,
                    y * TacticalMapPyramidLayout.TILE_SIZE,
                    width,
                    height);
                Path target = tilePath(state.mapDirectory, level, x, y);
                Files.createDirectories(target.getParent());
                if (!Files.isRegularFile(target)) {
                    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
                    if (!ImageIO.write(tile, "PNG", temporary.toFile())) {
                        throw new IOException("No PNG writer available");
                    }
                    long size = Files.size(temporary);
                    if (size <= 0L || size > MAX_ENCODED_TILE_BYTES) {
                        Files.deleteIfExists(temporary);
                        throw new IOException("Encoded tile exceeds size limit");
                    }
                    try {
                        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    static String cacheDirectoryName(String sha256) {
        return sha256 + "-" + PYRAMID_CACHE_VERSION;
    }

    private static Path tilePath(Path mapDirectory, int level, int x, int y) {
        return mapDirectory.resolve("l" + level).resolve(x + "_" + y + ".png");
    }

    private static void pruneDiskCache(Path root, long budget, Path keep) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> files;
        try (var stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile).toList();
        }
        long total = 0L;
        for (Path file : files) {
            total += Files.size(file);
        }
        if (total <= budget) {
            return;
        }
        List<Path> directories;
        try (var stream = Files.list(root)) {
            directories = stream.filter(Files::isDirectory)
                .filter(path -> !path.equals(keep))
                .sorted(Comparator.comparingLong(TacticalMapTileService::lastModified))
                .toList();
        }
        for (Path directory : directories) {
            if (total <= budget) {
                break;
            }
            long removed = directorySize(directory);
            deleteDirectory(directory);
            total -= removed;
        }
    }

    private static long directorySize(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException ignored) {
                    return 0L;
                }
            }).sum();
        }
    }

    private static void deleteDirectory(Path directory) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(directory)) {
            paths = new ArrayList<>(stream.sorted(Comparator.reverseOrder()).toList());
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    public record Descriptor(long session, String imagePath, String sha256,
                             int width, int height, int tileSize, int maxLevel) {
        public static final Descriptor EMPTY =
            new Descriptor(0L, "", "", 0, 0, TacticalMapPyramidLayout.TILE_SIZE, 0);

        public boolean present() {
            return session > 0L && !sha256.isBlank() && width > 0 && height > 0;
        }
    }

    private static final class ActiveState {
        private final Descriptor descriptor;
        private final TacticalMapPyramidLayout layout;
        private final Path mapDirectory;
        private volatile byte[] sourceBytes;
        private CompletableFuture<Void> build = CompletableFuture.completedFuture(null);

        private ActiveState(Descriptor descriptor, TacticalMapPyramidLayout layout,
                            Path mapDirectory, byte[] sourceBytes) {
            this.descriptor = descriptor;
            this.layout = layout;
            this.mapDirectory = mapDirectory;
            this.sourceBytes = sourceBytes;
        }
    }
}
