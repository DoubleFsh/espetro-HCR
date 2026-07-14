package com.example.espoints.network;

import com.example.espoints.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 服务端通知客户端打开155火炮支援选点地图。
 */
public class OpenArtillerySupportMapMessage {
    private static final String SCREEN_CLASS =
        "com.example.espoints.client.gui.ArtillerySupportMapScreen";

    public static void encode(OpenArtillerySupportMapMessage message, FriendlyByteBuf buf) {
    }

    public static OpenArtillerySupportMapMessage decode(FriendlyByteBuf buf) {
        return new OpenArtillerySupportMapMessage();
    }

    public static void handle(OpenArtillerySupportMapMessage message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                openClientScreen();
            }
        });
        context.setPacketHandled(true);
    }

    private static void openClientScreen() {
        try {
            Class<?> screenClass = Class.forName(SCREEN_CLASS);
            screenClass.getMethod("open").invoke(null);
        } catch (ReflectiveOperationException e) {
            ModLogger.syncError("Failed to open artillery support tactical map: " + e.getMessage());
        }
    }

    public static void sendTo(ServerPlayer player) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
            new OpenArtillerySupportMapMessage());
    }
}
