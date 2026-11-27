package com.harddayz.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Настройки ботов матча: сколько, насколько сильные, сколько денег у их штаба и чем им можно воевать. */
public class BotSettingsScreen extends HdScreen {
    /** Номер бита — ключ подписи screen.harddayz.bot_opt.N (биты как в War.BOT_*). */
    private static final int[] OPTS = {0, 9, 1, 2, 4, 5, 3, 6, 7, 8, 10, 11, 12};
    private final WarSetupScreen parent;

    public BotSettingsScreen(WarSetupScreen parent) {
        super(Component.translatable("screen.harddayz.bots_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int y = height / 2 - 110;
        y = row(y, "screen.harddayz.war_bots", () -> WarSetupScreen.bots, v -> WarSetupScreen.bots = Math.max(0, Math.min(40, v)), 2);
        y = row(y, "screen.harddayz.war_difficulty", () -> WarSetupScreen.difficulty, v -> WarSetupScreen.difficulty = Math.max(1, Math.min(3, v)), 1);
        y = row(y, "screen.harddayz.bot_money", () -> WarSetupScreen.botMoney, v -> WarSetupScreen.botMoney = Math.max(0, Math.min(400, v)), 25);
        y += 6;
        for (int i = 0; i < OPTS.length; i++) {
            int bit = OPTS[i];
            int bx = i % 2 == 0 ? cx - 152 : cx + 2;
            addRenderableWidget(Button.builder(label(bit), b -> {
                WarSetupScreen.botMask ^= 1 << bit;
                b.setMessage(label(bit));
            }).bounds(bx, y + i / 2 * 22, 150, 20).build());
        }
        y += (OPTS.length + 1) / 2 * 22 + 6;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> minecraft.setScreen(parent)).bounds(cx - 75, y, 150, 20).build());
    }

    private static Component label(int bit) {
        boolean on = (WarSetupScreen.botMask & 1 << bit) != 0;
        return Component.translatable("screen.harddayz.bot_opt." + bit).append(": ").append(on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    private final java.util.List<Object[]> labels = new java.util.ArrayList<>();

    private int row(int y, String key, java.util.function.IntSupplier get, java.util.function.IntConsumer set, int step) {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> set.accept(get.getAsInt() - step)).bounds(cx + 50, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> set.accept(get.getAsInt() + step)).bounds(cx + 110, y, 20, 20).build());
        labels.add(new Object[] {key, get, y});
        return y + 24;
    }

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
        g.fill(cx - 160, height / 2 - 132, cx + 160, height / 2 + 150, 0xEE101418);
        g.outline(cx - 160, height / 2 - 132, 320, 282, 0xFF5A6A40);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        int cx = width / 2;
        g.text(font, title, cx - font.width(title) / 2, height / 2 - 124, 0xFFE0C060, true);
        for (Object[] l : labels) {
            int y = (int) l[2];
            g.text(font, Component.translatable((String) l[0]), cx - 150, y + 6, 0xFFE8F0E0, false);
            String v = String.valueOf(((java.util.function.IntSupplier) l[1]).getAsInt());
            if (l[0].equals("screen.harddayz.bot_money")) {
                v += "%";
            }
            g.text(font, Component.literal(v), cx + 90 - font.width(v) / 2, y + 6, 0xFFFFFFFF, true);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
