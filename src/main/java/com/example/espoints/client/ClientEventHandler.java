package com.example.espoints.client;

import com.example.espoints.config.TacticalMapJsonConfig;
import com.example.espoints.hud.TacticalMapHUD;
import com.example.espoints.network.NetworkHandler;
import com.example.espoints.network.RequestCapturePointOverviewMessage;
import com.example.espoints.util.ModLogger;
import com.example.espoints.client.gui.MDRenderScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.example.espoints.ESPointsMod;
import org.espetro.Espetro;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ESPointsMod.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {
    private static final String KEY_CATEGORY = "key.categories.espoints";
    private static final String KEY_OPEN_GUI = "key.espoints.open_gui";
    private static final String KEY_TACTICAL_MAP = "key.espoints.tactical_map";
    private static final String KEY_MAP_CONFIG = "key.espoints.map_config";
    private static final String KEY_OPEN_MD_READER = "key.espoints.open_md_reader";
    
    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
        KEY_OPEN_GUI,
        GLFW.GLFW_KEY_UNKNOWN,
        KEY_CATEGORY
    );
    
    public static final KeyMapping TACTICAL_MAP_KEY = new KeyMapping(
        KEY_TACTICAL_MAP,
        GLFW.GLFW_KEY_V,
        KEY_CATEGORY
    );
    
    // 添加新的按键绑定：X键 打开地图配置界面
    public static final KeyMapping MAP_CONFIG_KEY = new KeyMapping(
        KEY_MAP_CONFIG,
        GLFW.GLFW_KEY_X,
        KEY_CATEGORY
    );
    
    // 添加新的按键绑定：无默认键 打开MD文件阅读器
    public static final KeyMapping OPEN_MD_READER_KEY = new KeyMapping(
        KEY_OPEN_MD_READER,
        GLFW.GLFW_KEY_UNKNOWN,
        KEY_CATEGORY
    );
    
    private static boolean wasGuiKeyPressed = false;
    private static boolean wasTacticalMapKeyPressed = false;
    private static boolean wasMapRangeIncreasePressed = false;
    private static boolean wasMapRangeDecreasePressed = false;
    private static boolean wasMapConfigKeyPressed = false;
    private static boolean wasMdReaderKeyPressed = false;
    private static int tacticalMarkerVisibilityTicks;
    
    /**
     * 客户端初始化事件，用于创建音频文件夹
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 创建fightBGM文件夹
        AudioManager.getAudioFilePath();
        ModLogger.info("已检查并确保fightBGM文件夹存在");
        event.enqueueWork(TacticalMarkRadialController::initialize);
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TacticalMapHUD.getInstance().clearServerSyncedBackgroundState();
        TacticalMapJsonConfig.apply(TacticalMapJsonConfig.createDefault(), "client disconnected");
        ClientBattleState.get().clear();
        ClientPlayerIdentityState.get().clear();
        com.example.espoints.tactical.ClientTacticalMarkerState.clear();
        tacticalMarkerVisibilityTicks = 0;
    }
    
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft mc = Minecraft.getInstance();
            ClientTacticalMapTileCache.get().tickRequests();
            // Visible tiles are now mostly local-cache hits; allow a short burst so
            // the first open can replace the blurry preview within a couple of frames.
            ClientTacticalMapTileCache.get().drainUploadQueue(8, 8_000_000L);
            
            // 处理打开GUI按键
            boolean isGuiKeyPressed = OPEN_GUI_KEY.isDown();
            if (isGuiKeyPressed && !wasGuiKeyPressed
                    && mc.player != null && mc.screen == null
                    && !conflictsWithEspetroKey(OPEN_GUI_KEY)) {
                NetworkHandler.INSTANCE.sendToServer(new RequestCapturePointOverviewMessage());
            }
            wasGuiKeyPressed = isGuiKeyPressed;
            
            // 处理战术地图按键
            boolean isTacticalMapPressed = TACTICAL_MAP_KEY.isDown();
            if (isTacticalMapPressed && !wasTacticalMapKeyPressed && mc.player != null) {
                TacticalMapHUD.getInstance().toggleMapVisibility();
            }
            wasTacticalMapKeyPressed = isTacticalMapPressed;

            // 地图缩放已改为打开小地图时用滚轮（TacticalMapHUD.onHudMouseScrolled），
            // 不再绑定 C/B 键，避免与提示词一并误导。
            wasMapRangeIncreasePressed = false;
            wasMapRangeDecreasePressed = false;

            // 处理地图配置界面按键（X键）
            boolean isMapConfigPressed = MAP_CONFIG_KEY.isDown();
            if (isMapConfigPressed && !wasMapConfigKeyPressed
                    && mc.player != null && mc.screen == null
                    && !conflictsWithEspetroKey(MAP_CONFIG_KEY)) {
                mc.setScreen(new com.example.espoints.client.gui.TacticalMapConfigScreen(null));
            }
            wasMapConfigKeyPressed = isMapConfigPressed;
            
            // 处理MD文件阅读器按键
            boolean isMdReaderPressed = OPEN_MD_READER_KEY.isDown();
            if (isMdReaderPressed && !wasMdReaderKeyPressed
                    && mc.player != null && mc.screen == null
                    && !conflictsWithEspetroKey(OPEN_MD_READER_KEY)) {
                mc.setScreen(new MDRenderScreen());
            }
            wasMdReaderKeyPressed = isMdReaderPressed;

            // 战术标点轮盘（ESPoints 自有，3D+地图标点）
            TacticalMarkRadialController.tick(mc);
            if (++tacticalMarkerVisibilityTicks >= 20) {
                tacticalMarkerVisibilityTicks = 0;
                PingWheelMarkerBridge.refreshVisibility(
                    com.example.espoints.tactical.ClientTacticalMarkerState.getMarkers());
            }
        }
    }

    private static boolean conflictsWithEspetroKey(KeyMapping espointsKey) {
        return sameKey(espointsKey, Espetro.KEY_TEAM)
            || sameKey(espointsKey, Espetro.KEY_CLASS)
            || sameKey(espointsKey, Espetro.KEY_SKILL)
            || sameKey(espointsKey, Espetro.KEY_RADIAL);
    }

    private static boolean sameKey(KeyMapping espointsKey, Object espetroKey) {
        return espetroKey instanceof KeyMapping key && espointsKey.same(key);
    }
}
