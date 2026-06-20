package com.example.hcrpoints.hud;

import com.example.hcrpoints.capturepoint.CapturePoint;
import com.example.hcrpoints.capturepoint.CapturePointManager;
import com.example.hcrpoints.capturepoint.DisplayState;
import com.example.hcrpoints.config.TacticalMapConfig;
import com.example.hcrpoints.config.TacticalMapJsonConfig;
import com.example.hcrpoints.network.SyncBastionsMessage;
import com.example.hcrpoints.util.EspetroTeamBridge;
import com.example.hcrpoints.util.ModLogger;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.event.ScreenEvent;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 战术地图HUD类，通过V键打开，显示玩家周围更大范围的据点信息
 */
public class TacticalMapHUD implements IGuiOverlay {
    private static final double ZOOM_FACTOR = 1.25D;
    private static final int MAP_TITLE_HEIGHT = 20;
    private static final ResourceLocation VANILLA_MAP_ICONS =
        ResourceLocation.withDefaultNamespace("textures/map/map_icons.png");
    private static final int MAP_BACKGROUND_FALLBACK_COLOR = 0xAA1D211B;
    private static final double LOCAL_PLAYER_MARKER_WORLD_SIZE = 18.0D;
    private static final double TEAMMATE_MARKER_WORLD_SIZE = 15.0D;
    private static final double CAPTURE_POINT_MARKER_WORLD_SIZE = 10.0D;
    private static final double BASTION_MARKER_WORLD_SIZE = 18.0D;
    private static final double BASE_MARKER_WORLD_SIZE = 28.0D;
    private static final int[] ROUTE_COLORS = {
            0xFFFF5555,
            0xFF55FF55,
            0xFF5599FF,
            0xFFFFFF55,
            0xFFFF55FF,
            0xFF55FFFF,
            0xFFFFAA55
    };
    
    private boolean isMapVisible = false; // 地图是否可见
    private double visibleWorldSpan = -1.0D;
    private List<CapturePoint> allPoints = new ArrayList<>();
    private List<SyncBastionsMessage.BastionInfo> visibleBastions = new ArrayList<>();
    private List<SyncBastionsMessage.BaseInfo> visibleBases = new ArrayList<>();
    
    // 存储从服务端同步的玩家位置
    private final Map<UUID, com.example.hcrpoints.network.SyncPlayerPositionsMessage.PlayerPosition> syncedPlayerPositions = new HashMap<>();
    
    // 摄影机晃动相关变量
    private float prevYaw = 0.0f;
    private float prevPitch = 0.0f;
    private float smoothShakeX = 0.0f;
    private float smoothShakeY = 0.0f;
    private static final float SMOOTH_FACTOR = 0.1f; // 平滑因子，数值越小晃动越平滑
    private static final int SHAKE_INTENSITY = 3; // 晃动强度

    private boolean draggingMap = false;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private double dragStartCenterX;
    private double dragStartCenterZ;
    private double draggedCenterX;
    private double draggedCenterZ;
    private boolean customMapCenter = false;

    private ResourceLocation backgroundTextureLocation;
    private DynamicTexture backgroundTexture;
    private String backgroundTextureKey = "";
    private int backgroundTextureWidth;
    private int backgroundTextureHeight;
    private int lastEmbeddedMapLeft = Integer.MIN_VALUE;
    private int lastEmbeddedMapTop = Integer.MIN_VALUE;
    private int lastEmbeddedMapWidth;
    private int lastEmbeddedMapHeight;
    private Object lastEmbeddedMapScreen;
    private long lastEmbeddedMapRenderMs;
    private int lastRecenterButtonLeft = Integer.MIN_VALUE;
    private int lastRecenterButtonTop = Integer.MIN_VALUE;
    private int lastRecenterButtonWidth;
    private int lastRecenterButtonHeight;
    
    public TacticalMapHUD() {
        // 注册事件监听器
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void syncVisibleCapturePointsFromServer(List<CapturePoint.SerializableCapturePoint> serializedPoints) {
        List<CapturePoint> syncedPoints = new ArrayList<>();
        for (CapturePoint.SerializableCapturePoint sp : serializedPoints) {
            CapturePoint point = new CapturePoint(sp.name, sp.pos1, sp.pos2, sp.batch);
            point.restoreFromSerializable(sp);
            syncedPoints.add(point);
        }
        syncedPoints.sort(Comparator.comparingInt(CapturePoint::getBatch).thenComparing(CapturePoint::getName));
        this.allPoints = syncedPoints;
    }

    public void syncBastionsFromServer(List<SyncBastionsMessage.BastionInfo> bastions) {
        this.visibleBastions = new ArrayList<>(bastions);
    }

    public void syncBastionsFromServer(List<SyncBastionsMessage.BastionInfo> bastions,
                                       List<SyncBastionsMessage.BaseInfo> bases) {
        this.visibleBastions = new ArrayList<>(bastions);
        this.visibleBases = new ArrayList<>(bases);
    }
    
    /**
     * 切换地图显示/隐藏状态
     */
    public void toggleMapVisibility() {
        isMapVisible = !isMapVisible;
        draggingMap = false;

        Minecraft mc = Minecraft.getInstance();
        if (isMapVisible) {
            TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
            resetVisibleSpan(config);
            customMapCenter = false;
        } else {
            releaseBackgroundTexture();
        }
    }
    
    /**
     * 切换地图显示模式
     */
    public void cycleDisplayMode() {
        // 地图现在只有一种显示形态，保留方法避免旧按键调用报错。
    }
    
    /**
     * 获取当前显示模式
     */
    public MapDisplayMode getDisplayMode() {
        return MapDisplayMode.TOGGLE_KEY;
    }
    
    /**
     * 获取地图是否可见（按键唤出模式下）
     */
    public boolean isMapVisible() {
        return isMapVisible;
    }

    public void increaseRenderRange() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = config.getBounds();
        ensureVisibleSpan(config, bounds);
        visibleWorldSpan = Math.min(bounds.size(), visibleWorldSpan * ZOOM_FACTOR);
        preserveCustomViewportCenter(bounds);
    }

