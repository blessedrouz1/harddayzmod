package com.harddayz.client;

import com.harddayz.network.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Радарный экран: круговая развёртка вокруг игрока, метки целей, которые реально видят РЛС сети. */
public class RadarScreen extends Screen {
    private static final int[] RANGES = {150, 300, 600, 1200};
    private Net.RadarState state;
    private int range = 2;
    private int ticks;

    public RadarScreen(Net.RadarState s) {
        super(Component.translatable("screen.harddayz.radar"));
        this.state = s;
    }

    public static void onState(Net.RadarState s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof RadarScreen r) {
            r.state = s;
        } else if (s.open()) {
            mc.gui.setScreen(new RadarScreen(s));
        }
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int bottom = height / 2 + 112;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> range = Math.min(RANGES.length - 1, range + 1))
                .bounds(cx - 120, bottom, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> range = Math.max(0, range - 1)).bounds(cx - 96, bottom, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(cx + 20, bottom, 100, 20).build());
    }

    @Override
    public void tick() {
        if (++ticks % 10 == 0) {
            Net.toServer(new Net.RadarRequest(false));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int cx = width / 2;
        int cy = height / 2 - 6;
        int r = 104;
        g.fill(cx - r - 14, cy - r - 22, cx + r + 14, cy + r + 44, 0xEE0A1410);
        g.outline(cx - r - 14, cy - r - 22, 2 * r + 28, 2 * r + 66, 0xFF2E6A50);
        // кольца и оси
        for (int ring = 1; ring <= 3; ring++) {
            circle(g, cx, cy, r * ring / 3, 0xFF1E4A38);
        }
        g.fill(cx - r, cy, cx + r, cy + 1, 0xFF1E4A38);
        g.fill(cx, cy - r, cx + 1, cy + r, 0xFF1E4A38);
        // луч развёртки
        double ang = (ticks + a) * 0.08;
        for (int i = 0; i < r; i += 2) {
            int x = cx + (int) (Math.sin(ang) * i);
            int y = cy - (int) (Math.cos(ang) * i);
            g.fill(x, y, x + 2, y + 2, 0x9040C890);
        }
    }

    private static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int col) {
        for (int i = 0; i < 180; i++) {
            double t = i * Math.PI * 2 / 180;
            int x = cx + (int) Math.round(Math.cos(t) * r);
            int y = cy + (int) Math.round(Math.sin(t) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Minecraft mc = Minecraft.getInstance();
        int cx = width / 2;
        int cy = height / 2 - 6;
        int r = 104;
        int rangeBlocks = RANGES[range];
        g.text(font, title, cx - r - 8, cy - r - 16, 0xFF7CE0A8, true);
        Component net = state.radars() > 0 ? Component.translatable("screen.harddayz.radar_on", state.radars())
                : Component.translatable("screen.harddayz.radar_off");
        g.text(font, net, cx + r - font.width(net) + 8, cy - r - 16, state.radars() > 0 ? 0xFF7CE0A8 : 0xFFE06050, true);
        g.text(font, Component.translatable("screen.harddayz.radar_range", rangeBlocks), cx - r - 8, cy + r + 8, 0xFF5AA080, false);
        // север вверх: x → вправо, z → вниз
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF7CE0A8);
        int shown = 0;
        for (String s : state.contacts()) {
            String[] p = s.split(";");
            double dx = Double.parseDouble(p[1]);
            double dz = Double.parseDouble(p[2]);
            int alt = Integer.parseInt(p[3]);
            int age = Integer.parseInt(p[4]);
            double dist = Math.sqrt(dx * dx + dz * dz);
            double k = r / (double) rangeBlocks;
            int x = cx + (int) (dx * k);
            int y = cy + (int) (dz * k);
            if (dist > rangeBlocks) {
                double s2 = rangeBlocks / dist;
                x = cx + (int) (dx * k * s2);
                y = cy + (int) (dz * k * s2);
            }
            int col = switch (p[0]) {
                case "ballistic", "missile", "rocket" -> 0xFFFF5050;
                case "shahed", "jet", "drone" -> 0xFFFFA040;
                default -> 0xFFE0D060;
            };
            int alpha = Mth.clamp(255 - age * 2, 70, 255);
            col = (alpha << 24) | (col & 0xFFFFFF);
            g.fill(x - 2, y - 2, x + 3, y + 3, col);
            if (age < 40) {
                g.outline(x - 4, y - 4, 9, 9, col);
            }
            if (shown < 10) {
                Component label = Component.translatable("radar.harddayz." + p[0]);
                g.text(font, label, x + 6, y - 4, col, false);
                g.text(font, Component.literal((int) dist + "м  h" + alt), x + 6, y + 5, 0xFF809080, false);
            }
            shown++;
        }
        if (state.contacts().isEmpty()) {
            Component none = Component.translatable("screen.harddayz.radar_clear");
            g.text(font, none, cx - font.width(none) / 2, cy + r - 24, 0xFF5AA080, false);
        }
        if (mc.player != null) {
            g.text(font, Component.literal("N"), cx - 2, cy - r - 6, 0xFF7CE0A8, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
