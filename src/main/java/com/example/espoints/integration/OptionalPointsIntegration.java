package com.example.espoints.integration;

import com.example.espoints.util.ModLogger;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/** Cached optional points-shop reflection bridge. */
public final class OptionalPointsIntegration {
    private static final String API_CLASS = "com.hcrzb.hcrzbshop.api.PlayerPointsAPI";
    private static volatile Binding binding;
    private static boolean warnedUnavailable;

    private OptionalPointsIntegration() {
    }

    public static boolean add(Player player, int points, String reason) {
        Binding current = binding();
        if (!current.available()) {
            return false;
        }
        try {
            Object result = current.addWithReason() != null
                ? current.addWithReason().invoke(null, player, points, reason)
                : current.add().invoke(null, player, points);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException error) {
            ModLogger.warn("积分商店 addPoints 调用失败: " + error.getMessage());
            return false;
        }
    }

    public static boolean remove(Player player, int points) {
        Binding current = binding();
        if (!current.available()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(current.remove().invoke(null, player, points));
        } catch (ReflectiveOperationException | RuntimeException error) {
            ModLogger.warn("积分商店 removePoints 调用失败: " + error.getMessage());
            return false;
        }
    }

    private static Binding binding() {
        Binding current = binding;
        if (current != null) {
            return current;
        }
        synchronized (OptionalPointsIntegration.class) {
            if (binding != null) {
                return binding;
            }
            try {
                Class<?> api = Class.forName(API_CLASS);
                Method add = null;
                Method addWithReason = null;
                try {
                    addWithReason = api.getMethod(
                        "addPoints", Player.class, int.class, String.class);
                } catch (NoSuchMethodException ignored) {
                    // Older optional integration.
                }
                try {
                    add = api.getMethod("addPoints", Player.class, int.class);
                } catch (NoSuchMethodException ignored) {
                    // Newer integrations may expose only the reason-aware overload.
                }
                if (add == null && addWithReason == null) {
                    throw new NoSuchMethodException("addPoints");
                }
                Method remove = api.getMethod("removePoints", Player.class, int.class);
                binding = new Binding(add, addWithReason, remove);
            } catch (ClassNotFoundException | NoSuchMethodException error) {
                binding = Binding.UNAVAILABLE;
                if (!warnedUnavailable) {
                    warnedUnavailable = true;
                    ModLogger.warn("未检测到兼容的可选积分商店 API；本次运行将跳过积分奖励与惩罚");
                }
            }
            return binding;
        }
    }

    private record Binding(Method add, Method addWithReason, Method remove) {
        private static final Binding UNAVAILABLE = new Binding(null, null, null);

        private boolean available() {
            return (add != null || addWithReason != null) && remove != null;
        }
    }
}
