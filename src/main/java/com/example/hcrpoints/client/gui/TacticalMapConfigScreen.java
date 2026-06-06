package com.example.hcrpoints.client.gui;

import com.example.hcrpoints.config.TacticalMapConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 战术地图配置界面
 */
public class TacticalMapConfigScreen extends Screen {
    private static final Component TITLE = Component.literal("战术地图配置");
    private static final Component SCALE_LABEL = Component.literal("迷你地图缩放百分比：");
    private static final Component ATTACKER_COLOR_LABEL = Component.literal("攻方进度条颜色：");
    private static final Component DEFENDER_COLOR_LABEL = Component.literal("守方进度条颜色：");
    private static final Component SAVE_BUTTON = Component.literal("保存");
    private static final Component CANCEL_BUTTON = Component.literal("取消");
    
    private final Screen parent;
    
    // 输入框控件
    private EditBox scaleInput;
    private EditBox attackerColorInput;
    private EditBox defenderColorInput;
    
    // 按钮控件
    private Button saveButton;
    private Button cancelButton;
    
    public TacticalMapConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        // 计算界面中心位置
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        
        // 迷你地图缩放输入框
        Component scaleInputComponent = Component.literal("scale_input");
        this.scaleInput = new EditBox(
            this.font,
            centerX - 100,
            startY + 20,
            100,
            20,
            scaleInputComponent
        );
        this.scaleInput.setMaxLength(3);
        this.scaleInput.setValue(String.valueOf(TacticalMapConfig.miniMapScale.get()));
        this.scaleInput.setTooltip(Tooltip.create(Component.literal("输入25-100之间的数值")));
        this.scaleInput.setFilter(s -> s.isEmpty() || s.matches("\\d*"));
        this.addRenderableWidget(this.scaleInput);
        
        // 攻方颜色输入框
        Component attackerColorInputComponent = Component.literal("attacker_color_input");
        this.attackerColorInput = new EditBox(
            this.font,
            centerX - 100,
            startY + 60,
            100,
            20,
            attackerColorInputComponent
        );
        this.attackerColorInput.setMaxLength(7);
        this.attackerColorInput.setValue(TacticalMapConfig.attackerProgressBarColor.get());
        this.attackerColorInput.setTooltip(Tooltip.create(Component.literal("输入十六进制颜色值，如：#FF5500")));
        this.attackerColorInput.setFilter(s -> s.isEmpty() || s.matches("#?[0-9A-Fa-f]*"));
        this.addRenderableWidget(this.attackerColorInput);
        
        // 守方颜色输入框
        Component defenderColorInputComponent = Component.literal("defender_color_input");
        this.defenderColorInput = new EditBox(
            this.font,
            centerX - 100,
            startY + 100,
            100,
            20,
            defenderColorInputComponent
        );
        this.defenderColorInput.setMaxLength(7);
        this.defenderColorInput.setValue(TacticalMapConfig.defenderProgressBarColor.get());
        this.defenderColorInput.setTooltip(Tooltip.create(Component.literal("输入十六进制颜色值，如：#0055FF")));
        this.defenderColorInput.setFilter(s -> s.isEmpty() || s.matches("#?[0-9A-Fa-f]*"));
        this.addRenderableWidget(this.defenderColorInput);
        
        // 保存按钮
        this.saveButton = this.addRenderableWidget(
            Button.builder(SAVE_BUTTON, button -> {
                this.saveConfig();
                this.onClose();
            })
            .bounds(centerX - 110, startY + 140, 100, 20)
            .build()
        );
        
        // 取消按钮
        this.cancelButton = this.addRenderableWidget(
            Button.builder(CANCEL_BUTTON, button -> {
                this.onClose();
            })
            .bounds(centerX + 10, startY + 140, 100, 20)
            .build()
        );
        
        // 初始化保存按钮状态
        this.updateSaveButtonStatus();
    }
    
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 渲染背景
        this.renderBackground(guiGraphics);
        
        // 渲染标题
        guiGraphics.drawCenteredString(
            this.font,
            TITLE,
            this.width / 2,
            this.height / 2 - 80,
            0xFFFFFF
        );
        
        // 渲染标签
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        
        // 缩放标签
        guiGraphics.drawString(
            this.font,
            SCALE_LABEL,
            centerX - 100,
            startY,
            0xFFFFFF,
            false
        );
        guiGraphics.drawString(
            this.font,
            "%",
            centerX + 5,
            startY + 20,
            0xFFFFFF,
            false
        );
        
        // 攻方颜色标签
        guiGraphics.drawString(
            this.font,
            ATTACKER_COLOR_LABEL,
            centerX - 100,
            startY + 40,
            0xFFFFFF,
            false
        );
        
        // 守方颜色标签
        guiGraphics.drawString(
            this.font,
            DEFENDER_COLOR_LABEL,
            centerX - 100,
            startY + 80,
            0xFFFFFF,
            false
        );
        
        // 渲染所有控件
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
    
    @Override
    public void tick() {
        // 更新输入框状态
        this.scaleInput.tick();
        this.attackerColorInput.tick();
        this.defenderColorInput.tick();
        
        // 更新保存按钮状态
        this.updateSaveButtonStatus();
    }
    
    /**
     * 更新保存按钮的状态
     */
    private void updateSaveButtonStatus() {
        boolean isScaleValid = this.isScaleInputValid();
        boolean isAttackerColorValid = this.isColorInputValid(this.attackerColorInput.getValue());
        boolean isDefenderColorValid = this.isColorInputValid(this.defenderColorInput.getValue());
        
        this.saveButton.active = isScaleValid && isAttackerColorValid && isDefenderColorValid;
    }
    
    /**
     * 检查缩放输入是否有效
     */
    private boolean isScaleInputValid() {
        try {
            String value = this.scaleInput.getValue();
            if (value.isEmpty()) {
                return false;
            }
            int scale = Integer.parseInt(value);
            return scale >= 25 && scale <= 100;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * 检查颜色输入是否有效
     */
    private boolean isColorInputValid(String colorInput) {
        if (colorInput.isEmpty()) {
            return false;
        }
        return colorInput.matches("#?[0-9A-Fa-f]{3}([0-9A-Fa-f]{3})?");
    }
    
    @Override
    public void onClose() {
        // 返回上一个界面
        this.minecraft.setScreen(this.parent);
    }
    
    /**
     * 保存配置
     */
    private void saveConfig() {
        // 保存缩放配置
        try {
            int scale = Integer.parseInt(this.scaleInput.getValue());
            if (scale >= 25 && scale <= 100) {
                TacticalMapConfig.miniMapScale.set(scale);
            }
        } catch (NumberFormatException e) {
            // 忽略无效输入
        }
        
        // 保存攻方颜色配置
        String attackerColor = this.attackerColorInput.getValue();
        if (this.isColorInputValid(attackerColor)) {
            if (!attackerColor.startsWith("#")) {
                attackerColor = "#" + attackerColor;
            }
            TacticalMapConfig.attackerProgressBarColor.set(attackerColor);
        }
        
        // 保存守方颜色配置
        String defenderColor = this.defenderColorInput.getValue();
        if (this.isColorInputValid(defenderColor)) {
            if (!defenderColor.startsWith("#")) {
                defenderColor = "#" + defenderColor;
            }
            TacticalMapConfig.defenderProgressBarColor.set(defenderColor);
        }
        
        // 保存配置到文件
        TacticalMapConfig.SPEC.save();
    }
}