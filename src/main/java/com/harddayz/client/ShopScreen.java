package com.harddayz.client;

import java.util.List;

import com.harddayz.network.Net;
import com.harddayz.war.Supply;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Планшет снабжения: каталог с ценами и сроками, бюджет фракции, активные поставки. */
public class ShopScreen extends HdScreen {
    private static final int W = 360;
    private static final int H = 232;
    private static final int ROW = 22;
    private static final int VISIBLE = 7;
    private Net.ShopState state;
    private int scroll;
    private int refresh;

    private List<Integer> catalog() {
        return java.util.stream.IntStream.range(0, Supply.CATALOG.size())
                .filter(i -> Supply.CATALOG.get(i).available()).boxed().toList();
    }

    public ShopScreen(Net.ShopState state) {
        super(Component.translatable("screen.harddayz.shop"));
        this.state = state;
    }

    public static void onState(Net.ShopState s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ShopScreen open) {
            open.state = s;
        } else if (s.open()) {
            mc.setScreen(new ShopScreen(s));
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int l = left();
        int t = top();
        List<Integer> catalog = catalog();
        for (int i = 0; i < VISIBLE && scroll + i < catalog.size(); i++) {
            int idx = catalog.get(scroll + i);
            addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.buy"), b -> Net.toServer(new Net.ShopBuy(idx)))
                    .bounds(l + 168, t + 34 + i * ROW, 52, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scroll = Math.max(0, scroll - VISIBLE);
            rebuild();
        }).bounds(l + 8, t + H - 26, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scroll = Math.min(Math.max(0, catalog.size() - VISIBLE), scroll + VISIBLE);
            rebuild();
        }).bounds(l + 36, t + H - 26, 24, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + 130, t + H - 26, 100, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dy) {
        int ns = Math.max(0, Math.min(Math.max(0, catalog().size() - VISIBLE), scroll - (int) Math.signum(dy)));
        if (ns != scroll) {
            scroll = ns;
            rebuild();
        }
        return true;
    }

    @Override
    public void tick() {
        if (++refresh % 20 == 0) {
            Net.toServer(new Net.ShopBuy(-1)); // обновить бюджет и таймеры
        }
    }

    @Override
    protected void background(Gfx g, int mouseX, int mouseY, float a) {
        g.fill(left(), top(), left() + W, top() + H, 0xEE10161A);
        g.outline(left(), top(), W, H, 0xFF5A8A70);
        g.fill(left() + 226, top() + 28, left() + 227, top() + H - 32, 0xFF2A4A3A);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        int l = left();
        int t = top();
        g.text(font, title, l + 8, t + 8, 0xFFB8F0C8, true);
        if (state.faction().isEmpty()) {
            g.text(font, Component.translatable("message.harddayz.shop_no_faction"), l + 8, t + 20, 0xFFFF7060, false);
        } else {
            g.text(font, Component.literal(state.faction() + "  ·  " + state.budget() + " ₴"), l + 160, t + 8, 0xFFFFD27A, true);
            if (!state.commander()) {
                g.text(font, Component.translatable("screen.harddayz.shop_not_commander"), l + 8, t + 20, 0xFF90A090, false);
            }
        }
        List<Integer> catalog = catalog();
        for (int i = 0; i < VISIBLE && scroll + i < catalog.size(); i++) {
            Supply.Entry e = Supply.CATALOG.get(catalog.get(scroll + i));
            int y = t + 34 + i * ROW;
            var stack = e.stack();
            g.item(stack, l + 8, y + 1);
            g.itemDecorations(font, stack, l + 8, y + 1);
            String name = stack.getHoverName().getString();
            if (font.width(name) > 128) {
                name = font.plainSubstrByWidth(name, 122) + "…";
            }
            boolean afford = state.budget() >= e.price() && (!e.heavy() || state.commander());
            g.text(font, name, l + 28, y, 0xFFE8E8E8, false);
            g.text(font, Component.literal(e.price() + " ₴  ·  " + e.seconds() + " с" + (e.heavy() ? "  ★" : "")), l + 28, y + 10,
                    afford ? 0xFF90C890 : 0xFFC07060, false);
        }
        // поставки
        g.text(font, Component.translatable("screen.harddayz.shop_orders", state.orders().size()), l + 232, t + 30, 0xFFB8F0C8, false);
        List<String> orders = state.orders();
        for (int i = 0; i < Math.min(9, orders.size()); i++) {
            String[] p = orders.get(i).split(";", 3);
            int idx = Integer.parseInt(p[0]);
            if (idx < 0 || idx >= Supply.CATALOG.size()) {
                continue;
            }
            var stack = Supply.CATALOG.get(idx).stack();
            int y = t + 44 + i * 18;
            g.item(stack, l + 232, y);
            int sec = Integer.parseInt(p[1]);
            g.text(font, Component.literal(String.format("%d:%02d", sec / 60, sec % 60)), l + 252, y + 4, 0xFFFFD27A, false);
            g.text(font, Component.literal(p.length > 2 ? p[2] : ""), l + 286, y + 4, 0xFF808880, false);
        }
        g.text(font, Component.translatable("screen.harddayz.shop_hint"), l + 8, t + H - 40, 0xFF708070, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
