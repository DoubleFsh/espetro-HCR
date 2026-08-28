package com.example.espoints.client;

import cc.sighs.auratip.api.action.Actions;
import cc.sighs.auratip.api.client.RadialMenuClientApi;
import cc.sighs.auratip.api.radiamenu.RadialMenuBuilder;
import cc.sighs.auratip.api.radiamenu.RadialMenuRegistry;
import cc.sighs.auratip.client.render.RadialMenuOverlay;
import cc.sighs.auratip.data.RadialMenuData;
import com.example.espoints.ESPointsMod;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.PlaceTacticalMarkerMessage;
import com.example.espoints.network.RequestTacticalMarkersMessage;
import com.example.espoints.tactical.ClientTacticalMarkerState;
import com.example.espoints.tactical.TacticalMarkerIcons;
import com.example.espoints.tactical.TacticalMarkerType;
import com.example.espoints.util.EspetroTeamBridge;
import com.example.espoints.util.ModLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;
import nx.pingwheel.common.config.ClientConfig;
import nx.pingwheel.common.core.PingController;
import nx.pingwheel.common.math.Raycast;
import nx.pingwheel.common.util.InputUtils;
import org.espetro.client.gui.ClientGameState;
import org.espetro.team.GamePhase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 长按标点键 → AuraTip 轮盘（战术类型 + ESPoints 贴图）→ raycast 发包。
 * 标点状态仍只在 ESPoints；输入、射线和显示能力复用 Ping Wheel。
 *
 * <p>直接链接固定的 AuraTip（build.gradle 中按 {@code auratipJar} 解析），
 * 不使用反射 / 动态代理。动作处理器只注册一次，注册表被清理后仅重新发布菜单。
 * 所有标点槽位显式 {@code closeAfterAction=true}，选择后立即进入 AuraTip 关闭动画，
 * 关闭语义统一由槽位配置负责，动作处理器不再强制关闭轮盘。
 */
@OnlyIn(Dist.CLIENT)
public final class TacticalMarkRadialController {

