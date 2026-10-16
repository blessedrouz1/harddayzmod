package com.harddayz.client;

import com.harddayz.network.Net;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Меню матча: выбор режима и настроек кнопками вместо команд в чате. */
public class WarSetupScreen extends HdScreen {
    /** 0 — штабы и рубежи, 1 — жизни, 2 — штаб и город. */
    private int mode;
    private int factions = 2;
    private int budget = 5000;
    private int lives = 20;
    private int minutes = 60;
    private int radius = 250;

    public WarSetupScreen() {
        super(Component.translatable("screen.harddayz.war_setup"));
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int y = height / 2 - 90;
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.war_mode." + mode), b -> {
            mode = (mode + 1) % 3;
            init();
        }).bounds(cx - 110, y, 220, 20).build());
        y += 30;
        if (mode != 2) {
            y = row(y, "screen.harddayz.war_factions", () -> factions, v -> factions = Math.max(2, Math.min(4, v)), 1);
        }
        y = row(y, "screen.harddayz.war_budget", () -> budget, v -> budget = Math.max(0, Math.min(100000, v)), 1000);
        if (mode == 1) {
            y = row(y, "screen.harddayz.war_lives", () -> lives, v -> lives = Math.max(1, Math.min(500, v)), 5);
        }
        if (mode == 2) {
            y = row(y, "screen.harddayz.war_minutes", () -> minutes, v -> minutes = Math.max(5, Math.min(600, v)), 5);
            y = row(y, "screen.harddayz.war_radius", () -> radius, v -> radius = Math.max(30, Math.min(2000, v)), 50);
        }
        y += 10;
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.war_start"), b -> {
            Net.toServer(new Net.WarSetup(mode, factions, budget, lives, minutes, radius));
            onClose();
        }).bounds(cx - 110, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(cx + 5, y, 105, 20).build());
    }

    private int row(int y, String key, java.util.function.IntSupplier get, java.util.function.IntConsumer set, int step) {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> set.accept(get.getAsInt() - step)).bounds(cx + 30, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> set.accept(get.getAsInt() + step)).bounds(cx + 90, y, 20, 20).build());
        labels.add(new Object[] {key, get, y});
        return y + 24;
    }

    private final java.util.List<Object[]> labels = new java.util.ArrayList<>();

    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        if (labels != null) {
            labels.clear();
        }
    }

    @Override
    protected void background(Gfx g, int mouseX, int mouseY, float a) {
        int cx = width / 2;
        g.fill(cx - 125, height / 2 - 120, cx + 125, height / 2 + 160, 0xEE101418);
        g.outline(cx - 125, height / 2 - 120, 250, 280, 0xFF5A6A40);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        int cx = width / 2;
        g.text(font, title, cx - font.width(title) / 2, height / 2 - 110, 0xFFE0C060, true);
        for (Object[] l : labels) {
            int y = (int) l[2];
            g.text(font, Component.translatable((String) l[0]), cx - 110, y + 6, 0xFFE8F0E0, false);
            String v = String.valueOf(((java.util.function.IntSupplier) l[1]).getAsInt());
            g.text(font, Component.literal(v), cx + 70 - font.width(v) / 2, y + 6, 0xFFFFFFFF, true);
        }
        Component hint = Component.translatable("screen.harddayz.war_hint." + mode);
        var lines = font.split(hint, 230);
        int y = height / 2 + 112;
        for (var line : lines) {
            g.g.drawString(font, line, cx - 115, y, 0xFF909890, false);
            y += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
