package com.example.espoints.network;

import com.example.espoints.ESPointsMod;
import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Syncs the actual tactical map background image selected by the server JSON.
 */
public class SyncTacticalMapBackgroundMessage {
    private static final String TACTICAL_MAP_HUD_CLASS = "com.example.espoints.hud.TacticalMapHUD";
    private static final int CHUNK_SIZE = 240 * 1024;
    private static final int MAX_BACKGROUND_BYTES = 16 * 1024 * 1024;
    private static final Map<String, PendingTransfer> PENDING_TRANSFERS = new ConcurrentHashMap<>();

    private final boolean clear;
    private final String transferId;
    private final String imagePath;
    private final String imageKey;
    private final int totalSize;
    private final int chunkIndex;
    private final int chunkCount;
    private final byte[] chunkData;

    private SyncTacticalMapBackgroundMessage(boolean clear, String transferId, String imagePath, String imageKey,
                                             int totalSize, int chunkIndex, int chunkCount, byte[] chunkData) {
        this.clear = clear;
        this.transferId = transferId == null ? "" : transferId;
        this.imagePath = imagePath == null ? "" : imagePath;
        this.imageKey = imageKey == null ? "" : imageKey;
        this.totalSize = totalSize;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.chunkData = chunkData == null ? new byte[0] : chunkData;
    }

    private static SyncTacticalMapBackgroundMessage clear(String imagePath) {
        return new SyncTacticalMapBackgroundMessage(true, "", imagePath, "", 0, 0, 0, new byte[0]);
    }

    private static SyncTacticalMapBackgroundMessage chunk(BackgroundPayload payload, String transferId,
                                                          int chunkIndex, int chunkCount, byte[] chunkData) {
        return new SyncTacticalMapBackgroundMessage(false, transferId, payload.imagePath(), payload.imageKey(),
            payload.bytes().length, chunkIndex, chunkCount, chunkData);
    }

    public static void encode(SyncTacticalMapBackgroundMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.clear);
        buf.writeUtf(msg.transferId);
        buf.writeUtf(msg.imagePath);
        buf.writeUtf(msg.imageKey);
        if (!msg.clear) {
            buf.writeVarInt(msg.totalSize);
            buf.writeVarInt(msg.chunkIndex);
            buf.writeVarInt(msg.chunkCount);
            buf.writeByteArray(msg.chunkData);
        }
    }

    public static SyncTacticalMapBackgroundMessage decode(FriendlyByteBuf buf) {
        boolean clear = buf.readBoolean();
        String transferId = buf.readUtf();
        String imagePath = buf.readUtf();
        String imageKey = buf.readUtf();
        if (clear) {
            return new SyncTacticalMapBackgroundMessage(true, transferId, imagePath, imageKey, 0, 0, 0, new byte[0]);
        }

        int totalSize = buf.readVarInt();
        int chunkIndex = buf.readVarInt();
        int chunkCount = buf.readVarInt();
        byte[] chunkData = buf.readByteArray(CHUNK_SIZE);
        return new SyncTacticalMapBackgroundMessage(false, transferId, imagePath, imageKey, totalSize, chunkIndex, chunkCount, chunkData);
    }

    public static void handle(SyncTacticalMapBackgroundMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            if (!context.getDirection().getReceptionSide().isClient()) {
                return;
            }

            if (msg.clear) {
                PENDING_TRANSFERS.clear();
                clearHudBackground(msg.imagePath);
                return;
            }

            acceptChunk(msg);
        });
        context.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player) {
        BackgroundPayload payload = loadConfiguredBackgroundPayload();
        if (payload == null) {
            sendClearToPlayer(player);
            return;
        }

        sendPayload(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void broadcastToAll() {
        BackgroundPayload payload = loadConfiguredBackgroundPayload();
        if (payload == null) {
            sendClearToAll();
            return;
        }

        sendPayload(PacketDistributor.ALL.noArg(), payload);
        ModLogger.info("已向所有玩家广播战术地图底图同步消息: " + payload.imagePath());
    }

    private static void sendClearToPlayer(ServerPlayer player) {
        try {
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                clear(TacticalMapJsonConfig.getInstance().backgroundImage)
            );
        } catch (Exception e) {
            ModLogger.error("向玩家发送战术地图底图清除消息失败: " + e.getMessage());
        }
    }

    private static void sendClearToAll() {
        try {
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                clear(TacticalMapJsonConfig.getInstance().backgroundImage));
        } catch (Exception e) {
            ModLogger.error("广播战术地图底图清除消息失败: " + e.getMessage());
        }
    }

    private static void sendPayload(PacketDistributor.PacketTarget target, BackgroundPayload payload) {
        String transferId = UUID.randomUUID().toString();
        int chunkCount = Math.max(1, (payload.bytes().length + CHUNK_SIZE - 1) / CHUNK_SIZE);
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int start = chunkIndex * CHUNK_SIZE;
            int end = Math.min(payload.bytes().length, start + CHUNK_SIZE);
            byte[] chunkData = Arrays.copyOfRange(payload.bytes(), start, end);
            NetworkHandler.INSTANCE.send(target, chunk(payload, transferId, chunkIndex, chunkCount, chunkData));
        }
    }

    private static BackgroundPayload loadConfiguredBackgroundPayload() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        String imagePath = config.backgroundImage == null ? "" : config.backgroundImage.trim();
        if (imagePath.isEmpty()) {
            return null;
        }

        try (InputStream input = openBackgroundInput(imagePath)) {
            if (input == null) {
                ModLogger.warn("战术地图JSON配置了底图但服务端无法读取: " + imagePath);
                return null;
            }

            byte[] bytes = input.readAllBytes();
            if (bytes.length <= 0) {
                ModLogger.warn("战术地图底图为空，已跳过同步: " + imagePath);
                return null;
            }
            if (bytes.length > MAX_BACKGROUND_BYTES) {
                ModLogger.warn("战术地图底图超过同步上限 " + MAX_BACKGROUND_BYTES + " 字节，已跳过: " + imagePath);
                return null;
            }

            String imageKey = "server:" + imagePath + ":" + bytes.length + ":" + Arrays.hashCode(bytes);
            return new BackgroundPayload(imagePath, imageKey, bytes);
        } catch (IOException | InvalidPathException e) {
            ModLogger.warn("读取战术地图底图失败，已跳过同步: " + imagePath + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static InputStream openBackgroundInput(String imagePath) throws IOException {
        ResourceLocation resourceLocation = parseBackgroundResourceLocation(imagePath);
        if (resourceLocation != null) {
            MinecraftServer server = ESPointsMod.getServer();
            if (server != null) {
                ResourceManager resourceManager = server.getResourceManager();
                Optional<Resource> resource = resourceManager.getResource(resourceLocation);
                if (resource.isPresent()) {
                    return resource.get().open();
                }
            }

            InputStream classpathResource = openClasspathAsset(resourceLocation);
            if (classpathResource != null) {
                return classpathResource;
            }
        }

        Path path = resolveServerBackgroundPath(imagePath);
        if (Files.isRegularFile(path)) {
            return Files.newInputStream(path);
        }
        return null;
    }

    private static InputStream openClasspathAsset(ResourceLocation resourceLocation) {
        String resourcePath = "assets/" + resourceLocation.getNamespace() + "/" + resourceLocation.getPath();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        InputStream input = contextLoader == null ? null : contextLoader.getResourceAsStream(resourcePath);
        if (input != null) {
            return input;
        }
        return SyncTacticalMapBackgroundMessage.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    private static ResourceLocation parseBackgroundResourceLocation(String imagePath) {
        String normalized = imagePath.replace('\\', '/').trim();
        if (normalized.startsWith("assets/")) {
            String[] parts = normalized.split("/", 3);
            if (parts.length == 3) {
                return ResourceLocation.tryParse(parts[1] + ":" + parts[2]);
            }
            return null;
        }

        if (normalized.contains(":")) {
            return ResourceLocation.tryParse(normalized);
        }

        if (normalized.startsWith("textures/")) {
            return ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, normalized);
        }

        return null;
    }

    private static Path resolveServerBackgroundPath(String imagePath) {
        Path path = Path.of(imagePath);
        if (path.isAbsolute()) {
            return path;
        }

        MinecraftServer server = ESPointsMod.getServer();
        Path root = server == null ? Path.of(".") : server.getServerDirectory().toPath();
        return root.resolve(imagePath).normalize();
    }

    private static void acceptChunk(SyncTacticalMapBackgroundMessage msg) {
        if (msg.transferId.isBlank()
                || msg.totalSize <= 0
                || msg.totalSize > MAX_BACKGROUND_BYTES
                || msg.chunkCount <= 0
                || msg.chunkIndex < 0
                || msg.chunkIndex >= msg.chunkCount) {
            return;
        }

        PendingTransfer transfer = PENDING_TRANSFERS.computeIfAbsent(msg.transferId,
            id -> new PendingTransfer(msg.imagePath, msg.imageKey, msg.totalSize, msg.chunkCount));
        if (!transfer.accept(msg.chunkIndex, msg.chunkData)) {
            PENDING_TRANSFERS.remove(msg.transferId);
            return;
        }

        if (transfer.isComplete()) {
            PENDING_TRANSFERS.remove(msg.transferId);
            applyHudBackground(transfer.imagePath, transfer.imageKey, transfer.combine());
        }
    }

    private static void applyHudBackground(String imagePath, String imageKey, byte[] bytes) {
        try {
            Class<?> hudClass = Class.forName(TACTICAL_MAP_HUD_CLASS);
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            Method method = hudClass.getMethod("applySyncedBackgroundImage", String.class, String.class, byte[].class);
            method.invoke(hud, imagePath, imageKey, bytes);
            ModLogger.info("客户端战术地图底图已同步: " + imagePath);
        } catch (ReflectiveOperationException e) {
            ModLogger.warn("应用战术地图底图同步失败: " + e.getMessage());
        }
    }

    private static void clearHudBackground(String imagePath) {
        try {
            Class<?> hudClass = Class.forName(TACTICAL_MAP_HUD_CLASS);
            Object hud = hudClass.getMethod("getInstance").invoke(null);
            hudClass.getMethod("clearSyncedBackgroundImage", String.class).invoke(hud, imagePath);
        } catch (ReflectiveOperationException e) {
            ModLogger.warn("清除战术地图底图同步失败: " + e.getMessage());
        }
    }

    private record BackgroundPayload(String imagePath, String imageKey, byte[] bytes) {
    }

    private static final class PendingTransfer {
        private final String imagePath;
        private final String imageKey;
        private final int totalSize;
        private final byte[][] chunks;
        private int receivedCount;
        private int receivedBytes;

        private PendingTransfer(String imagePath, String imageKey, int totalSize, int chunkCount) {
            this.imagePath = imagePath;
            this.imageKey = imageKey;
            this.totalSize = totalSize;
            this.chunks = new byte[chunkCount][];
        }

        private boolean accept(int chunkIndex, byte[] chunkData) {
            if (chunkData == null || chunkData.length <= 0 || chunks[chunkIndex] != null) {
                return true;
            }

            if (receivedBytes + chunkData.length > totalSize) {
                return false;
            }

            chunks[chunkIndex] = chunkData;
            receivedBytes += chunkData.length;
            receivedCount++;
            return true;
        }

        private boolean isComplete() {
            return receivedCount == chunks.length && receivedBytes == totalSize;
        }

        private byte[] combine() {
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, result, offset, chunk.length);
                offset += chunk.length;
            }
            return result;
        }
    }
}