    private static final int OPEN_DELAY_TICKS = 4;
    private static final String OWNER = "espoints_mark";
    private static final ResourceLocation MENU_ID =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "tactical_mark");
    private static final ResourceLocation PLACE_ACTION =
        ResourceLocation.fromNamespaceAndPath(ESPointsMod.MOD_ID, "place_tactical_mark");

    private static boolean initialized;
    private static boolean actionsRegistered;
    private static boolean keyWasDown;
    private static boolean ownsOverlay;
    private static boolean consumedUntilRelease;
    private static int heldTicks;
    private static long lastRequestMarkersMs;

    private TacticalMarkRadialController() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerActionsOnce();
        publishMenu();
        ModLogger.info("战术标点 AuraTip 轮盘已注册 (menu=" + MENU_ID + ")");
    }

    /**
     * 动作处理器只注册一次，与菜单发布解耦。
     * {@link RadialMenuRegistry} 被其他模组清空后，只需重新发布菜单，避免重复注册处理器。
     */
    private static void registerActionsOnce() {
        if (actionsRegistered) {
            return;
        }
        actionsRegistered = true;
        Actions.register(PLACE_ACTION, params -> {
            try {
                placeAtLook(TacticalMarkerType.valueOf(params.getString("type", "")));
            } catch (IllegalArgumentException ignored) {
                return;
            }
            // 关闭动画由槽位 closeAfterAction 负责；这里只拦截同一次按键期间的
            // 二次确认（直接鼠标点击后再松开标点键不会重复发包）。
            consumedUntilRelease = true;
            ownsOverlay = false;
        });
    }

    private static void publishMenu() {
        RadialMenuRegistry.setMenus(OWNER, List.of(buildMenuData()));
    }

    static RadialMenuData buildMenuData() {
        RadialMenuBuilder builder = new RadialMenuBuilder(MENU_ID)
            .radii(44, 96)
            .animationSpeed(1.25f)
            .ringColors(List.of("#E6141719", "#F02A2D2F"));
        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            // 敌方单位统一红；进攻黄 / 防守蓝
            String color = switch (type) {
                case ATTACK_HERE -> "#FFFFB52E";
                case DEFEND_HERE -> "#FF4D9DFF";
                case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                     ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> "#FFE05252";
                default -> "#FFE05252";
            };
            // 显式 closeAfterAction=true：选择后立即关闭；绝不使用 persistentSlot。
            builder = builder.slot(
                "espoints.mark." + type.name(),
                TacticalMarkerIcons.textureFor(type),
                Actions.script(PLACE_ACTION, Map.of("type", type.name())),
                Component.literal(type.getDisplayName()),
                color,
                true);
        }
        return builder.build();
    }

    public static void tick(Minecraft mc) {
        if (!initialized) {
            initialize();
        }
        if (mc == null || mc.player == null) {
            reset(false);
            return;
        }
        if (!isActiveBattlefield(mc)) {
            reset(false);
            keyWasDown = false;
            return;
        }
        // Ping Wheel 在 tick start 已经排队；战局内由本轮盘接管，立即撤销默认标点。
        PingController.revokePingAction();
        boolean down = InputUtils.KEY_BINDING_PING.isDown();
        if (!down) {
            if (keyWasDown) {
                finishSelection(mc);
            }
            keyWasDown = false;
            heldTicks = 0;
            consumedUntilRelease = false;
            return;
        }
        keyWasDown = true;
        if (consumedUntilRelease || mc.screen != null) {
            return;
        }
        if (ownsOverlay) {
            return;
        }
        heldTicks++;
        if (heldTicks < OPEN_DELAY_TICKS) {
            return;
        }
        if (!canLocalPlace()) {
            mc.player.displayClientMessage(
                Component.literal("§c当前身份不能放置战术标点。"), true);
            consumedUntilRelease = true;
            return;
        }
        // 打开轮盘前拉一次标点快照，保证 3D 与地图有数据
        requestMarkersIfStale();

        if (openAuraMenu()) {
            ownsOverlay = true;
        } else {
            mc.player.displayClientMessage(
                Component.literal("§c无法打开标点轮盘（菜单未注册或 AuraTip 异常）。"), true);
            consumedUntilRelease = true;
        }
    }

    private static void requestMarkersIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastRequestMarkersMs < 1500L) {
            return;
        }
        // 增量同步正常时无需周期性拉取完整列表；只在本地完全无状态时恢复一次。
        if (ClientTacticalMarkerState.getMarkers().isEmpty()) {
            lastRequestMarkersMs = now;
            NetworkHandler.INSTANCE.sendToServer(new RequestTacticalMarkersMessage());
        }
    }

    private static boolean canLocalPlace() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return false;
        }
        return EspetroTeamBridge.canPlaceTacticalMarkerClientHint(p);
    }

    /** PingController mixin 使用：只在 Espetro 活跃战场接管默认 Ping Wheel。 */
    public static boolean shouldSuppressDefaultPing() {
        return isActiveBattlefield(Minecraft.getInstance());
    }

    static List<String> menuSlotIds() {
        List<String> ids = new ArrayList<>();
        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            ids.add("espoints.mark." + type.name());
        }
        return ids;
    }

    private static boolean isActiveBattlefield(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        GamePhase phase = ClientGameState.getCurrentPhase();
        return (phase == GamePhase.DEPLOYING || phase == GamePhase.BATTLE)
            && !net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension());
    }

    private static void finishSelection(Minecraft mc) {
        if (!ownsOverlay) {
            reset(false);
            return;
        }
        // 松开按键时确认当前悬停槽位；有效选择只发送一次标点请求，随后由
        // AuraTip 依槽位 closeAfterAction 正常关闭；空白区域由 AuraTip 判定关闭。
        if (RadialMenuOverlay.INSTANCE.isActive()) {
            double mouseX = mc.mouseHandler.xpos()
                * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double mouseY = mc.mouseHandler.ypos()
                * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            RadialMenuOverlay.INSTANCE.mouseClicked(
                mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        reset(true);
    }

    private static void reset(boolean keepConsumed) {
        heldTicks = 0;
        ownsOverlay = false;
        if (!keepConsumed) {
            consumedUntilRelease = false;
        }
    }

    private static boolean openAuraMenu() {
        if (!ensureMenusRegistered()) {
            return false;
        }
        try {
            RadialMenuClientApi.open(MENU_ID);
            return true;
        } catch (Throwable t) {
            ModLogger.warn("打开标点轮盘失败: " + t);
            return false;
        }
    }

    private static boolean ensureMenusRegistered() {
        if (RadialMenuRegistry.getRuntimeMenu(MENU_ID) != null) {
            return true;
        }
        // 注册表被其他模组 clearAll/clear 后：只重新发布菜单，不重复注册动作处理器。
        publishMenu();
        return RadialMenuRegistry.getRuntimeMenu(MENU_ID) != null;
    }

    private static void placeAtLook(TacticalMarkerType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || type == null) {
            return;
        }
        Entity cam = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        float pt = mc.getFrameTime();
        Vec3 look = cam.getViewVector(pt);
        ClientConfig pingConfig = ClientConfig.HANDLER.getConfig();
        double maxReach = Math.min(256.0D,
            Math.min(pingConfig.getRaycastDistance(), pingConfig.getPingDistance()));
        HitResult hit = Raycast.traceDirectional(
            look, pt, maxReach, cam.isCrouching());
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            mc.player.displayClientMessage(
                Component.literal("§7准星没有指向可标记位置。"), true);
            return;
        }
        Vec3 pos = hit.getLocation().add(0, 0.25, 0);
        NetworkHandler.INSTANCE.sendToServer(
            new PlaceTacticalMarkerMessage(type, pos.x, pos.y, pos.z));
    }
}