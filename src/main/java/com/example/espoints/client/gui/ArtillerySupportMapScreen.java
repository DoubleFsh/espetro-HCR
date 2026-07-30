package com.example.espoints.client.gui;

import com.example.espoints.hud.TacticalMapHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import se.mickelus.mutil.gui.GuiElement;

/** MUtil-hosted commander map used to select an artillery target. */
public class ArtillerySupportMapScreen extends MutilScreen {
    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;

    public ArtillerySupportMapScreen() {
        super(Component.literal("火炮支援"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new ArtillerySupportMapScreen());
    }

    @Override
    protected void init() {
        super.init();
        TacticalMapHUD.getInstance().beginArtillerySelection();
    }

    @Override
    protected void buildMutilRoot(GuiElement root) {
        computeMapBounds();
        root.addChild(HcrMutilWidgets.panel(mapX, mapY, mapW, mapH,
            0x00000000, HcrMutilWidgets.BORDER_ACTIVE));
        root.addChild(HcrMutilWidgets.text(8, 7,
            "§6§l火炮支援选点", HcrMutilWidgets.GOLD));
        root.addChild(HcrMutilWidgets.text(8, 20,
            "§7右键选择目标，滚轮缩放地图", HcrMutilWidgets.MUTED));
        root.addChild(HcrMutilWidgets.button(width - 50, 6, 42, 18,
            "返回", this::onClose));
    }

    @Override
    protected void renderBeforeMutil(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HcrMutilWidgets.drawScreenShade(graphics, this.width, this.height);
        computeMapBounds();
        TacticalMapHUD.getInstance().renderArtillerySelectionMap(
            graphics, mapX, mapY, mapW, mapH, partialTick);
    }

    private void computeMapBounds() {
        mapX = 6;
        mapY = 36;
        mapW = Math.max(1, width - 12);
        mapH = Math.max(1, height - mapY - 8);
    }

    @Override
    public void removed() {
        TacticalMapHUD.getInstance().endArtillerySelection();
        super.removed();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT
            && TacticalMapHUD.getInstance().submitArtillerySelectionTarget(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (TacticalMapHUD.getInstance().zoomArtillerySelectionMap(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
