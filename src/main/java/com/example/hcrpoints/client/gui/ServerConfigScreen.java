package com.example.hcrpoints.client.gui;

import com.example.hcrpoints.config.ModConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import se.mickelus.mutil.gui.GuiElement;

import java.util.regex.Pattern;

/**
 * 服务端配置界面。
 */
public class ServerConfigScreen extends MutilScreen {
    private static final Component TITLE = Component.literal("服务端配置");
    private static final int MARGIN = 18;
    private static final int HEADER_H = 48;
    private static final int ROW_H = 28;
    private static final int SECTION_H = 24;
    private static final int GAP = 6;
    private static final int INPUT_W = 112;
    private static final Pattern INT_PATTERN = Pattern.compile("\\d*");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("\\d*(\\.\\d*)?");

    private final Screen parentScreen;

    public ServerConfigScreen(Screen parentScreen) {
        super(TITLE);
        this.parentScreen = parentScreen;
    }

    @Override
    protected void buildMutilRoot(GuiElement root) {
        int pageX = MARGIN;
        int pageY = MARGIN;
        int pageW = Math.max(360, this.width - MARGIN * 2);
        int pageH = Math.max(220, this.height - MARGIN * 2);

        root.addChild(HcrMutilWidgets.panel(pageX, pageY, pageW, pageH,
            HcrMutilWidgets.PANEL, HcrMutilWidgets.BORDER));
        buildHeader(root, pageX, pageY, pageW);

        int listX = pageX + 18;
        int listY = pageY + HEADER_H + 12;
        int listW = pageW - 36;
        int listH = pageY + pageH - listY - 18;
        ScrollableList list = new ScrollableList(listX, listY, listW, listH)
            .setScrollStep(ROW_H + GAP)
            .setAlwaysShowScrollbar(true);
        root.addChild(list);

        int y = 0;
        y = addSection(list, y, "HUD 配置");
        y = addBooleanRow(list, y, "启用 HUD 显示", ModConfig.enableHUD);
        y = addBooleanRow(list, y, "启用据点信息轮播", ModConfig.enableCarousel);

        y = addSection(list, y + GAP, "队伍配置");
        y = addInfoRow(list, y, "队伍来源", "Espetro 进攻方 / 防守方");
        y = addBooleanRow(list, y, "显示敌我标识", ModConfig.enableTeamIndicator);

        y = addSection(list, y + GAP, "性能配置");
        y = addIntRow(list, y, "据点检查间隔 (tick)", ModConfig.checkInterval, 1, 100);

        y = addSection(list, y + GAP, "奖励配置");
        y = addIntRow(list, y, "据点内奖励间隔 (秒)", ModConfig.pointRewardInterval, 1, 3600);
        y = addIntRow(list, y, "据点内每次奖励点数", ModConfig.pointRewardAmount, 1, 1000);
        y = addIntRow(list, y, "击杀玩家奖励点数", ModConfig.killRewardAmount, 1, 1000);
        y = addIntRow(list, y, "占领据点奖励点数", ModConfig.captureRewardAmount, 1, 1000);
        y = addIntRow(list, y, "占领后持续奖励间隔 (秒)", ModConfig.capturedRewardInterval, 1, 3600);
        y = addIntRow(list, y, "占领后每次奖励点数", ModConfig.capturedRewardAmount, 1, 1000);
        y = addIntRow(list, y, "占领后奖励延迟 (秒)", ModConfig.capturedRewardDelay, 1, 3600);
        y = addBooleanRow(list, y, "启用友军击杀惩罚", ModConfig.enableFriendlyFirePenalty);
        y = addIntRow(list, y, "友军击杀扣除点数", ModConfig.friendlyFirePenalty, 1, 10000);

        y = addSection(list, y + GAP, "行动攻防机制配置");
        y = addInfoRow(list, y, "行动模式", "固定启用");
        addDoubleRow(list, y, "兵力不足阈值 (%)", ModConfig.lowReinforcementThreshold, 0.0, 100.0);
    }

