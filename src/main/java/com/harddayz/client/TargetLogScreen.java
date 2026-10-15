package com.harddayz.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.harddayz.item.TargetLogItem;
import com.harddayz.network.Net;
import com.harddayz.registry.ModItems;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Журнал целей: список точек, правка координат, удаление, очистка, копирование «x y z» в буфер
 * (в пульте ПУ — кнопка «Вставить»). Изменения сохраняются при закрытии.
 */
public class TargetLogScreen extends HdScreen {
    private static final int W = 300;
    private static final int ROWS = 8;
    private static final int ROW_H = 22;
    private static final int H = 44 + ROWS * ROW_H + 32;
    private static final Pattern NUM = Pattern.compile("-?\\d+");

    private final boolean offhand;
    private final List<int[]> pts = new ArrayList<>();
    private int scroll;
    private int copied = -1;
    private int copiedTicks;

    public TargetLogScreen(boolean offhand) {
        super(Component.translatable("item.harddayz.target_log"));
        this.offhand = offhand;
    }

    /** Три числа из строки: «123 64 -50», «x:123 y:64 z:-50», «123, 64, -50». */
    public static @Nullable BlockPos parse(String s) {
        Matcher m = NUM.matcher(s == null ? "" : s);
        int[] v = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!m.find()) {
                return null;
            }
            try {
                v[i] = Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new BlockPos(v[0], v[1], v[2]);
    }

    private ItemStack stack() {
        return minecraft.player.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        if (pts.isEmpty() && copied < 0 && scroll == 0 && !loaded) {
            for (BlockPos p : TargetLogItem.points(stack())) {
                pts.add(new int[] {p.getX(), p.getY(), p.getZ()});
            }
            loaded = true;
        }
        rebuild();
    }

    private boolean loaded;

    private void rebuild() {
        clearWidgets();
        scroll = Math.max(0, Math.min(scroll, pts.size() - ROWS));
        int l = left();
        int t = top();
        for (int r = 0; r < ROWS && scroll + r < pts.size(); r++) {
            int idx = scroll + r;
            int y = t + 30 + r * ROW_H;
            int[] p = pts.get(idx);
            for (int k = 0; k < 3; k++) {
                int kk = k;
                EditBox box = new EditBox(font, l + 34 + k * 62, y, 56, 18, Component.empty());
                box.setMaxLength(9);
                box.setValue(String.valueOf(p[k]));
                box.setResponder(v -> {
                    try {
                        p[kk] = Integer.parseInt(v.trim());
                    } catch (NumberFormatException ignored) {
                        // пока вводят «-» — оставляем старое значение
                    }
                });
                addRenderableWidget(box);
            }
            addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.copy"), b -> copy(idx))
                    .bounds(l + 222, y - 1, 48, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✕"), b -> {
                pts.remove(idx);
                rebuild();
            }).bounds(l + 274, y - 1, 20, 20).build());
        }
        int by = t + H - 28;
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scroll--;
            rebuild();
        }).bounds(l + 6, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scroll++;
            rebuild();
        }).bounds(l + 28, by, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.add_here"), b -> {
            if (pts.size() < TargetLogItem.MAX) {
                BlockPos h = minecraft.player.blockPosition();
                pts.add(new int[] {h.getX(), h.getY(), h.getZ()});
                scroll = pts.size();
                rebuild();
            }
        }).bounds(l + 52, by, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.paste"), b -> {
            BlockPos p = parse(minecraft.keyboardHandler.getClipboard());
            if (p != null && pts.size() < TargetLogItem.MAX) {
                pts.add(new int[] {p.getX(), p.getY(), p.getZ()});
                scroll = pts.size();
                rebuild();
            }
        }).bounds(l + 126, by, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.clear"), b -> {
            pts.clear();
            rebuild();
        }).bounds(l + 188, by, 54, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + 244, by, 50, 20).build());
    }

    private void copy(int idx) {
        int[] p = pts.get(idx);
        minecraft.keyboardHandler.setClipboard(p[0] + " " + p[1] + " " + p[2]);
        copied = idx;
        copiedTicks = 40;
    }

    @Override
    public void tick() {
        super.tick();
        if (copiedTicks > 0 && --copiedTicks == 0) {
            copied = -1;
        }
        if (!stack().is(ModItems.TARGET_LOG.get())) {
            onClose(); // журнал убрали из руки
        }
    }

    @Override
    public void removed() {
        if (stack().is(ModItems.TARGET_LOG.get())) {
            List<Integer> out = new ArrayList<>();
            for (int[] p : pts) {
                out.add(p[0]);
                out.add(p[1]);
                out.add(p[2]);
            }
            Net.toServer(new Net.LogEdit(offhand, out));
        }
        super.removed();
    }

    @Override
    protected void background(Gfx g, int mouseX, int mouseY, float a) {
        g.fill(left(), top(), left() + W, top() + H, 0xE8141810);
        g.outline(left(), top(), W, H, 0xFF7A8A50);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        int l = left();
        int t = top();
        g.text(font, title, l + 8, t + 8, 0xFFD8E0A0, true);
        g.text(font, Component.translatable("item.harddayz.target_log.count", pts.size(), TargetLogItem.MAX), l + 150, t + 8, 0xFFE0E0E0, false);
        g.text(font, "X", l + 58, t + 19, 0xFF90A070, false);
        g.text(font, "Y", l + 120, t + 19, 0xFF90A070, false);
        g.text(font, "Z", l + 182, t + 19, 0xFF90A070, false);
        if (pts.isEmpty()) {
            g.centeredText(font, Component.translatable("message.harddayz.log_empty"), l + W / 2, t + 60, 0xFF808080);
        }
        for (int r = 0; r < ROWS && scroll + r < pts.size(); r++) {
            int idx = scroll + r;
            int y = t + 30 + r * ROW_H;
            g.text(font, "#" + (idx + 1), l + 8, y + 5, idx == copied ? 0xFF80FF80 : 0xFFFFD27A, false);
            int[] p = pts.get(idx);
            int dist = (int) Math.sqrt(new BlockPos(p[0], p[1], p[2]).distToCenterSqr(minecraft.player.position()));
            if (mouseX >= l + 8 && mouseX < l + 30 && mouseY >= y && mouseY < y + 18) {
                g.text(font, dist + " м", l + 8, y - 6, 0xFFB0B0B0, false);
            }
        }
        if (copied >= 0) {
            g.text(font, Component.translatable("screen.harddayz.copied"), l + 8, t + H - 40, 0xFF80FF80, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
