package com.harddayz.client;

import com.harddayz.ClientConfig;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Меню настроек мода (Моды → HardDayz → Настроить). */
public class HdConfigScreen extends Screen {
    private final Screen parent;

    public HdConfigScreen(Screen parent) {
        super(Component.translatable("screen.harddayz.config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height / 4;
        toggle(ClientConfig.BLAST_PARTICLES, "config.harddayz.blast_particles", y);
        toggle(ClientConfig.BLAST_SOUNDS, "config.harddayz.blast_sounds", y + 24);
        toggle(ClientConfig.SCREEN_SHAKE, "config.harddayz.screen_shake", y + 48);
        toggle(ClientConfig.SKY_FLASH, "config.harddayz.sky_flash", y + 72);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, y + 110, 200, 20).build());
    }

    private void toggle(ForgeConfigSpec.BooleanValue v, String key, int y) {
        addRenderableWidget(Button.builder(label(v, key), b -> {
            v.set(!v.get());
            v.save();
            b.setMessage(label(v, key));
        }).bounds(width / 2 - 140, y, 280, 20).build());
    }

    private static Component label(ForgeConfigSpec.BooleanValue v, String key) {
        return Component.translatable(key).append(": ").append(v.get() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, title, width / 2, height / 4 - 24, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
