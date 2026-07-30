package com.example.espoints.client;

import com.example.espoints.ESPointsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ESPointsMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientProxy {
    
    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        // 注册按键绑定
        event.register(ClientEventHandler.OPEN_GUI_KEY);
        event.register(ClientEventHandler.TACTICAL_MAP_KEY);
        event.register(ClientEventHandler.MAP_CONFIG_KEY); // 注册地图配置界面按键
        event.register(ClientEventHandler.OPEN_MD_READER_KEY); // 注册MD文件阅读器按键
        // 战术标点直接复用 Ping Wheel 的按键，不再注册第二个冲突键位。
    }
}
