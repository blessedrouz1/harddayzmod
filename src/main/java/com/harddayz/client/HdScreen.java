package com.harddayz.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран с разделением на фон и содержимое, как в новых версиях (extractBackground / extractRenderState):
 * {@link #background} рисуется под кнопками, {@link #content} — поверх.
 */
public abstract class HdScreen extends Screen {
    protected HdScreen(Component title) {
        super(title);
    }

    protected void background(Gfx g, int mouseX, int mouseY, float pt) {
    }

    protected void content(Gfx g, int mouseX, int mouseY, float pt) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float pt) {
        renderBackground(graphics);
        Gfx g = new Gfx(graphics);
        background(g, mouseX, mouseY, pt);
        super.render(graphics, mouseX, mouseY, pt);
        content(g, mouseX, mouseY, pt);
    }
}