    private void buildHeader(GuiElement root, int pageX, int pageY, int pageW) {
        root.addChild(HcrMutilWidgets.text(pageX + 18, pageY + 13, "服务端配置", HcrMutilWidgets.TEXT));
        root.addChild(HcrMutilWidgets.text(pageX + 18, pageY + 29, "服务器规则、奖励与行动模式参数", HcrMutilWidgets.MUTED));

        root.addChild(HcrMutilWidgets.button(pageX + pageW - 68, pageY + 14, 50, 18, "完成", this::onClose)
            .setTextColor(HcrMutilWidgets.GOLD));
        root.addChild(HcrMutilWidgets.rect(pageX + 18, pageY + HEADER_H, pageW - 36, 1, 0x35FFFFFF));
    }

    private int addSection(GuiElement parent, int y, String title) {
        parent.addChild(HcrMutilWidgets.rect(0, y + SECTION_H - 3, Math.max(1, parent.getWidth() - 12), 1, 0x35FFFFFF));
        parent.addChild(HcrMutilWidgets.text(0, y + 5, title, HcrMutilWidgets.GOLD));
        return y + SECTION_H;
    }

    private int addBooleanRow(GuiElement parent, int y, String label, ForgeConfigSpec.BooleanValue configValue) {
        addRowShell(parent, y, label);
        HcrMutilWidgets.ActionButton toggle = HcrMutilWidgets.button(
            Math.max(0, parent.getWidth() - INPUT_W - 12), y + 4, INPUT_W, 20,
            configValue.get() ? "是" : "否",
            () -> {
                configValue.set(!configValue.get());
                saveConfig();
                rebuildMutilRoot();
            }
        ).setSelected(configValue.get()).setTextColor(configValue.get() ? HcrMutilWidgets.POSITIVE : HcrMutilWidgets.MUTED);
        parent.addChild(toggle);
        return y + ROW_H + GAP;
    }

    private int addInfoRow(GuiElement parent, int y, String label, String value) {
        addRowShell(parent, y, label);
        int x = Math.max(0, parent.getWidth() - INPUT_W - 12);
        parent.addChild(HcrMutilWidgets.text(x, y + 9, value, HcrMutilWidgets.MUTED));
        return y + ROW_H + GAP;
    }

    private int addIntRow(GuiElement parent, int y, String label, ForgeConfigSpec.IntValue configValue, int min, int max) {
        addRowShell(parent, y, label);
        parent.addChild(new HcrMutilWidgets.TextInput(
            Math.max(0, parent.getWidth() - INPUT_W - 12), y + 4, INPUT_W, 20,
            String.valueOf(configValue.get()), 8, INT_PATTERN,
            value -> {
                if (value.isEmpty()) {
                    return;
                }
                try {
                    int parsed = Integer.parseInt(value);
                    if (parsed >= min && parsed <= max) {
                        configValue.set(parsed);
                        saveConfig();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        ));
        return y + ROW_H + GAP;
    }

    private int addDoubleRow(GuiElement parent, int y, String label, ForgeConfigSpec.DoubleValue configValue, double min, double max) {
        addRowShell(parent, y, label);
        parent.addChild(new HcrMutilWidgets.TextInput(
            Math.max(0, parent.getWidth() - INPUT_W - 12), y + 4, INPUT_W, 20,
            String.valueOf(configValue.get()), 8, DOUBLE_PATTERN,
            value -> {
                if (value.isEmpty()) {
                    return;
                }
                try {
                    double parsed = Double.parseDouble(value);
                    if (parsed >= min && parsed <= max) {
                        configValue.set(parsed);
                        saveConfig();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        ));
        return y + ROW_H + GAP;
    }

    private void addRowShell(GuiElement parent, int y, String label) {
        int rowW = Math.max(1, parent.getWidth() - 12);
        parent.addChild(HcrMutilWidgets.rect(0, y, rowW, ROW_H, 0x50404040));
        parent.addChild(HcrMutilWidgets.text(10, y + 9, label, HcrMutilWidgets.TEXT));
    }

    private void saveConfig() {
        ModConfig.SPEC.save();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
