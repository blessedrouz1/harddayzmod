package com.harddayz.client;

import java.util.ArrayList;
import java.util.List;

import com.harddayz.entity.Launcher;
import com.harddayz.item.DesignatorItem;
import com.harddayz.item.TargetLogItem;
import com.harddayz.network.Net;
import com.harddayz.registry.ModItems;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Пульт пусковой. Координаты подставляются из целеуказателя и журнала целей (листаются стрелками),
 * но пуск — только кнопкой «ПУСК».
 */
public class LauncherScreen extends HdScreen {
    private static final int W = 270;
    private static final int H = 196;
    private final Launcher launcher;
    private EditBox x;
    private EditBox y;
    private EditBox z;
    /** Источники координат: подпись + точка. */
    private final List<Component> labels = new ArrayList<>();
    private final List<BlockPos> sources = new ArrayList<>();
    private int index;

    public LauncherScreen(Launcher launcher) {
        super(Component.translatable("screen.harddayz." + launcher.kind().name().toLowerCase()));
        this.launcher = launcher;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private boolean aimable() {
        Launcher.Kind k = launcher.kind();
        return (!k.samLike() || k == Launcher.Kind.S400) && k != Launcher.Kind.EW && k != Launcher.Kind.RADAR && k != Launcher.Kind.RELAY && !k.gunner() && !k.heli();
    }

    @Override
    protected void init() {
        collectSources();
        int l = left();
        int t = top();
        if (aimable()) {
            slot = launcher.selectedSlot();
            ItemStack hand0 = minecraft.player.getMainHandItem();
            boolean fromItem = hand0.is(ModItems.DESIGNATOR.get()) || hand0.is(ModItems.TARGET_LOG.get());
            BlockPos saved = launcher.slot(slot);
            BlockPos start = fromItem && !sources.isEmpty() ? sources.get(index) : saved != null ? saved
                    : sources.isEmpty() ? minecraft.player.blockPosition() : sources.get(index);
            for (int i = 0; i < Launcher.SLOT_COUNT; i++) {
                int s = i;
                slotButtons[i] = addRenderableWidget(Button.builder(Component.literal(String.valueOf(i + 1)), b -> switchSlot(s))
                        .bounds(l + 150 + i * 22, t + 42, 20, 16).build());
            }
            x = field(l + 30, t + 62, start.getX());
            y = field(l + 112, t + 62, start.getY());
            z = field(l + 194, t + 62, start.getZ());
            addRenderableWidget(Button.builder(Component.literal("◀"), b -> pick(index - 1)).bounds(l + 10, t + 88, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> pick(index + 1)).bounds(l + 236, t + 88, 24, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.here"), b -> set(minecraft.player.blockPosition()))
                    .bounds(l + 10, t + 114, 122, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.paste"), b -> paste())
                    .bounds(l + 138, t + 114, 122, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.fire"), b -> fire())
                    .bounds(l + 10, t + 140, 250, 24).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + 85, t + H - 26, 100, 20).build());
    }

    /** Целеуказатель + все точки журнала (сначала из руки, потом из инвентаря). */
    private void collectSources() {
        labels.clear();
        sources.clear();
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(minecraft.player.getMainHandItem());
        stacks.add(minecraft.player.getOffhandItem());
        for (ItemStack s : minecraft.player.getInventory().items) {
            stacks.add(s);
        }
        BlockPos stored = null;
        boolean designator = false;
        boolean log = false;
        for (ItemStack s : stacks) {
            if (!designator && s.is(ModItems.DESIGNATOR.get()) && DesignatorItem.target(s) != null) {
                designator = true;
                labels.add(Component.translatable("screen.harddayz.src_designator"));
                sources.add(DesignatorItem.target(s));
            }
            if (!log && s.is(ModItems.TARGET_LOG.get()) && !TargetLogItem.points(s).isEmpty()) {
                log = true;
                List<BlockPos> pts = TargetLogItem.points(s);
                for (int i = 0; i < pts.size(); i++) {
                    labels.add(Component.translatable("screen.harddayz.src_point", i + 1, pts.size()));
                    sources.add(pts.get(i));
                }
            }
        }
        // открыт целеуказателем/журналом — начинаем с его точки, иначе с сохранённой цели
        ItemStack hand = minecraft.player.getMainHandItem();
        if (hand.is(ModItems.DESIGNATOR.get()) || hand.is(ModItems.TARGET_LOG.get())) {
            int skip = (stored != null ? 1 : 0) + (hand.is(ModItems.TARGET_LOG.get()) && designator ? 1 : 0);
            index = Math.min(skip, Math.max(0, sources.size() - 1));
        }
    }

    private int slot;
    private final Button[] slotButtons = new Button[Launcher.SLOT_COUNT];

    /** Сохранить текущие координаты в слот и перейти к другому. */
    private void switchSlot(int s) {
        Net.toServer(new Net.FireOrder(launcher.getId(), num(x), num(y), num(z), false, slot));
        slot = s;
        BlockPos p = launcher.slot(s);
        if (p != null) {
            set(p);
        }
    }

    private void pick(int i) {
        if (sources.isEmpty()) {
            return;
        }
        index = Math.floorMod(i, sources.size());
        set(sources.get(index));
    }

    private EditBox field(int px, int py, int value) {
        EditBox box = new EditBox(font, px, py, 60, 18, Component.empty());
        box.setValue(String.valueOf(value));
        addRenderableWidget(box);
        return box;
    }

    private void set(BlockPos p) {
        x.setValue(String.valueOf(p.getX()));
        y.setValue(String.valueOf(p.getY()));
        z.setValue(String.valueOf(p.getZ()));
    }

    /** Координаты из буфера обмена (скопированные в журнале целей или откуда угодно: «x y z»). */
    private void paste() {
        BlockPos p = TargetLogScreen.parse(minecraft.keyboardHandler.getClipboard());
        if (p != null) {
            set(p);
        }
    }

    private static int num(EditBox b) {
        try {
            return Integer.parseInt(b.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void fire() {
        Net.toServer(new Net.FireOrder(launcher.getId(), num(x), num(y), num(z), true, slot));
        fired = true;
        onClose();
    }

    private boolean fired;

    @Override
    public void removed() {
        // «Готово», Esc — координаты остаются в машине
        if (!fired && x != null) {
            Net.toServer(new Net.FireOrder(launcher.getId(), num(x), num(y), num(z), false, slot));
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
        g.text(font, title, l + 10, t + 8, 0xFFD8E0A0, true);
        g.text(font, Component.translatable("screen.harddayz.ammo", launcher.ammo(), launcher.kind().capacity), l + 10, t + 22, 0xFFE0E0E0, false);
        g.text(font, Component.translatable("screen.harddayz.health", (int) launcher.health(), (int) launcher.kind().health), l + 150, t + 22, 0xFFE0E0E0, false);
        g.text(font, Component.translatable("screen.harddayz.drive_hint"), l + 10, t + 34, 0xFF90A070, false);
        if (!aimable()) {
            g.text(font, Component.translatable(launcher.kind() == Launcher.Kind.EW ? "message.harddayz.ew_auto"
                    : launcher.kind() == Launcher.Kind.RADAR ? "screen.harddayz.radar_info" : "screen.harddayz.sam_info"),
                    l + 10, t + 50, 0xFFB0C080, false);
            return;
        }
        g.text(font, Component.translatable("screen.harddayz.target"), l + 10, t + 46, 0xFFB0C080, false);
        for (int i = 0; i < Launcher.SLOT_COUNT; i++) {
            if (slotButtons[i] != null) {
                int bx = slotButtons[i].getX();
                int by = slotButtons[i].getY();
                if (i == slot) {
                    g.outline(bx - 1, by - 1, 22, 18, 0xFFFFD27A);
                } else if (launcher.slot(i) != null) {
                    g.fill(bx + 8, by + 17, bx + 12, by + 18, 0xFF90C890);
                }
            }
        }
        g.text(font, "X", l + 18, t + 67, 0xFFE0E0E0, false);
        g.text(font, "Y", l + 100, t + 67, 0xFFE0E0E0, false);
        g.text(font, "Z", l + 182, t + 67, 0xFFE0E0E0, false);
        Component src = sources.isEmpty() ? Component.translatable("screen.harddayz.src_none") : labels.get(index);
        g.centeredText(font, src, l + W / 2, t + 94, sources.isEmpty() ? 0xFF808080 : 0xFFFFD27A);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
