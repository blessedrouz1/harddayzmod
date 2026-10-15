package com.harddayz.client;

import java.util.List;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Экран конца матча: победитель и таблица статистики по фракциям. */
public class MatchEndScreen extends HdScreen {
    private static final int W = 400;
    private final String winner;
    private final List<String[]> rows;

    public MatchEndScreen(String winner, List<String> rows) {
        super(Component.translatable("screen.harddayz.match_end"));
        this.winner = winner;
        this.rows = rows.stream().map(r -> r.split(";", -1)).toList();
    }

    private int h() {
        return 70 + rows.size() * 16 + 30;
    }

    @Override
    protected void init() {
        int t = (height - h()) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 50, t + h() - 26, 100, 20).build());
    }

    private static int color(String id) {
        return switch (id) {
            case "blue" -> 0xFF6080FF;
            case "red" -> 0xFFFF5050;
            case "green" -> 0xFF50E050;
            default -> 0xFFFFE050;
        };
    }

    @Override
    protected void background(Gfx g, int mouseX, int mouseY, float a) {
        int l = (width - W) / 2;
        int t = (height - h()) / 2;
        g.fill(l, t, l + W, t + h(), 0xF0101410);
        g.outline(l, t, W, h(), 0xFFC8A040);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        int l = (width - W) / 2;
        int t = (height - h()) / 2;
        g.centeredText(font, title, width / 2, t + 8, 0xFFFFD27A);
        String[] win = rows.stream().filter(r -> r[0].equals(winner)).findFirst().orElse(null);
        Component wl = win != null ? Component.translatable("screen.harddayz.winner", win[1]) : Component.translatable("screen.harddayz.no_winner");
        g.centeredText(font, wl, width / 2, t + 24, win != null ? color(win[0]) : 0xFFB0B0B0);
        String[] head = Component.translatable("screen.harddayz.stats_head").getString().split("\\|");
        int[] cols = {8, 88, 128, 162, 196, 236, 290, 334, 374};
        for (int i = 0; i < head.length && i < cols.length; i++) {
            String s = head[i];
            // длинные заголовки ужимаем, чтобы влезли в колонку
            int max = (i + 1 < cols.length ? cols[i + 1] : W) - cols[i] - 2;
            while (font.width(s) > max && s.length() > 2) {
                s = s.substring(0, s.length() - 1);
            }
            g.text(font, s, l + cols[i], t + 46, 0xFF90A070, false);
        }
        int y = t + 62;
        for (String[] r : rows) {
            int c = color(r[0]);
            if (r[0].equals(winner)) {
                g.fill(l + 4, y - 3, l + W - 4, y + 12, 0x40C8A040);
            }
            // id;имя;очки;убийства;смерти;уничтожил;потерял;потратил;рубежи;игроков
            String[] v = {r[1], r[9], r[2], r[3], r[4], r[5], r[6], r[7], r[8]};
            for (int i = 0; i < v.length; i++) {
                g.text(font, v[i], l + cols[i], y, i == 0 ? c : 0xFFE0E0E0, false);
            }
            y += 16;
        }
    }
}
