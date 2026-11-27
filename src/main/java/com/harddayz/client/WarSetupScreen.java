package com.harddayz.client;

import com.harddayz.network.Net;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Меню матча: выбор режима и настроек кнопками вместо команд в чате. */
public class WarSetupScreen extends HdScreen {
    /** 0 — штабы и рубежи, 1 — жизни, 2 — штаб и город, 3 — вместе против ботов, 4 — выжить под обстрелом. */
    private int mode;
    private int factions = 2;
    private int budget = 5000;
    private int lives = 20;
    private int minutes = 60;
    private int radius = 250;
    static int bots = 12;
    static int difficulty = 2;
    static int botMask = -1;
    static int botMoney = 100;
    private int prep = 5;

    public WarSetupScreen() {
        super(Component.translatable("screen.harddayz.war_setup"));
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int y = height / 2 - 94;
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.war_mode." + mode), b -> {
            mode = (mode + 1) % 5;
            init();
        }).bounds(cx - 110, y, 220, 20).build());
        y += 30;
        if (mode < 2) {
            y = row(y, "screen.harddayz.war_factions", () -> factions, v -> factions = Math.max(2, Math.min(4, v)), 1);
        }
        y = row(y, "screen.harddayz.war_budget", () -> budget, v -> budget = Math.max(0, Math.min(100000, v)), 1000);
        if (mode == 1) {
            y = row(y, "screen.harddayz.war_lives", () -> lives, v -> lives = Math.max(1, Math.min(500, v)), 5);
        }
        if (mode == 4) {
            y = row(y, "screen.harddayz.war_radius", () -> radius, v -> radius = Math.max(40, Math.min(600, v)), 20);
        }
        if (mode == 2) {
            y = row(y, "screen.harddayz.war_minutes", () -> minutes, v -> minutes = Math.max(5, Math.min(600, v)), 5);
            y = row(y, "screen.harddayz.war_radius", () -> radius, v -> radius = Math.max(30, Math.min(2000, v)), 50);
        }
        if (mode == 4) {
            y = row(y, "screen.harddayz.war_difficulty", () -> difficulty, v -> difficulty = Math.max(1, Math.min(3, v)), 1);
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.bots_button", bots, difficulty), b -> minecraft.setScreen(new BotSettingsScreen(this)))
                .bounds(width / 2 - 110, y, 220, 20).build());
        y += 24;
        y = row(y, "screen.harddayz.war_prep", () -> prep, v -> prep = Math.max(0, Math.min(30, v)), 1);
        y += 6;
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.war_start"), b -> {
            Net.toServer(new Net.WarSetup(mode, factions, budget, lives, minutes, radius, bots, difficulty, prep, botMask, botMoney));
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
        g.fill(cx - 125, height / 2 - 120, cx + 125, height / 2 + 168, 0xEE101418);
        g.outline(cx - 125, height / 2 - 120, 250, 288, 0xFF5A6A40);
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
        int y = height / 2 + 122;
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
