package com.example.espoints.client.gui;

import com.example.espoints.config.MapImageQuality;
import com.example.espoints.config.TacticalMapConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.espetro.client.aui.GuiElement;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 战术地图配置界面。
 */
public class TacticalMapConfigScreen extends EspetroMenuScreen {
    private static final Component TITLE = Component.literal("战术地图配置");
    private static final Pattern HEX_PATTERN = Pattern.compile("#?[0-9A-Fa-f]*");
    private static final Pattern VALID_HEX_PATTERN = Pattern.compile("#?[0-9A-Fa-f]{3}([0-9A-Fa-f]{3})?");

    private final Screen parent;
    private final Map<MapImageQuality, HcrAuiWidgets.ActionButton> qualityButtons =
        new EnumMap<>(MapImageQuality.class);
    private HcrAuiWidgets.TextInput attackerColorInput;
    private HcrAuiWidgets.TextInput defenderColorInput;

    public TacticalMapConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void buildMenuRoot(GuiElement root) {
        int panelW = Math.min(420, Math.max(320, this.width - 36));
        int panelH = 232;
        int panelX = (this.width - panelW) / 2;
        int panelY = Math.max(18, (this.height - panelH) / 2);

        root.addChild(HcrAuiWidgets.panel(panelX, panelY, panelW, panelH,
            HcrAuiWidgets.PANEL, HcrAuiWidgets.BORDER));
        root.addChild(HcrAuiWidgets.text(panelX + 18, panelY + 14, "战术地图配置", HcrAuiWidgets.TEXT));
        root.addChild(HcrAuiWidgets.text(panelX + 18, panelY + 30,
            "图像质量即时生效；进度条颜色使用十六进制 RGB", HcrAuiWidgets.MUTED));
        root.addChild(HcrAuiWidgets.rect(panelX + 18, panelY + 50, panelW - 36, 1, 0x35FFFFFF));

        int labelX = panelX + 24;
        int inputX = panelX + panelW - 146;
        addQualityRow(root, labelX, panelY + 65, panelW);
        addColorRow(root, labelX, inputX, panelY + 112, "攻方进度条颜色", true);
        addColorRow(root, labelX, inputX, panelY + 148, "守方进度条颜色", false);

        root.addChild(HcrAuiWidgets.button(panelX + panelW - 156, panelY + panelH - 32, 64, 18, "保存", () -> {
            saveConfig();
            onClose();
        }).setTextColor(HcrAuiWidgets.GOLD));
        root.addChild(HcrAuiWidgets.button(panelX + panelW - 84, panelY + panelH - 32, 58, 18, "取消", this::onClose)
            .setTextColor(HcrAuiWidgets.MUTED));
    }

    private void addQualityRow(GuiElement root, int labelX, int y, int panelW) {
        root.addChild(HcrAuiWidgets.text(labelX, y + 5,
            "地图图像质量", HcrAuiWidgets.TEXT));
        int buttonWidth = 58;
        int gap = 6;
        int buttonsWidth = buttonWidth * MapImageQuality.values().length
            + gap * (MapImageQuality.values().length - 1);
        int buttonX = Math.max(labelX + 92,
            (this.width - panelW) / 2 + panelW - 24 - buttonsWidth);
        qualityButtons.clear();
        for (MapImageQuality quality : MapImageQuality.values()) {
            HcrAuiWidgets.ActionButton button = HcrAuiWidgets.button(
                buttonX, y, buttonWidth, 20, quality.displayName(),
                () -> selectQuality(quality));
            button.setSelected(TacticalMapConfig.mapImageQuality.get() == quality);
            root.addChild(button);
            qualityButtons.put(quality, button);
            buttonX += buttonWidth + gap;
        }
        root.addChild(HcrAuiWidgets.text(labelX, y + 26,
            "稳定 250 ms 后渐进加载更清晰瓦片", HcrAuiWidgets.DIM));
    }

    private void selectQuality(MapImageQuality quality) {
        TacticalMapConfig.mapImageQuality.set(quality);
        TacticalMapConfig.SPEC.save();
        qualityButtons.forEach((value, button) ->
            button.setSelected(value == quality));
    }

    private void addColorRow(GuiElement root, int labelX, int inputX, int y, String label, boolean attacker) {
        String current = attacker
            ? TacticalMapConfig.attackerProgressBarColor.get()
            : TacticalMapConfig.defenderProgressBarColor.get();
        int swatchColor = parseColor(current, attacker ? 0xFFFF5500 : 0xFF0055FF);

        root.addChild(HcrAuiWidgets.rect(labelX, y + 5, 10, 10, swatchColor));
        root.addChild(HcrAuiWidgets.text(labelX + 18, y + 5, label, HcrAuiWidgets.TEXT));

        HcrAuiWidgets.TextInput input = new HcrAuiWidgets.TextInput(
            inputX, y, 120, 20, current, 7, HEX_PATTERN, value -> {
            }
        );
        root.addChild(input);

        if (attacker) {
            attackerColorInput = input;
        } else {
            defenderColorInput = input;
        }
    }

    private void saveConfig() {
        String attackerColor = normalizeColor(attackerColorInput == null ? "" : attackerColorInput.getValue());
        if (attackerColor != null) {
            TacticalMapConfig.attackerProgressBarColor.set(attackerColor);
        }

        String defenderColor = normalizeColor(defenderColorInput == null ? "" : defenderColorInput.getValue());
        if (defenderColor != null) {
            TacticalMapConfig.defenderProgressBarColor.set(defenderColor);
        }

        TacticalMapConfig.SPEC.save();
    }

    private String normalizeColor(String colorInput) {
        if (colorInput == null || !VALID_HEX_PATTERN.matcher(colorInput).matches()) {
            return null;
        }
        return colorInput.startsWith("#") ? colorInput : "#" + colorInput;
    }

    private int parseColor(String value, int fallback) {
        String normalized = normalizeColor(value);
        if (normalized == null) {
            return fallback;
        }
        try {
            return 0xFF000000 | Integer.parseInt(normalized.substring(1), 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
