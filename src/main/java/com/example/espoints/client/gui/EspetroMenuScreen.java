package com.example.espoints.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.espetro.client.aui.AuiScreen;
import org.espetro.client.aui.GuiElement;

/**
 * ESPoints menus share Espetro's AUI host. Custom map / markdown painting
 * still happens in {@link #renderAfterMenu}.
 */
abstract class EspetroMenuScreen extends AuiScreen {

    protected EspetroMenuScreen(Component title) {
        super(title);
    }

    @Override
    protected void renderBeforeMenu(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HcrAuiWidgets.drawScreenShade(graphics, this.width, this.height);
    }

    @Override
    protected abstract void buildMenuRoot(GuiElement root);
}
