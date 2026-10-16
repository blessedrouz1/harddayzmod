package com.harddayz.client;

import java.util.List;

import com.harddayz.network.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Экран засечек: откуда стреляли по нам. Клик по строке — координаты в буфер обмена, чтобы ударить в ответ. */
public class LaunchScreen extends HdScreen {
    private static final int ROWS = 12;
    private Net.RadarState state;
    private int scroll;
    private int ticks;
    private String copied = "";
    private int copiedTicks;

    public LaunchScreen(Net.RadarState s) {
        super(Component.translatable("screen.harddayz.launches"));
        this.state = s;
    }

    public void update(Net.RadarState s) {
        state = s;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int bottom = height / 2 + 112;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> scroll = Math.max(0, scroll - ROWS)).bounds(cx - 150, bottom, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> scroll = Math.min(Math.max(0, rows().size() - ROWS), scroll + ROWS))
                .bounds(cx - 126, bottom, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 50, bottom, 100, 20).build());
    }

    /** Свежие сверху. */
    private List<String> rows() {
        return state.launches().stream().sorted((a, b) -> Integer.compare(age(a), age(b))).toList();
    }

    private static int age(String s) {
        return Integer.parseInt(s.split(";")[6]);
    }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) {
            Net.toServer(new Net.RadarRequest(0));
        }
        if (copiedTicks > 0) {
            copiedTicks--;
        }
    }

    private int rowY(int i) {
        return height / 2 - 92 + i * 16;
    }

    @Override
    protected void background(Gfx g, int mouseX, int mouseY, float a) {
        int cx = width / 2;
        g.fill(cx - 160, height / 2 - 122, cx + 160, height / 2 + 140, 0xEE0A1410);
        g.outline(cx - 160, height / 2 - 122, 320, 262, 0xFF2E6A50);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        int cx = width / 2;
        g.text(font, title, cx - 152, height / 2 - 114, 0xFF7CE0A8, true);
        Component net = state.radars() > 0 ? Component.translatable("screen.harddayz.radar_on", state.radars())
                : Component.translatable("screen.harddayz.radar_off");
        g.text(font, net, cx + 152 - font.width(net), height / 2 - 114, state.radars() > 0 ? 0xFF7CE0A8 : 0xFFE06050, true);
        List<String> rows = rows();
        if (rows.isEmpty()) {
            Component none = Component.translatable("screen.harddayz.launches_none");
            g.text(font, none, cx - font.width(none) / 2, height / 2 - 20, 0xFF5AA080, false);
        }
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            String[] p = rows.get(scroll + i).split(";");
            int y = rowY(i);
            boolean hover = mouseX >= cx - 152 && mouseX <= cx + 152 && mouseY >= y - 2 && mouseY < y + 14;
            if (hover) {
                g.fill(cx - 152, y - 2, cx + 152, y + 14, 0x403CB070);
            }
            int ageSec = Integer.parseInt(p[6]) / 20;
            int col = ageSec < 60 ? 0xFFFF6050 : ageSec < 300 ? 0xFFFFB050 : 0xFF90A090;
            g.text(font, Component.translatable("launch.harddayz." + p[0]), cx - 148, y, col, false);
            g.text(font, Component.literal(p[1] + " " + p[2] + " " + p[3]), cx - 30, y, 0xFFE8F0E0, false);
            String info = "±" + p[4] + "  ×" + p[5] + "  " + (ageSec < 60 ? ageSec + "с" : ageSec / 60 + "м");
            g.text(font, Component.literal(info), cx + 148 - font.width(info), y, 0xFF809080, false);
        }
        g.text(font, Component.translatable("screen.harddayz.launches_hint"), cx - 152, height / 2 + 98, 0xFF5AA080, false);
        if (copiedTicks > 0) {
            Component c = Component.translatable("screen.harddayz.copied", copied);
            g.text(font, c, cx - font.width(c) / 2, height / 2 + 86, 0xFF7CE0A8, true);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = width / 2;
        List<String> rows = rows();
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            int y = rowY(i);
            if (mx >= cx - 152 && mx <= cx + 152 && my >= y - 2 && my < y + 14) {
                String[] p = rows.get(scroll + i).split(";");
                copied = p[1] + " " + p[2] + " " + p[3];
                Minecraft.getInstance().keyboardHandler.setClipboard(copied);
                copiedTicks = 60;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, rows().size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
