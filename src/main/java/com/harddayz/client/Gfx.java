package com.harddayz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Тонкая обёртка над GuiGraphics 1.20.1 с именами методов из новых версий (GuiGraphicsExtractor). */
public final class Gfx {
    public final GuiGraphics g;

    public Gfx(GuiGraphics g) {
        this.g = g;
    }

    public int guiWidth() {
        return g.guiWidth();
    }

    public int guiHeight() {
        return g.guiHeight();
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    public void outline(int x, int y, int w, int h, int color) {
        g.renderOutline(x, y, w, h, color);
    }

    public void text(Font font, String s, int x, int y, int color, boolean shadow) {
        g.drawString(font, s, x, y, color, shadow);
    }

    public void text(Font font, Component s, int x, int y, int color, boolean shadow) {
        g.drawString(font, s, x, y, color, shadow);
    }

    public void centeredText(Font font, String s, int x, int y, int color) {
        g.drawCenteredString(font, s, x, y, color);
    }

    public void centeredText(Font font, Component s, int x, int y, int color) {
        g.drawCenteredString(font, s, x, y, color);
    }

    public void item(net.minecraft.world.item.ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
    }

    public void itemDecorations(Font font, net.minecraft.world.item.ItemStack stack, int x, int y) {
        g.renderItemDecorations(font, stack, x, y);
    }
}