    public void decreaseRenderRange() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = config.getBounds();
        ensureVisibleSpan(config, bounds);
        visibleWorldSpan = Math.max(config.getMinimumRange(bounds), visibleWorldSpan / ZOOM_FACTOR);
        preserveCustomViewportCenter(bounds);
    }

    public void zoomFromMouseWheel(double delta) {
        if (delta > 0.0D) {
            decreaseRenderRange();
        } else if (delta < 0.0D) {
            increaseRenderRange();
        }
    }

    public void recenterOnPlayer() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        resetVisibleSpan(config);
        draggingMap = false;
        customMapCenter = false;
    }

    public void onTacticalMapConfigSynced() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        resetVisibleSpan(config);
        draggingMap = false;
        customMapCenter = false;
        releaseBackgroundTexture();
    }
    
    /**
     * 渲染HUD界面
     * @param gui Forge GUI对象
     * @param guiGraphics GUI图形对象
     * @param partialTick 部分刻度
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!isMapVisible) {
            return;
        }
        
        int mapWidth = (int)(screenWidth * 0.42);
        int mapHeight = (int)(screenHeight * 0.75);
        int mapLeft = screenWidth - mapWidth;
        int mapTop = (screenHeight - mapHeight) / 2;
        renderMapArea(guiGraphics, mapLeft, mapTop, mapWidth, mapHeight,
            "战术地图 (V键关闭) " + getRangeText() + " C+/B-", false);
    }

    public void renderEmbeddedMap(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight, float partialTick) {
        lastEmbeddedMapLeft = mapLeft;
        lastEmbeddedMapTop = mapTop;
        lastEmbeddedMapWidth = mapWidth;
        lastEmbeddedMapHeight = mapHeight;
        lastEmbeddedMapScreen = Minecraft.getInstance().screen;
        lastEmbeddedMapRenderMs = System.currentTimeMillis();
        renderMapArea(guiGraphics, mapLeft, mapTop, mapWidth, mapHeight,
            "战术地图 " + getRangeText() + " 鼠标滚轮缩放", true);
    }

    @SubscribeEvent
    public void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (isInsideLastEmbeddedMap(event.getMouseX(), event.getMouseY())) {
            zoomFromMouseWheel(event.getScrollDelta());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && isInsideLastRecenterButton(event.getMouseX(), event.getMouseY())) {
            recenterOnPlayer();
            event.setCanceled(true);
        }
    }

    private void renderMapArea(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight,
                               String title, boolean allowMouseDrag) {
        if (mapWidth <= 0 || mapHeight <= 0) {
            return;
        }

        // 渲染地图背景（半透明）
        int bgColor = 0xCC202020;
        guiGraphics.fill(mapLeft, mapTop, mapLeft + mapWidth, mapTop + mapHeight, bgColor);
        
        // 渲染地图边框
        int borderColor = 0xFF000000;
        guiGraphics.fill(mapLeft, mapTop, mapLeft + mapWidth, mapTop + 1, borderColor);
        guiGraphics.fill(mapLeft, mapTop, mapLeft + 1, mapTop + mapHeight, borderColor);
        guiGraphics.fill(mapLeft + mapWidth - 1, mapTop, mapLeft + mapWidth, mapTop + mapHeight, borderColor);
        guiGraphics.fill(mapLeft, mapTop + mapHeight - 1, mapLeft + mapWidth, mapTop + mapHeight, borderColor);
        
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            Component.literal(title),
            mapLeft + 5,
            mapTop + 5,
            0xFFFFFF,
            false
        );

        if (allowMouseDrag) {
            renderRecenterButton(guiGraphics, mapLeft, mapTop, mapWidth);
        } else {
            clearRecenterButton();
        }
        
        // 渲染鸟瞰图，包含玩家位置
        renderBirdsEyeView(guiGraphics, mapLeft, mapTop, mapWidth, mapHeight, allowMouseDrag);
    }

    private void renderRecenterButton(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth) {
        String label = "归中";
        int buttonWidth = Math.max(34, Minecraft.getInstance().font.width(label) + 12);
        int buttonHeight = 14;
        int buttonLeft = mapLeft + mapWidth - buttonWidth - 6;
        int buttonTop = mapTop + 4;

        lastRecenterButtonLeft = buttonLeft;
        lastRecenterButtonTop = buttonTop;
        lastRecenterButtonWidth = buttonWidth;
        lastRecenterButtonHeight = buttonHeight;

        guiGraphics.fill(buttonLeft, buttonTop, buttonLeft + buttonWidth, buttonTop + buttonHeight, 0xAA101010);
        guiGraphics.renderOutline(buttonLeft, buttonTop, buttonWidth, buttonHeight, 0xAAE6E06A);
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            label,
            buttonLeft + (buttonWidth - Minecraft.getInstance().font.width(label)) / 2,
            buttonTop + 3,
            0xFFFFFF55,
            false
        );
    }

    private void clearRecenterButton() {
        lastRecenterButtonLeft = Integer.MIN_VALUE;
        lastRecenterButtonTop = Integer.MIN_VALUE;
        lastRecenterButtonWidth = 0;
        lastRecenterButtonHeight = 0;
    }
    
    /**
     * 渲染行动模式兵力显示
     * @param guiGraphics GUI图形对象
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param isMiniMap 是否是迷你地图
     */
    private void renderReinforcementsDisplay(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight, boolean isMiniMap) {
        CapturePointManager manager = CapturePointManager.getInstance();
        
        // 获取攻防双方队伍名称
        String attackerTeam = manager.getAttackerTeam();
        String defenderTeam = manager.getDefenderTeam();
        
        if (attackerTeam == null || defenderTeam == null) {
            return; // 队伍未设置完整，不显示
        }
        
        // 获取双方兵力
        int attackerReinforcements = manager.getTeamReinforcements(attackerTeam);
        int defenderReinforcements = manager.getTeamReinforcements(defenderTeam);
        
        // 计算进度条位置和尺寸
        int barHeight = 10;
        int barTop = isMiniMap ? mapTop + 5 : mapTop + 20; // 迷你地图上显示在顶部，完整地图显示在标题下方
        int barLeft = mapLeft + 5;
        int barRight = mapLeft + mapWidth - 5;
        int barWidth = barRight - barLeft;
        int halfBarWidth = barWidth / 2;
        
        // 从配置中获取颜色代码
        String defenderHexColor = com.example.hcrpoints.config.TacticalMapConfig.defenderProgressBarColor.get();
        String attackerHexColor = com.example.hcrpoints.config.TacticalMapConfig.attackerProgressBarColor.get();
        
        // 将颜色代码转换为整数颜色值
        int defenderColor = com.example.hcrpoints.util.ModLogger.hexToColor(defenderHexColor, 0xFF0055FF); // 默认蓝色
        int attackerColor = com.example.hcrpoints.util.ModLogger.hexToColor(attackerHexColor, 0xFFFF5500); // 默认红色
        
        // 渲染守方进度条
        int defenderInitialReinforcements = manager.getTeamInitialReinforcements(defenderTeam);
        int defenderBarWidth = 0;
        if (defenderInitialReinforcements > 0) {
            defenderBarWidth = (int)((double)defenderReinforcements / defenderInitialReinforcements * halfBarWidth);
        }
        guiGraphics.fill(barLeft, barTop, barLeft + halfBarWidth, barTop + barHeight, 0x44000000); // 背景
        guiGraphics.fill(barLeft, barTop, barLeft + defenderBarWidth, barTop + barHeight, defenderColor); // 进度条
        
        // 渲染攻方进度条
        int attackerInitialReinforcements = manager.getTeamInitialReinforcements(attackerTeam);
        int attackerBarWidth = 0;
        if (attackerInitialReinforcements > 0) {
            attackerBarWidth = (int)((double)attackerReinforcements / attackerInitialReinforcements * halfBarWidth);
        }
        int attackerBarLeft = barLeft + halfBarWidth;
        guiGraphics.fill(attackerBarLeft, barTop, attackerBarLeft + halfBarWidth, barTop + barHeight, 0x44000000); // 背景
        guiGraphics.fill(attackerBarLeft + halfBarWidth - attackerBarWidth, barTop, attackerBarLeft + halfBarWidth, barTop + barHeight, attackerColor); // 进度条
        
        // 绘制队伍名称和兵力数量
        Minecraft minecraft = Minecraft.getInstance();
        
        // 计算文字位置：迷你地图上显示在进度条上方，完整地图上显示在下方
        int textY = isMiniMap ? barTop - 10 : barTop + barHeight + 2;
        
        // 守方信息（左对齐）
        String defenderText = defenderTeam + ": " + defenderReinforcements;
        guiGraphics.drawString(
            minecraft.font,
            defenderText,
            barLeft,
            textY,
            0xFFFFFF,
            false
        );
        
        // 攻方信息（右对齐）
        String attackerText = attackerTeam + ": " + attackerReinforcements;
        guiGraphics.drawString(
            minecraft.font,
            attackerText,
            barRight - minecraft.font.width(attackerText),
            textY,
            0xFFFFFF,
            false
        );
    }
    
    /**
     * 从服务端同步玩家位置
     * @param playerPositions 玩家位置映射
     */
    public void syncPlayerPositionsFromServer(Map<UUID, com.example.hcrpoints.network.SyncPlayerPositionsMessage.PlayerPosition> playerPositions) {
        this.syncedPlayerPositions.clear();
        this.syncedPlayerPositions.putAll(playerPositions);
    }
    
    /**
     * 在地图上渲染其他玩家位置
     * @param guiGraphics GUI图形对象
     * @param localPlayer 本地玩家
     * @param playerMapX 玩家在地图上的X坐标
     * @param playerMapY 玩家在地图上的Y坐标
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     */
    private void renderOtherPlayersOnMap(GuiGraphics guiGraphics, LocalPlayer localPlayer, MapViewport viewport) {
        // 使用配置类检查是否显示玩家位置
        boolean showPlayerLocations = com.example.hcrpoints.config.MapPlayerDisplayConfig.getInstance().isShowPlayerLocations();
        
        if (!showPlayerLocations) {
            // 配置不允许显示，不渲染其他玩家位置
            return;
        }

        String localTeam = EspetroTeamBridge.getPlayerTeam(localPlayer);
        
        // 遍历从服务端同步的玩家位置
        for (Map.Entry<UUID, com.example.hcrpoints.network.SyncPlayerPositionsMessage.PlayerPosition> entry : syncedPlayerPositions.entrySet()) {
            UUID playerUUID = entry.getKey();
            com.example.hcrpoints.network.SyncPlayerPositionsMessage.PlayerPosition pos = entry.getValue();
            
            // 跳过本地玩家
            if (playerUUID.equals(localPlayer.getUUID())) {
                continue;
            }
            
            // 获取其他玩家坐标
            double otherPlayerX = pos.getX();
            double otherPlayerZ = pos.getZ();

            if (!viewport.containsWorld(otherPlayerX, otherPlayerZ)) {
                continue;
            }
            
            if (!EspetroTeamBridge.isSameTeam(localTeam, pos.getTeamName())) {
                continue;
            }

            int mapPosX = viewport.screenX(otherPlayerX);
            int mapPosY = viewport.screenY(otherPlayerZ);
            int teammateSize = viewport.markerSize(TEAMMATE_MARKER_WORLD_SIZE, 8, 20);
            renderMapPlayerIcon(guiGraphics, mapPosX, mapPosY, pos.getYaw(), teammateSize);
        }
        
        // 如果没有同步到玩家位置，尝试直接获取本地玩家列表作为备选方案
        if (syncedPlayerPositions.isEmpty()) {
            // 使用配置类检查是否显示玩家位置
            showPlayerLocations = com.example.hcrpoints.config.MapPlayerDisplayConfig.getInstance().isShowPlayerLocations();
            if (!showPlayerLocations) {
                // 配置不允许显示，不渲染其他玩家位置
                return;
            }
            
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                for (net.minecraft.world.entity.player.Player otherPlayer : minecraft.level.players()) {
                    if (otherPlayer == localPlayer) {
                        continue;
                    }

                    double otherPlayerX = otherPlayer.getX();
                    double otherPlayerZ = otherPlayer.getZ();
                    if (!viewport.containsWorld(otherPlayerX, otherPlayerZ)) {
                        continue;
                    }

                    if (!EspetroTeamBridge.isSameTeam(localTeam, EspetroTeamBridge.getPlayerTeam(otherPlayer))) {
                        continue;
                    }

                    int mapPosX = viewport.screenX(otherPlayerX);
                    int mapPosY = viewport.screenY(otherPlayerZ);
                    int teammateSize = viewport.markerSize(TEAMMATE_MARKER_WORLD_SIZE, 8, 20);
                    renderMapPlayerIcon(guiGraphics, mapPosX, mapPosY, otherPlayer.getYRot(), teammateSize);
                }
            }
        }
    }
    
    /**
     * 渲染鸟瞰图
     * @param guiGraphics GUI图形对象
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param alpha 透明度
     */
    private void renderBirdsEyeView(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight,
                                    boolean allowMouseDrag) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        ensureBackgroundTexture(config);
        TacticalMapJsonConfig.TacticalMapBounds bounds = config.getBounds();
        ensureVisibleSpan(config, bounds);

        MapViewport content = createViewport(config, bounds, player, mapLeft, mapTop, mapWidth, mapHeight);
        if (allowMouseDrag) {
            handleMapDrag(content, bounds);
            content = createViewport(config, bounds, player, mapLeft, mapTop, mapWidth, mapHeight);
        } else {
            draggingMap = false;
            customMapCenter = false;
        }

        guiGraphics.enableScissor(content.left, content.top, content.right(), content.bottom());
        renderMapBackground(guiGraphics, content);
        if (config.showGrid) {
            renderViewportGrid(guiGraphics, content);
        }

        String localTeam = EspetroTeamBridge.getPlayerTeam(player);

        renderBasesOnMap(guiGraphics, content, player, config.showLabels, localTeam);

        renderCapturePointsOnMap(guiGraphics, content, player, config.showLabels, localTeam);

        // 渲染己方兵站
        renderBastionsOnMap(guiGraphics, content, player, config.showLabels, localTeam);

        // 渲染其他玩家位置
        renderOtherPlayersOnMap(guiGraphics, player, content);

        if (content.containsWorld(player.getX(), player.getZ())) {
            int localSize = content.markerSize(LOCAL_PLAYER_MARKER_WORLD_SIZE, 10, 24);
            renderMapPlayerIcon(guiGraphics, content.screenX(player.getX()), content.screenY(player.getZ()),
                player.getYRot(), localSize);
        }

        guiGraphics.disableScissor();
        guiGraphics.renderOutline(content.left, content.top, content.width, content.height, 0xCC000000);
    }

    private void renderBasesOnMap(GuiGraphics guiGraphics, MapViewport viewport, LocalPlayer player,
                                  boolean showLabels, String localTeam) {
        if (visibleBases.isEmpty()) {
            return;
        }

        double playerY = player.getY();
        int size = viewport.markerSize(BASE_MARKER_WORLD_SIZE, 10, 26);

        for (SyncBastionsMessage.BaseInfo base : visibleBases) {
            if (!isFriendlyTeam(localTeam, base.getTeam())) {
                continue;
            }

            BlockPos pos = base.getPos();
            if (!viewport.containsWorld(pos.getX(), pos.getZ())) {
                continue;
            }

            int mapPosX = viewport.screenX(pos.getX());
            int mapPosY = viewport.screenY(pos.getZ());
            int baseColor = getBastionColor(base.getTeam());
            renderBaseMarker(guiGraphics, mapPosX, mapPosY, size, baseColor, base.getYaw());

            if (!showLabels) {
                continue;
            }

            String name = base.getName() == null || base.getName().isEmpty() ? "主基地" : base.getName();
            renderMapLabel(guiGraphics, name, mapPosX, mapPosY, size + 3, -size / 2, 0xFFFFFF);

            int relativeHeight = (int) Math.round(pos.getY() - playerY);
            String heightText = relativeHeight > 0 ? "+" + relativeHeight : String.valueOf(relativeHeight);
            renderMapLabel(guiGraphics, heightText, mapPosX, mapPosY, size + 3, 6, 0x88DDDD);
        }
    }

    private void renderBastionsOnMap(GuiGraphics guiGraphics, MapViewport viewport, LocalPlayer player,
                                     boolean showLabels, String localTeam) {
        if (visibleBastions.isEmpty()) {
            return;
        }

        double playerY = player.getY();
        int size = viewport.markerSize(BASTION_MARKER_WORLD_SIZE, 7, 18);

        for (SyncBastionsMessage.BastionInfo bastion : visibleBastions) {
            if (!isFriendlyTeam(localTeam, bastion.getTeam())) {
                continue;
            }

            BlockPos pos = bastion.getPos();
            if (!viewport.containsWorld(pos.getX(), pos.getZ())) {
                continue;
            }

            int mapPosX = viewport.screenX(pos.getX());
            int mapPosY = viewport.screenY(pos.getZ());
            int bastionColor = getBastionColor(bastion.getTeam());
            renderBastionMarker(guiGraphics, mapPosX, mapPosY, size, bastionColor);

            if (!showLabels) {
                continue;
            }

            String name = bastion.getName() == null || bastion.getName().isEmpty() ? "兵站" : bastion.getName();
            renderMapLabel(guiGraphics, name, mapPosX, mapPosY, size + 3, -size / 2, 0xFFFFFF);

            int relativeHeight = (int) Math.round(pos.getY() - playerY);
            String heightText = relativeHeight > 0 ? "+" + relativeHeight : String.valueOf(relativeHeight);
            renderMapLabel(guiGraphics, heightText, mapPosX, mapPosY, size + 3, 6, 0x88DDDD);
        }
    }

    private void renderBastionMarker(GuiGraphics guiGraphics, int x, int y, int size, int color) {
        int half = Math.max(3, size / 2);
        int arm = Math.max(1, size / 5);
        guiGraphics.fill(x - arm, y - half, x + arm + 1, y - arm, color);
        guiGraphics.fill(x - half, y - arm, x + half + 1, y + arm + 1, color);
        guiGraphics.fill(x - arm, y + arm, x + arm + 1, y + half + 1, color);
        guiGraphics.fill(x - half + 1, y - half + 1, x + half, y + half, 0xCC101010);
        guiGraphics.fill(x - arm, y - arm, x + arm + 1, y + arm + 1, color);
    }

    private void renderBaseMarker(GuiGraphics guiGraphics, int x, int y, int size, int color, float yaw) {
        int half = Math.max(5, size / 2);
        int inner = Math.max(2, half / 2);
        guiGraphics.fill(x - half, y - half, x + half + 1, y + half + 1, 0xDD101010);
        guiGraphics.fill(x - half + 1, y - half + 1, x + half, y + half, color);
        guiGraphics.fill(x - inner, y - inner, x + inner + 1, y + inner + 1, 0xEE101010);

        double radians = Math.toRadians(yaw);
        int tipX = x - (int) Math.round(Math.sin(radians) * half);
        int tipY = y + (int) Math.round(Math.cos(radians) * half);
        int pointerHalf = Math.max(1, size / 8);
        guiGraphics.fill(tipX - pointerHalf, tipY - pointerHalf, tipX + pointerHalf + 1, tipY + pointerHalf + 1, 0xFFFFFFFF);
    }

    private void renderPointMarker(GuiGraphics guiGraphics, int x, int y, int size, int color) {
        int half = Math.max(2, size / 2);
        guiGraphics.fill(x - half, y - half, x + half + 1, y + half + 1, 0xDD101010);
        guiGraphics.fill(x - half + 1, y - half + 1, x + half, y + half, color);
    }

    private void renderMapLabel(GuiGraphics guiGraphics, String text, int x, int y, int offsetX, int offsetY, int color) {
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            text,
            x + offsetX,
            y + offsetY,
            color,
            false
        );
    }

    private int getBastionColor(String team) {
        if ("ATTACK".equalsIgnoreCase(team)) {
            return 0xFFFF5555;
        }
        if ("DEFEND".equalsIgnoreCase(team)) {
            return 0xFF5599FF;
        }
        return 0xFF55FFFF;
    }
    
    /**
     * 在地图上渲染据点
     * @param guiGraphics GUI图形对象
     * @param playerMapX 玩家在地图上的X坐标
     * @param playerMapY 玩家在地图上的Y坐标
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param alpha 透明度
     * @param player 本地玩家
     */
    private void renderCapturePointsOnMap(GuiGraphics guiGraphics, MapViewport viewport, LocalPlayer player,
                                          boolean showLabels, String localTeam) {
        double playerY = player.getY();
        int markerSize = viewport.markerSize(CAPTURE_POINT_MARKER_WORLD_SIZE, 5, 16);
        int labelOffset = markerSize / 2 + 3;

        // 遍历所有据点
        Map<CapturePoint, int[]> pointPositions = new HashMap<>();
        for (CapturePoint point : allPoints) {
            if (!isFriendlyCapturePoint(point, localTeam)) {
                continue;
            }

            // 计算据点中心坐标
            double pointCenterX = (point.getPos1().getX() + point.getPos2().getX()) / 2.0;
            double pointCenterZ = (point.getPos1().getZ() + point.getPos2().getZ()) / 2.0;
            double pointCenterY = (point.getPos1().getY() + point.getPos2().getY()) / 2.0;

            int mapPosX = viewport.screenX(pointCenterX);
            int mapPosY = viewport.screenY(pointCenterZ);
            if (viewport.containsWorld(pointCenterX, pointCenterZ)) {
                pointPositions.put(point, new int[] {mapPosX, mapPosY});
            }
            
            // 根据据点状态获取颜色
            int pointColor = getStatusColor(point);
            
            // 渲染据点中心方块
            renderPointMarker(guiGraphics, mapPosX, mapPosY, markerSize, pointColor);

            if (!showLabels) {
                renderPointBoundary(guiGraphics, point, viewport);
                continue;
            }

            // 渲染据点名称
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                point.getName(),
                mapPosX + labelOffset,
                mapPosY - 6,
                0xFFFFFF,
                false
            );
            
            // 计算并渲染玩家与据点的相对高度
            int relativeHeight = (int)Math.round(pointCenterY - playerY);
            String heightText = relativeHeight > 0 ? "+" + relativeHeight : String.valueOf(relativeHeight);
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                heightText,
                mapPosX + labelOffset,
                mapPosY + 6,
                0xAAAAAA,
                false
            );
            
            // 渲染据点坐标
            String coordText = (int)pointCenterX + ", " + (int)pointCenterZ;
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                coordText,
                mapPosX + labelOffset,
                mapPosY + 18,
                0x888888,
                false
            );
            
            // 渲染据点边界，传入网格边界参数，确保据点范围限制在网格内
            renderPointBoundary(guiGraphics, point, viewport);
        }

        renderBatchRoutes(guiGraphics, pointPositions, viewport, localTeam);
    }

    private void renderBatchRoutes(GuiGraphics guiGraphics, Map<CapturePoint, int[]> pointPositions,
                                   MapViewport viewport, String localTeam) {
        Map<Integer, List<CapturePoint>> pointsByBatch = new TreeMap<>();
        for (CapturePoint point : allPoints) {
            if (!isFriendlyCapturePoint(point, localTeam)) {
                continue;
            }
            pointsByBatch.computeIfAbsent(point.getBatch(), key -> new ArrayList<>()).add(point);
        }

        CapturePoint previousBatchLastPoint = null;
        for (List<CapturePoint> batchPoints : pointsByBatch.values()) {
            batchPoints.sort(Comparator.comparing(CapturePoint::getName));

            if (previousBatchLastPoint != null && !batchPoints.isEmpty()) {
                drawRouteLine(guiGraphics, pointPositions, previousBatchLastPoint, batchPoints.get(0), ROUTE_COLORS[0], viewport);
            }

            for (int i = 0; i < batchPoints.size() - 1; i++) {
                int color = ROUTE_COLORS[i % ROUTE_COLORS.length];
                drawRouteLine(guiGraphics, pointPositions, batchPoints.get(i), batchPoints.get(i + 1), color, viewport);
            }

            if (!batchPoints.isEmpty()) {
                previousBatchLastPoint = batchPoints.get(batchPoints.size() - 1);
            }
        }
    }

    private void drawRouteLine(GuiGraphics guiGraphics, Map<CapturePoint, int[]> pointPositions,
                               CapturePoint from, CapturePoint to, int color, MapViewport viewport) {
        int[] fromPos = pointPositions.get(from);
        int[] toPos = pointPositions.get(to);
        if (fromPos == null || toPos == null) {
            return;
        }
        drawClippedLine(guiGraphics, fromPos[0], fromPos[1], toPos[0], toPos[1], color, viewport);
    }
    
    /**
     * 渲染据点边界
     * @param guiGraphics GUI图形对象
     * @param point 据点对象
     * @param playerX 玩家X坐标
     * @param playerZ 玩家Z坐标
     * @param playerMapX 玩家在地图上的X坐标
     * @param playerMapY 玩家在地图上的Y坐标
     * @param scaleX X轴缩放比例
     * @param scaleY Y轴缩放比例
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapRight 地图右下角X坐标
     * @param mapBottom 地图右下角Y坐标
     * @param alpha 透明度
     */
    private void renderPointBoundary(GuiGraphics guiGraphics, CapturePoint point, MapViewport viewport) {
        // 计算据点边界坐标
        int minX = Math.min(point.getPos1().getX(), point.getPos2().getX());
        int maxX = Math.max(point.getPos1().getX(), point.getPos2().getX());
        int minZ = Math.min(point.getPos1().getZ(), point.getPos2().getZ());
        int maxZ = Math.max(point.getPos1().getZ(), point.getPos2().getZ());

        if (maxX < viewport.viewMinX || minX > viewport.viewMaxX
                || maxZ < viewport.viewMinZ || minZ > viewport.viewMaxZ) {
            return;
        }

        int leftX = Mth.clamp(viewport.screenX(minX), viewport.left, viewport.right() - 1);
        int rightX = Mth.clamp(viewport.screenX(maxX), viewport.left, viewport.right() - 1);
        int topY = Mth.clamp(viewport.screenY(minZ), viewport.top, viewport.bottom() - 1);
        int bottomY = Mth.clamp(viewport.screenY(maxZ), viewport.top, viewport.bottom() - 1);

        // 根据据点状态获取颜色
        int boundaryColor = (getStatusColor(point) & 0x80FFFFFF); // 半透明

        if (leftX > rightX) {
            int tmp = leftX;
            leftX = rightX;
            rightX = tmp;
        }
        if (topY > bottomY) {
            int tmp = topY;
            topY = bottomY;
            bottomY = tmp;
        }

        if (leftX < rightX) {
            guiGraphics.fill(leftX, topY, rightX + 1, topY + 1, boundaryColor);
            guiGraphics.fill(leftX, bottomY, rightX + 1, bottomY + 1, boundaryColor);
        }
        if (topY < bottomY) {
            guiGraphics.fill(leftX, topY, leftX + 1, bottomY + 1, boundaryColor);
            guiGraphics.fill(rightX, topY, rightX + 1, bottomY + 1, boundaryColor);
        }
    }

    private MapViewport createViewport(TacticalMapJsonConfig config,
                                       TacticalMapJsonConfig.TacticalMapBounds bounds,
                                       LocalPlayer player,
                                       int mapLeft,
                                       int mapTop,
                                       int mapWidth,
                                       int mapHeight) {
        int availableHeight = Math.max(1, mapHeight - MAP_TITLE_HEIGHT);
        double displayAspectRatio = getMapDisplayAspectRatio(bounds);
        double worldAspectRatio = bounds.aspectRatio();
        int width = Math.max(1, mapWidth);
        int height = Math.max(1, (int) Math.round(width / displayAspectRatio));
        if (height > availableHeight) {
            height = availableHeight;
            width = Math.max(1, (int) Math.round(height * displayAspectRatio));
        }
        int left = mapLeft + (mapWidth - width) / 2;
        int top = mapTop + MAP_TITLE_HEIGHT + (availableHeight - height) / 2;

        double centerX = (draggingMap || customMapCenter) ? draggedCenterX : player.getX();
        double centerZ = (draggingMap || customMapCenter) ? draggedCenterZ : player.getZ();
        ensureVisibleSpan(config, bounds);
        double[] clamped = clampViewportCenter(centerX, centerZ, visibleWorldSpan, bounds);
        if (draggingMap || customMapCenter) {
            draggedCenterX = clamped[0];
            draggedCenterZ = clamped[1];
        }

        double spanX;
        double spanZ;
        if (worldAspectRatio >= 1.0D) {
            spanX = visibleWorldSpan;
            spanZ = visibleWorldSpan / worldAspectRatio;
        } else {
            spanZ = visibleWorldSpan;
            spanX = visibleWorldSpan * worldAspectRatio;
        }

        return new MapViewport(left, top, width, height, clamped[0], clamped[1], spanX, spanZ, bounds);
    }

    private double getMapDisplayAspectRatio(TacticalMapJsonConfig.TacticalMapBounds bounds) {
        return Math.max(0.1D, Math.min(10.0D, bounds.aspectRatio()));
    }

    private void handleMapDrag(MapViewport viewport, TacticalMapJsonConfig.TacticalMapBounds bounds) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        boolean leftPressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        double mouseX = getGuiMouseX(mc);
        double mouseY = getGuiMouseY(mc);
        boolean insideMap = viewport.containsScreen(mouseX, mouseY);
        boolean insideRecenterButton = isInsideLastRecenterButton(mouseX, mouseY);

        if (leftPressed && insideRecenterButton && !draggingMap) {
            return;
        }

        if (leftPressed && insideMap && !draggingMap) {
            draggingMap = true;
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragStartCenterX = viewport.centerX;
            dragStartCenterZ = viewport.centerZ;
            draggedCenterX = viewport.centerX;
            draggedCenterZ = viewport.centerZ;
            customMapCenter = true;
        }

        if (!leftPressed) {
            draggingMap = false;
            return;
        }

        if (draggingMap) {
            double dx = mouseX - dragStartMouseX;
            double dy = mouseY - dragStartMouseY;
            double[] clamped = clampViewportCenter(
                dragStartCenterX - dx / viewport.scaleX,
                dragStartCenterZ - dy / viewport.scaleZ,
                visibleWorldSpan,
                bounds
            );
            draggedCenterX = clamped[0];
            draggedCenterZ = clamped[1];
        }
    }

    private double getGuiMouseX(Minecraft mc) {
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }

    private double getGuiMouseY(Minecraft mc) {
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    private boolean isInsideLastEmbeddedMap(double mouseX, double mouseY) {
        return lastEmbeddedMapLeft != Integer.MIN_VALUE
            && Minecraft.getInstance().screen == lastEmbeddedMapScreen
            && System.currentTimeMillis() - lastEmbeddedMapRenderMs <= 1000L
            && lastEmbeddedMapWidth > 0
            && lastEmbeddedMapHeight > 0
            && mouseX >= lastEmbeddedMapLeft
            && mouseX <= lastEmbeddedMapLeft + lastEmbeddedMapWidth
            && mouseY >= lastEmbeddedMapTop
            && mouseY <= lastEmbeddedMapTop + lastEmbeddedMapHeight;
    }

    private boolean isInsideLastRecenterButton(double mouseX, double mouseY) {
        return lastRecenterButtonLeft != Integer.MIN_VALUE
            && isInsideLastEmbeddedMap(mouseX, mouseY)
            && lastRecenterButtonWidth > 0
            && lastRecenterButtonHeight > 0
            && mouseX >= lastRecenterButtonLeft
            && mouseX <= lastRecenterButtonLeft + lastRecenterButtonWidth
            && mouseY >= lastRecenterButtonTop
            && mouseY <= lastRecenterButtonTop + lastRecenterButtonHeight;
    }

    private boolean isFriendlyTeam(String localTeam, String markerTeam) {
        return localTeam != null && EspetroTeamBridge.isSameTeam(localTeam, markerTeam);
    }

    private boolean isFriendlyCapturePoint(CapturePoint point, String localTeam) {
        if (point == null || localTeam == null) {
            return false;
        }
        String captorName = point.getCaptorName();
        String captorTeam = EspetroTeamBridge.canonicalizeTeamName(captorName);
        return captorTeam == null || EspetroTeamBridge.isSameTeam(localTeam, captorTeam);
    }

    private double[] clampViewportCenter(double centerX, double centerZ, double span,
                                         TacticalMapJsonConfig.TacticalMapBounds bounds) {
        double aspectRatio = bounds.aspectRatio();
        double spanX;
        double spanZ;
        if (bounds.width() >= bounds.height()) {
            spanX = span;
            spanZ = span / aspectRatio;
        } else {
            spanZ = span;
            spanX = span * aspectRatio;
        }

        double halfX = spanX / 2.0D;
        double halfZ = spanZ / 2.0D;
        double minCenterX = bounds.minX + halfX;
        double maxCenterX = bounds.maxX - halfX;
        double minCenterZ = bounds.minZ + halfZ;
        double maxCenterZ = bounds.maxZ - halfZ;

        if (minCenterX > maxCenterX) {
            centerX = bounds.centerX();
        } else {
            centerX = Mth.clamp(centerX, minCenterX, maxCenterX);
        }
        if (minCenterZ > maxCenterZ) {
            centerZ = bounds.centerZ();
        } else {
            centerZ = Mth.clamp(centerZ, minCenterZ, maxCenterZ);
        }
        return new double[] {centerX, centerZ};
    }

    private void renderMapBackground(GuiGraphics guiGraphics, MapViewport viewport) {
        renderConfiguredBackground(guiGraphics, viewport);
        guiGraphics.fill(viewport.left, viewport.top, viewport.right(), viewport.bottom(), 0x22000000);
    }

    private void renderConfiguredBackground(GuiGraphics guiGraphics, MapViewport viewport) {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        ensureBackgroundTexture(config);
        if (backgroundTextureLocation == null || backgroundTextureWidth <= 0 || backgroundTextureHeight <= 0) {
            guiGraphics.fill(viewport.left, viewport.top, viewport.right(), viewport.bottom(), MAP_BACKGROUND_FALLBACK_COLOR);
            return;
        }

        BackgroundSourceRect source = getBackgroundSourceRect(viewport.bounds);
        float u = (float) (source.left
            + (viewport.viewMinX - viewport.bounds.minX) * source.width / viewport.bounds.width());
        float v = (float) (source.top
            + (viewport.viewMinZ - viewport.bounds.minZ) * source.height / viewport.bounds.height());
        u = Mth.clamp(u, source.left, source.right() - 1.0F);
        v = Mth.clamp(v, source.top, source.bottom() - 1.0F);
        int uWidth = Math.max(1, (int) Math.round(viewport.spanX * source.width / viewport.bounds.width()));
        int vHeight = Math.max(1, (int) Math.round(viewport.spanZ * source.height / viewport.bounds.height()));
        uWidth = Math.min(uWidth, Math.max(1, (int) Math.floor(source.right() - u)));
        vHeight = Math.min(vHeight, Math.max(1, (int) Math.floor(source.bottom() - v)));
        guiGraphics.blit(backgroundTextureLocation, viewport.left, viewport.top, viewport.width, viewport.height,
            u, v, uWidth, vHeight, backgroundTextureWidth, backgroundTextureHeight);
    }

    private BackgroundSourceRect getBackgroundSourceRect(TacticalMapJsonConfig.TacticalMapBounds bounds) {
        double targetAspect = getMapDisplayAspectRatio(bounds);
        double imageAspect = backgroundTextureWidth / (double) backgroundTextureHeight;

        int cropLeft = 0;
        int cropTop = 0;
        int cropWidth = backgroundTextureWidth;
        int cropHeight = backgroundTextureHeight;

        if (imageAspect > targetAspect) {
            cropWidth = Mth.clamp((int) Math.round(backgroundTextureHeight * targetAspect), 1, backgroundTextureWidth);
        } else if (imageAspect < targetAspect) {
            cropHeight = Mth.clamp((int) Math.round(backgroundTextureWidth / targetAspect), 1, backgroundTextureHeight);
        }

        return new BackgroundSourceRect(cropLeft, cropTop, cropWidth, cropHeight);
    }

    private void ensureBackgroundTexture(TacticalMapJsonConfig config) {
        String imagePath = config.backgroundImage == null ? "" : config.backgroundImage.trim();
        if (imagePath.isEmpty()) {
            releaseBackgroundTexture();
            return;
        }

        try {
            Path path = resolveBackgroundPath(imagePath);
            long modified = getLastModified(path);
            String key = path.toAbsolutePath().normalize() + ":" + modified;
            if (key.equals(backgroundTextureKey) && backgroundTextureLocation != null && backgroundTexture != null) {
                return;
            }

            try (InputStream input = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(input);
                releaseBackgroundTexture();
                backgroundTextureWidth = image.getWidth();
                backgroundTextureHeight = image.getHeight();
                backgroundTexture = new DynamicTexture(image);
                backgroundTextureLocation = Minecraft.getInstance().getTextureManager()
                    .register("hcr_tactical_map_background", backgroundTexture);
                backgroundTextureKey = key;
            }
        } catch (IOException | InvalidPathException e) {
            releaseBackgroundTexture();
            ModLogger.warn("加载战术地图背景图失败: " + imagePath + " (" + e.getMessage() + ")");
        }
    }

    private Path resolveBackgroundPath(String imagePath) {
        Path path = Path.of(imagePath);
        if (path.isAbsolute()) {
            return path;
        }
        return Minecraft.getInstance().gameDirectory.toPath().resolve(imagePath).normalize();
    }

    private long getLastModified(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : Long.MIN_VALUE;
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private void releaseBackgroundTexture() {
        if (backgroundTextureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(backgroundTextureLocation);
        }
        backgroundTextureLocation = null;
        backgroundTexture = null;
        backgroundTextureKey = "";
        backgroundTextureWidth = 0;
        backgroundTextureHeight = 0;
    }

    private void renderViewportGrid(GuiGraphics guiGraphics, MapViewport viewport) {
        double gridStep = chooseGridStep(Math.max(viewport.spanX, viewport.spanZ));
        int gridColor = 0x33FFFFFF;

        double firstX = Math.ceil(viewport.viewMinX / gridStep) * gridStep;
        for (double x = firstX; x <= viewport.viewMaxX; x += gridStep) {
            int screenX = viewport.screenX(x);
            guiGraphics.fill(screenX, viewport.top, screenX + 1, viewport.bottom(), gridColor);
        }

        double firstZ = Math.ceil(viewport.viewMinZ / gridStep) * gridStep;
        for (double z = firstZ; z <= viewport.viewMaxZ; z += gridStep) {
            int screenY = viewport.screenY(z);
            guiGraphics.fill(viewport.left, screenY, viewport.right(), screenY + 1, gridColor);
        }
    }

    private double chooseGridStep(double span) {
        double target = span / 8.0D;
        double step = 16.0D;
        while (step < target) {
            step *= 2.0D;
        }
        return step;
    }

    private void renderMapPlayerIcon(GuiGraphics guiGraphics, int centerX, int centerY, float yaw, int size) {
        MapDecoration.Type type = MapDecoration.Type.PLAYER;
        int icon = type.getIcon();
        int u = (icon % 16) * 8;
        int v = (icon / 16) * 8;
        float scale = Mth.clamp(size, 6, 28) / 8.0F;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 200.0F);
        guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(yaw + 180.0F));
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.blit(VANILLA_MAP_ICONS, -4, -4, 8, 8, (float) u, (float) v, 8, 8, 128, 128);
        guiGraphics.pose().popPose();
    }

    private void drawClippedLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, MapViewport viewport) {
        int minX = viewport.left;
        int minY = viewport.top;
        int maxX = viewport.right() - 1;
        int maxY = viewport.bottom() - 1;

        int out1 = computeOutCode(x1, y1, minX, minY, maxX, maxY);
        int out2 = computeOutCode(x2, y2, minX, minY, maxX, maxY);

        int guard = 0;
        while (guard++ < 8) {
            if ((out1 | out2) == 0) {
                drawLine(guiGraphics, x1, y1, x2, y2, color);
                return;
            }
            if ((out1 & out2) != 0) {
                return;
            }

            int out = out1 != 0 ? out1 : out2;
            int x = 0;
            int y = 0;

            if ((out & 8) != 0) {
                if (y2 == y1) {
                    return;
                }
                x = (int) Math.round(x1 + (x2 - x1) * (maxY - y1) / (double) (y2 - y1));
                y = maxY;
            } else if ((out & 4) != 0) {
                if (y2 == y1) {
                    return;
                }
                x = (int) Math.round(x1 + (x2 - x1) * (minY - y1) / (double) (y2 - y1));
                y = minY;
            } else if ((out & 2) != 0) {
                if (x2 == x1) {
                    return;
                }
                y = (int) Math.round(y1 + (y2 - y1) * (maxX - x1) / (double) (x2 - x1));
                x = maxX;
            } else if ((out & 1) != 0) {
                if (x2 == x1) {
                    return;
                }
                y = (int) Math.round(y1 + (y2 - y1) * (minX - x1) / (double) (x2 - x1));
                x = minX;
            }

            if (out == out1) {
                x1 = x;
                y1 = y;
                out1 = computeOutCode(x1, y1, minX, minY, maxX, maxY);
            } else {
                x2 = x;
                y2 = y;
                out2 = computeOutCode(x2, y2, minX, minY, maxX, maxY);
            }
        }
    }

    private int computeOutCode(int x, int y, int minX, int minY, int maxX, int maxY) {
        int code = 0;
        if (x < minX) {
            code |= 1;
        } else if (x > maxX) {
            code |= 2;
        }
        if (y < minY) {
            code |= 4;
        } else if (y > maxY) {
            code |= 8;
        }
        return code;
    }

    private void resetVisibleSpan(TacticalMapJsonConfig config) {
        TacticalMapJsonConfig.TacticalMapBounds bounds = config.getBounds();
        visibleWorldSpan = config.getInitialRange(bounds);
    }

    private void ensureVisibleSpan(TacticalMapJsonConfig config, TacticalMapJsonConfig.TacticalMapBounds bounds) {
        double min = config.getMinimumRange(bounds);
        if (visibleWorldSpan <= 0.0D || Double.isNaN(visibleWorldSpan)) {
            visibleWorldSpan = config.getInitialRange(bounds);
        }
        visibleWorldSpan = Mth.clamp(visibleWorldSpan, min, bounds.size());
    }

    private void preserveCustomViewportCenter(TacticalMapJsonConfig.TacticalMapBounds bounds) {
        if (!customMapCenter && !draggingMap) {
            return;
        }

        double[] clamped = clampViewportCenter(draggedCenterX, draggedCenterZ, visibleWorldSpan, bounds);
        draggedCenterX = clamped[0];
        draggedCenterZ = clamped[1];
        customMapCenter = true;
    }

    private String getRangeText() {
        TacticalMapJsonConfig config = TacticalMapJsonConfig.getInstance();
        TacticalMapJsonConfig.TacticalMapBounds bounds = config.getBounds();
        ensureVisibleSpan(config, bounds);
        return "范围:" + Math.round(visibleWorldSpan);
    }

    private static final class MapViewport {
        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final double centerX;
        private final double centerZ;
        private final double spanX;
        private final double spanZ;
        private final double viewMinX;
        private final double viewMinZ;
        private final double viewMaxX;
        private final double viewMaxZ;
        private final double scaleX;
        private final double scaleZ;
        private final TacticalMapJsonConfig.TacticalMapBounds bounds;

        private MapViewport(int left, int top, int width, int height, double centerX, double centerZ,
                            double spanX, double spanZ,
                            TacticalMapJsonConfig.TacticalMapBounds bounds) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.spanX = spanX;
            this.spanZ = spanZ;
            this.bounds = bounds;
            this.viewMinX = centerX - spanX / 2.0D;
            this.viewMinZ = centerZ - spanZ / 2.0D;
            this.viewMaxX = centerX + spanX / 2.0D;
            this.viewMaxZ = centerZ + spanZ / 2.0D;
            this.scaleX = width / spanX;
            this.scaleZ = height / spanZ;
        }

        private int right() {
            return left + width;
        }

        private int bottom() {
            return top + height;
        }

        private boolean containsWorld(double x, double z) {
            return x >= viewMinX && x <= viewMaxX && z >= viewMinZ && z <= viewMaxZ;
        }

        private boolean containsScreen(double x, double y) {
            return x >= left && x <= right() && y >= top && y <= bottom();
        }

        private int screenX(double worldX) {
            return left + (int) Math.round((worldX - viewMinX) * scaleX);
        }

        private int screenY(double worldZ) {
            return top + (int) Math.round((worldZ - viewMinZ) * scaleZ);
        }

        private int markerSize(double worldSize, int minPixels, int maxPixels) {
            double pixelsPerBlock = Math.min(scaleX, scaleZ);
            return Mth.clamp((int) Math.round(worldSize * pixelsPerBlock), minPixels, maxPixels);
        }
    }

    private static final class BackgroundSourceRect {
        private final int left;
        private final int top;
        private final int width;
        private final int height;

        private BackgroundSourceRect(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        private int right() {
            return left + width;
        }

        private int bottom() {
            return top + height;
        }
    }
    
    /**
     * 根据显示状态获取对应颜色
     * @param point 据点对象
     * @param alpha 透明度
     * @return 对应的颜色值（ARGB格式）
     */
    private int getStatusColor(CapturePoint point) {
        DisplayState displayState = point.getDisplayState();
        int baseColor = 0;
        
        switch (displayState) {
            case NEUTRAL:
                baseColor = 0xFFAAAAAA; // 灰色 - 中立状态
                break;
                
            case CAPTURING_FLAG_SINGLE:
                baseColor = 0xFFFFFF00; // 黄色 - 单人升旗状态
                break;
                
            case CAPTURING_CONTESTED_MULTI:
                baseColor = 0xFFFF8000; // 橙色 - 争夺中升旗状态
                break;
                
            case CONTESTED_MULTI:
                baseColor = 0xFFFF8000; // 橙色 - 争夺状态
                break;
                
            case CAPTURING_DOWN:
                baseColor = 0xFFFF0000; // 红色 - 降旗状态
                break;
                
            case CAPTURED:
                // 检查当前玩家是否属于占领者
                if (isFriendlyCapture(point)) {
                    baseColor = 0xFF55FF55; // 绿色 - 友方占领
                } else {
                    baseColor = 0xFFFF5555; // 红色 - 敌方占领
                }
                break;
                
            default:
                baseColor = 0xFFAAAAAA; // 默认灰色
        }
        
        return baseColor;
    }
    
    /**
     * 渲染队伍玩家列表
     * @param guiGraphics GUI图形对象
     * @param mapLeft 地图左上角X坐标
     * @param mapTop 地图左上角Y坐标
     * @param mapWidth 地图宽度
     * @param mapHeight 地图高度
     * @param displayMode 地图显示模式
     */
    private void renderTeamPlayers(GuiGraphics guiGraphics, int mapLeft, int mapTop, int mapWidth, int mapHeight, MapDisplayMode displayMode) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) {
            return;
        }
        
        String localTeam = EspetroTeamBridge.getPlayerTeam(localPlayer);
        if (localTeam == null) {
            return;
        }
        
        // 获取所有在线玩家
        List<net.minecraft.client.player.AbstractClientPlayer> allPlayers = minecraft.level.players();
        if (allPlayers.isEmpty()) {
            return;
        }
        
        // 创建玩家距离信息列表
        List<PlayerDistanceInfo> playerDistanceInfos = new ArrayList<>();
        
        // 计算每个玩家与本地玩家的距离
        for (net.minecraft.client.player.AbstractClientPlayer player : allPlayers) {
            if (!EspetroTeamBridge.isSameTeam(localTeam, EspetroTeamBridge.getPlayerTeam(player))) {
                continue; // 只显示同队伍玩家
            }
            
            double distance = localPlayer.distanceTo(player);
            playerDistanceInfos.add(new PlayerDistanceInfo(player, distance));
        }
        
        // 排序玩家，优先显示自己，然后按距离排序
        playerDistanceInfos.sort((a, b) -> {
            // 优先显示本地玩家
            if (a.player == localPlayer) {
                return -1;
            }
            if (b.player == localPlayer) {
                return 1;
            }
            // 其他玩家按距离排序
            return Double.compare(a.distance, b.distance);
        });
        
        // 选择最多4个玩家（包含本地玩家）
        List<PlayerDistanceInfo> selectedPlayers = new ArrayList<>();
        for (int i = 0; i < playerDistanceInfos.size() && selectedPlayers.size() < 4; i++) {
            selectedPlayers.add(playerDistanceInfos.get(i));
        }
        
        // 计算显示位置
        int textX, textY;
        boolean isLeftSide = displayMode == MapDisplayMode.ALWAYS_VISIBLE_BOTTOM_LEFT || displayMode == MapDisplayMode.ALWAYS_VISIBLE_TOP_LEFT;
        
        if (isLeftSide) {
            // 迷你地图在左侧，玩家列表显示在右侧
            textX = mapLeft + mapWidth + 5;
        } else {
            // 迷你地图在右侧，玩家列表显示在左侧
            int maxNameWidth = 0;
            for (PlayerDistanceInfo info : selectedPlayers) {
                int nameWidth = minecraft.font.width(info.player.getName().getString());
                if (nameWidth > maxNameWidth) {
                    maxNameWidth = nameWidth;
                }
            }
            textX = mapLeft - maxNameWidth - 25;
        }
        
        // 垂直居中显示
        int totalHeight = selectedPlayers.size() * 12;
        textY = mapTop + (mapHeight - totalHeight) / 2;
        
        // 获取攻防队伍和颜色
        CapturePointManager manager = CapturePointManager.getInstance();
        String attackerTeam = manager.getAttackerTeam();
        String defenderTeam = manager.getDefenderTeam();
        
        // 获取颜色配置
        String defenderHexColor = TacticalMapConfig.defenderProgressBarColor.get();
        String attackerHexColor = TacticalMapConfig.attackerProgressBarColor.get();
        int defenderColor = com.example.hcrpoints.util.ModLogger.hexToColor(defenderHexColor, 0xFF0055FF);
        int attackerColor = com.example.hcrpoints.util.ModLogger.hexToColor(attackerHexColor, 0xFFFF5500);
        
        // 渲染玩家列表
        for (PlayerDistanceInfo info : selectedPlayers) {
            // 根据玩家血量确定小方块颜色
            float health = info.player.getHealth();
            float maxHealth = info.player.getMaxHealth();
            float healthPercentage = health / maxHealth;
            int playerColor;
            
            if (healthPercentage >= 0.6f) {
                // 100%~60%: 绿色
                playerColor = 0xFF00FF00;
            } else if (healthPercentage >= 0.25f) {
                // 59%~25%: 黄色
                playerColor = 0xFFFFFF00;
            } else {
                // 低于25%: 红色
                playerColor = 0xFFFF0000;
            }
            
            // 渲染血量状态方块
            guiGraphics.fill(textX, textY + 2, textX + 8, textY + 10, playerColor);
            
            // 渲染玩家名称
            guiGraphics.drawString(
                minecraft.font,
                info.player.getName().getString(),
                textX + 12,
                textY,
                0xFFFFFFFF,
                false
            );
            
            // 下移到下一个玩家位置
            textY += 12;
        }
    }
    
    /**
     * 玩家距离信息类
     */
    private static class PlayerDistanceInfo {
        private final net.minecraft.client.player.AbstractClientPlayer player;
        private final double distance;
        
        public PlayerDistanceInfo(net.minecraft.client.player.AbstractClientPlayer player, double distance) {
            this.player = player;
            this.distance = distance;
        }
    }
    
    /**
     * 渲染玩家箭头（表示朝向）
     * @param guiGraphics GUI图形对象
     * @param player 玩家对象
     * @param centerX 箭头中心X坐标
     * @param centerY 箭头中心Y坐标
     */
    private void renderPlayerArrow(GuiGraphics guiGraphics, LocalPlayer player, int centerX, int centerY) {
        int playerColor = 0xFFFF0000;
        
        // 获取玩家朝向（yRot）
        float playerYaw = player.getYRot();
        
        // Minecraft坐标系统到地图坐标系统的转换：
        // - yRot=0° 玩家面向北（正Z轴），地图上向上
        // - yRot=90° 玩家面向东（正X轴），地图上向右
        // - yRot=180° 玩家面向南（负Z轴），地图上向下
        // - yRot=270° 玩家面向西（负X轴），地图上向左
        
        // 箭头尺寸
        int arrowLength = 8;
        int arrowWidth = 3;
        
        // 计算箭头三个点的坐标
        int tipX, tipY, leftX, leftY, rightX, rightY;
        
        // 根据玩家朝向直接计算箭头方向，将四个方向反转
        tipX = centerX - (int)(Math.sin(Math.toRadians(playerYaw)) * arrowLength);
        tipY = centerY + (int)(Math.cos(Math.toRadians(playerYaw)) * arrowLength);
        
        // 计算箭头尾部的位置
        float tailAngle = playerYaw + 180;
        leftX = centerX - (int)(Math.sin(Math.toRadians(tailAngle + 30)) * arrowWidth);
        leftY = centerY + (int)(Math.cos(Math.toRadians(tailAngle + 30)) * arrowWidth);
        
        rightX = centerX - (int)(Math.sin(Math.toRadians(tailAngle - 30)) * arrowWidth);
        rightY = centerY + (int)(Math.cos(Math.toRadians(tailAngle - 30)) * arrowWidth);
        
        // 渲染箭头三角形
        drawTriangle(guiGraphics, tipX, tipY, leftX, leftY, rightX, rightY, playerColor);
    }
    
    /**
     * 绘制三角形
     * @param guiGraphics GUI图形对象
     * @param x1 点1 X坐标
     * @param y1 点1 Y坐标
     * @param x2 点2 X坐标
     * @param y2 点2 Y坐标
     * @param x3 点3 X坐标
     * @param y3 点3 Y坐标
     * @param color 颜色
     */
    private void drawTriangle(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        // 使用Bresenham算法绘制三个边
        drawLine(guiGraphics, x1, y1, x2, y2, color);
        drawLine(guiGraphics, x2, y2, x3, y3, color);
        drawLine(guiGraphics, x3, y3, x1, y1, color);
        
        // 填充三角形内部
        // 找到y轴范围
        int minY = Math.min(y1, Math.min(y2, y3));
        int maxY = Math.max(y1, Math.max(y2, y3));
        
        // 遍历每一行
        for (int y = minY; y <= maxY; y++) {
            // 找到该行与三角形的左右交点
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            
            // 检查边1-2
            if ((y1 <= y && y < y2) || (y2 <= y && y < y1)) {
                int x = interpolate(x1, y1, x2, y2, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            
            // 检查边2-3
            if ((y2 <= y && y < y3) || (y3 <= y && y < y2)) {
                int x = interpolate(x2, y2, x3, y3, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            
            // 检查边3-1
            if ((y3 <= y && y < y1) || (y1 <= y && y < y3)) {
                int x = interpolate(x3, y3, x1, y1, y);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            
            // 填充该行
            if (left <= right) {
                guiGraphics.fill(left, y, right + 1, y + 1, color);
            }
        }
    }
    
    /**
     * 线性插值计算x坐标
     */
    private int interpolate(int x1, int y1, int x2, int y2, int y) {
        if (y1 == y2) return x1;
        return x1 + (x2 - x1) * (y - y1) / (y2 - y1);
    }
    
    /**
     * 渲染一条线
     * @param guiGraphics GUI图形对象
     * @param x1 起始X坐标
     * @param y1 起始Y坐标
     * @param x2 结束X坐标
     * @param y2 结束Y坐标
     * @param color 颜色
     */
    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        // 使用Bresenham算法绘制线
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        
        int err = dx - dy;
        int e2;
        
        int x = x1;
        int y = y1;
        
        while (true) {
            // 绘制当前点
            guiGraphics.fill(x, y, x + 1, y + 1, color);
            
            // 检查是否到达终点
            if (x == x2 && y == y2) break;
            
            e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    
    /**
     * 检查占领是否为友方
     * @param point 据点对象
     * @return 如果是友方占领返回true，否则返回false
     */
    private boolean isFriendlyCapture(CapturePoint point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        
        String captorName = point.getCaptorName();
        if (captorName.isEmpty()) {
            return false;
        }
        
        return EspetroTeamBridge.isSameTeam(EspetroTeamBridge.getPlayerTeam(mc.player), captorName);
    }
    
    /**
     * 计算两个角度之间的差值（考虑角度环绕）
     * @param previous 上一帧的角度
     * @param current 当前帧的角度
     * @return 角度差值，范围在[-180, 180]之间
     */
    private float calculateAngleDelta(float previous, float current) {
        float delta = current - previous;
        // 确保差值在[-180, 180]范围内
        if (delta > 180.0f) {
            delta -= 360.0f;
        } else if (delta < -180.0f) {
            delta += 360.0f;
        }
        return delta;
    }
    
    /**
     * 获取战术地图HUD实例
     * @return 战术地图HUD实例
     */
    public static TacticalMapHUD getInstance() {
        return InstanceHolder.INSTANCE;
    }
    
    /**
     * 单例持有者
     */
    private static class InstanceHolder {
        private static final TacticalMapHUD INSTANCE = new TacticalMapHUD();
    }
}
