package com.example.espoints.client;

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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 长按标点键 → AuraTip 轮盘（战术类型 + ESPoints 贴图）→ raycast 发包。
 * 标点状态仍只在 ESPoints；输入、射线和显示能力复用 Ping Wheel。
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
    private static boolean initFailed;
    private static boolean auraAvailable;
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
        // 允许在 AuraTip 晚于 ESPoints 类加载时重试（不永久 initFailed）
        auraAvailable = tryInitAuraTip();
        if (auraAvailable) {
            initialized = true;
            ModLogger.info("战术标点 AuraTip 轮盘已注册 (menu=" + MENU_ID + ")");
        } else if (initFailed) {
            // 真正的 API 不兼容才锁死；类找不到则下次 tick 再试
            initialized = true;
            ModLogger.warn("AuraTip 标点轮盘不可用（将仅提示，不自动误标）");
        }
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

        // 未初始化成功则再试一次（AuraTip 可能刚就绪）
        if (!auraAvailable && !initFailed) {
            initialize();
        }

        if (auraAvailable) {
            // 权限交给服务端；客户端仍尝试打开轮盘，避免粗检误挡
            if (openAuraMenu()) {
                ownsOverlay = true;
            } else {
                mc.player.displayClientMessage(
                    Component.literal("§c无法打开标点轮盘（菜单未注册或 AuraTip 异常）。"), true);
                consumedUntilRelease = true;
            }
        } else {
            mc.player.displayClientMessage(
                Component.literal("§c标点轮盘未就绪：请确认已安装 AuraTip，并查看日志「初始化 AuraTip」。"),
                true);
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
        tryCloseAuraWithClick(mc);
        reset(true);
    }

    private static void reset(boolean keepConsumed) {
        heldTicks = 0;
        ownsOverlay = false;
        if (!keepConsumed) {
            consumedUntilRelease = false;
        }
    }

    private static boolean tryInitAuraTip() {
        try {
            Class<?> actions = Class.forName("cc.sighs.auratip.api.action.Actions");
            Class<?> paramsHandler = Class.forName(
                "cc.sighs.auratip.api.action.Actions$ParamsHandler");
            Class<?> paramsClz = Class.forName("cc.sighs.auratip.api.util.Params");
            Class<?> registry = Class.forName(
                "cc.sighs.auratip.api.radiamenu.RadialMenuRegistry");
            Class<?> builderClz = Class.forName(
                "cc.sighs.auratip.api.radiamenu.RadialMenuBuilder");

            Object handler = Proxy.newProxyInstance(
                TacticalMarkRadialController.class.getClassLoader(),
                new Class<?>[]{paramsHandler},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("equals".equals(name)) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("toString".equals(name)) {
                        return "ESPointsTacticalMarkHandler";
                    }
                    if ("execute".equals(name) && args != null && args.length == 1) {
                        Object params = args[0];
                        Method getString = paramsClz.getMethod(
                            "getString", String.class, String.class);
                        String typeName = String.valueOf(
                            getString.invoke(params, "type", ""));
                        try {
                            placeAtLook(TacticalMarkerType.valueOf(typeName));
                        } catch (IllegalArgumentException ignored) {
                        }
                        consumedUntilRelease = true;
                        ownsOverlay = false;
                        tryCloseOverlay();
                    }
                    return null;
                });

            Method register = actions.getMethod(
                "register", ResourceLocation.class, paramsHandler);
            register.invoke(null, PLACE_ACTION, handler);

            Method script = actions.getMethod(
                "script", ResourceLocation.class, Map.class);
            rebuildMenus(builderClz, registry, script);
            // 校验菜单已进入运行时快照
            Method getRuntime = registry.getMethod(
                "getRuntimeMenu", ResourceLocation.class);
            Object resolved = getRuntime.invoke(null, MENU_ID);
            if (resolved == null) {
                throw new IllegalStateException(
                    "setMenus 后仍无法 resolve 菜单: " + MENU_ID);
            }
            return true;
        } catch (ClassNotFoundException t) {
            // AuraTip 尚未加载：下次 tick 可重试
            ModLogger.warn("AuraTip 类未找到，稍后重试: " + t.getMessage());
            return false;
        } catch (Throwable t) {
            ModLogger.warn("初始化 AuraTip 标点轮盘失败: " + t);
            // NoSuchMethod 等视为硬失败
            initFailed = true;
            return false;
        }
    }

    private static void rebuildMenus(Class<?> builderClz, Class<?> registry, Method script)
        throws Exception {
        Object builder = builderClz.getConstructor(ResourceLocation.class)
            .newInstance(MENU_ID);
        Method radii = builderClz.getMethod("radii", int.class, int.class);
        Method anim = builderClz.getMethod("animationSpeed", float.class);
        Method ring = builderClz.getMethod("ringColors", List.class);
        Method slot = builderClz.getMethod(
            "slot", String.class, ResourceLocation.class,
            Class.forName("cc.sighs.auratip.data.action.Action"),
            Component.class, String.class);
        Method build = builderClz.getMethod("build");

        builder = radii.invoke(builder, 44, 96);
        builder = anim.invoke(builder, 1.25f);
        builder = ring.invoke(builder, List.of("#E6141719", "#F02A2D2F"));

        for (TacticalMarkerType type : TacticalMarkerType.selectableValues()) {
            Map<String, Object> params = new HashMap<>();
            params.put("type", type.name());
            Object action = script.invoke(null, PLACE_ACTION, params);
            // 敌方单位统一红；进攻黄 / 防守蓝
            String color = switch (type) {
                case ATTACK_HERE -> "#FFFFB52E";
                case DEFEND_HERE -> "#FF4D9DFF";
                case ENEMY_INFANTRY, ENEMY_TANK, ENEMY_IFV,
                     ENEMY_LIGHT_VEHICLE, ENEMY_HELICOPTER -> "#FFE05252";
                default -> "#FFE05252";
            };
            builder = slot.invoke(builder,
                "espoints.mark." + type.name(),
                TacticalMarkerIcons.textureFor(type),
                action,
                Component.literal(type.getDisplayName()),
                color);
        }
        Object menuData = build.invoke(builder);
        List<Object> menus = new ArrayList<>();
        menus.add(menuData);
        // 关键：签名是 Collection 不是 List，getMethod(List.class) 会 NoSuchMethodException
        Method setMenus = registry.getMethod(
            "setMenus", String.class, Collection.class);
        setMenus.invoke(null, OWNER, menus);
    }

    private static boolean openAuraMenu() {
        try {
            // 打开前再确保菜单在注册表中（Espetro 可能 clearAll / 重建）
            if (!ensureMenusRegistered()) {
                return false;
            }
            Class<?> clientApi = Class.forName(
                "cc.sighs.auratip.api.client.RadialMenuClientApi");
            Method open = clientApi.getMethod("open", ResourceLocation.class);
            open.invoke(null, MENU_ID);
            return true;
        } catch (Throwable t) {
            ModLogger.warn("打开标点轮盘失败: " + t);
            return false;
        }
    }

    private static boolean ensureMenusRegistered() {
        try {
            Class<?> registry = Class.forName(
                "cc.sighs.auratip.api.radiamenu.RadialMenuRegistry");
            Method getRuntime = registry.getMethod(
                "getRuntimeMenu", ResourceLocation.class);
            if (getRuntime.invoke(null, MENU_ID) != null) {
                return true;
            }
            // 被 Espetro/其他 mod 清掉：重建
            initialized = false;
            initFailed = false;
            auraAvailable = false;
            if (tryInitAuraTip()) {
                initialized = true;
                auraAvailable = true;
                return true;
            }
            return false;
        } catch (Throwable t) {
            return auraAvailable;
        }
    }

    private static void tryCloseAuraWithClick(Minecraft mc) {
        try {
            Class<?> overlay = Class.forName(
                "cc.sighs.auratip.client.render.RadialMenuOverlay");
            Object instance = overlay.getField("INSTANCE").get(null);
            Method isActive = overlay.getMethod("isActive");
            if (Boolean.TRUE.equals(isActive.invoke(instance))) {
                double mouseX = mc.mouseHandler.xpos()
                    * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
                double mouseY = mc.mouseHandler.ypos()
                    * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
                Method click = overlay.getMethod(
                    "mouseClicked", double.class, double.class, int.class);
                click.invoke(instance, mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
        } catch (Throwable ignored) {
            tryCloseOverlay();
        }
    }

    private static void tryCloseOverlay() {
        try {
            Class<?> overlay = Class.forName(
                "cc.sighs.auratip.client.render.RadialMenuOverlay");
            Object instance = overlay.getField("INSTANCE").get(null);
            Method isActive = overlay.getMethod("isActive");
            if (Boolean.TRUE.equals(isActive.invoke(instance))) {
                overlay.getMethod("close").invoke(instance);
            }
        } catch (Throwable ignored) {
        }
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
