package com.harddayz.client;

import com.harddayz.network.Net;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Ход матча в реальном времени: режим, время, объекты, команды. Создатель может закончить матч. */
public class MatchScreen extends HdScreen {
    private Net.OpenWarMenu state;
    private int ticks;
    private boolean confirm;

    public MatchScreen(Net.OpenWarMenu s) {
        super(Component.translatable("screen.harddayz.match"));
        this.state = s;
    }

    public void update(Net.OpenWarMenu s) {
        boolean rebuild = s.creator() != state.creator();
        state = s;
        if (rebuild) {
            init();
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int bottom = height / 2 + 96;
        if (state.creator()) {
            addRenderableWidget(Button.builder(Component.translatable(confirm ? "screen.harddayz.match_end_sure" : "screen.harddayz.match_end"), b -> {
                if (confirm) {
                    Net.toServer(new Net.WarMenuRequest(1));
                    onClose();
                } else {
                    confirm = true;
                    init();
                }
            }).bounds(cx - 150, bottom, 140, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 50, bottom, 100, 20).build());
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            Net.toServer(new Net.WarMenuRequest(0));
        }
    }

    @Override
    protected void background(Gfx g, int mouseX, int mouseY, float a) {
        int cx = width / 2;
        g.fill(cx - 160, height / 2 - 110, cx + 160, height / 2 + 124, 0xEE101418);
        g.outline(cx - 160, height / 2 - 110, 320, 234, 0xFF5A6A40);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        int cx = width / 2;
        g.text(font, title, cx - font.width(title) / 2, height / 2 - 102, 0xFFE0C060, true);
        int y = height / 2 - 84;
        for (String line : state.lines()) {
            g.text(font, Component.literal(line), cx - 150, y, 0xFFE8F0E0, false);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
