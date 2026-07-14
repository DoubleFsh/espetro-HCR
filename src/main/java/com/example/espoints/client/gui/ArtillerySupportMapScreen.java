package com.example.espoints.client.gui;

import com.example.espoints.hud.TacticalMapHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ArtillerySupportMapScreen extends Screen {
    private static final int REFERENCE_TITLE_H = 22;
    private static final int REFERENCE_STATUS_BAR_H = 15;
    private static final int REFERENCE_VERTICAL_GAP = 6;
    private static final int REFERENCE_CENTER_GAP = 4;
    private static final int REFERENCE_EDGE_MARGIN = 4;

    public ArtillerySupportMapScreen() {
        super(Component.literal("155火炮支援"));
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
    public void removed() {
        TacticalMapHUD.getInstance().endArtillerySelection();
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int mapW = getReferenceMapWidth();
        int mapH = getReferenceMapHeight();
        int mapX = (this.width - mapW) / 2;
        int mapY = (this.height - mapH) / 2;
        TacticalMapHUD.getInstance().renderArtillerySelectionMap(graphics, mapX, mapY, mapW, mapH, partialTick);
    }

    private int getReferenceMapWidth() {
        int referenceMapX = this.width / 2 + REFERENCE_CENTER_GAP;
        return Math.max(1, this.width - referenceMapX - REFERENCE_EDGE_MARGIN);
    }

    private int getReferenceMapHeight() {
        return Math.max(1, this.height - REFERENCE_TITLE_H - REFERENCE_STATUS_BAR_H - REFERENCE_VERTICAL_GAP);
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
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
