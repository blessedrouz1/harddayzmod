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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Прицел миномёта: координаты цели (из целеуказателя, журнала, буфера), заряд, поправки по разрыву,
 * тип мины, серия и беглый огонь. Сервер считает углы и присылает состояние.
 */
public class MortarScreen extends HdScreen {
    private static final int W = 300;
    private static final int H = 232;
    private static int[] info = new int[22];
    private static int infoId = -1;
    private static final String[] SHELLS = {"he", "smoke", "illum", "incend", "gas"};

    private final Launcher mortar;
    private EditBox x;
    private EditBox y;
    private EditBox z;
    private EditBox fwd;
    private EditBox right;
    private Button chargeBtn;
    private Button shellBtn;
    private Button countBtn;
    private Button modeBtn;
    /** -1 — авто, 0..3 — заряд. */
    private int chargeWant = -1;
    private static int shell;
    private static int count = 1;
    private static boolean rapid;
    private final List<Component> labels = new ArrayList<>();
    private final List<BlockPos> sources = new ArrayList<>();
    private int index;

    public MortarScreen(Launcher mortar) {
        super(Component.translatable("screen.harddayz.mortar"));
        this.mortar = mortar;
    }

    public static void onInfo(Net.MortarInfo p) {
        infoId = p.entity();
        info = p.v().length >= 22 ? p.v() : java.util.Arrays.copyOf(p.v(), 22);
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof MortarScreen s && s.mortar.getId() == p.entity()) {
            s.refresh();
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
        collectSources();
        int l = left();
        int t = top();
        BlockPos start = has() && info[3] == 1 ? new BlockPos(info[4], info[5], info[6])
                : sources.isEmpty() ? minecraft.player.blockPosition() : sources.get(0);
        x = box(l + 22, t + 74, 60, start.getX());
        y = box(l + 112, t + 74, 60, start.getY());
        z = box(l + 202, t + 74, 60, start.getZ());
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> pick(index - 1)).bounds(l + 10, t + 98, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> pick(index + 1)).bounds(l + 120, t + 98, 20, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.here"), b -> set(minecraft.player.blockPosition()))
                .bounds(l + 146, t + 98, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.paste"), b -> paste()).bounds(l + 220, t + 98, 70, 18).build());

        chargeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            chargeWant = chargeWant >= 3 ? -1 : chargeWant + 1;
            refresh();
        }).bounds(l + 10, t + 122, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.mortar_aim"), b -> send(0, num(x), num(y), num(z), chargeWant, 0))
                .bounds(l + 104, t + 122, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.mortar_look"), b -> send(4, 0, 0, 0, 0, 0))
                .bounds(l + 198, t + 122, 92, 20).build());

        fwd = box(l + 58, t + 150, 40, 0);
        right = box(l + 150, t + 150, 40, 0);
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.mortar_fix"), b -> {
            send(1, num(fwd), 0, num(right), 0, 0);
            fwd.setValue("0");
            right.setValue("0");
        }).bounds(l + 198, t + 149, 92, 20).build());

        shellBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            shell = (shell + 1) % SHELLS.length;
            refresh();
        }).bounds(l + 10, t + 176, 120, 20).build());
        countBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            count = count >= 8 ? 1 : count + 1;
            refresh();
        }).bounds(l + 134, t + 176, 70, 20).build());
        modeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            rapid = !rapid;
            refresh();
        }).bounds(l + 208, t + 176, 82, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.harddayz.fire"), b -> send(2, 0, rapid ? 1 : 0, 0, shell, count))
                .bounds(l + 10, t + 202, 190, 22).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(l + 204, t + 202, 86, 22).build());
        refresh();
    }

    private boolean has() {
        return infoId == mortar.getId();
    }

    private void refresh() {
        if (chargeBtn == null) {
            return;
        }
        chargeBtn.setMessage(chargeWant < 0 ? Component.translatable("screen.harddayz.mortar_charge_auto")
                : Component.translatable("screen.harddayz.mortar_charge", chargeWant + 1));
        int have = has() ? info[14 + shell] : 0;
        shellBtn.setMessage(Component.translatable("screen.harddayz.mortar_shell_" + SHELLS[shell]).append(" (" + (have >= 999 ? "∞" : have) + ")"));
        countBtn.setMessage(Component.translatable("screen.harddayz.mortar_count", count));
        modeBtn.setMessage(Component.translatable(rapid ? "screen.harddayz.mortar_rapid" : "screen.harddayz.mortar_single"));
    }

    private void send(int action, int px, int py, int pz, int a, int b) {
        Net.toServer(new Net.MortarOrder(mortar.getId(), action, px, py, pz, a, b));
    }

    private void collectSources() {
        labels.clear();
        sources.clear();
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(minecraft.player.getMainHandItem());
        stacks.add(minecraft.player.getOffhandItem());
        stacks.addAll(minecraft.player.getInventory().items);
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
    }

    private void pick(int i) {
        if (sources.isEmpty()) {
            return;
        }
        index = Math.floorMod(i, sources.size());
        set(sources.get(index));
    }

    private EditBox box(int px, int py, int w, int value) {
        EditBox b = new EditBox(font, px, py, w, 18, Component.empty());
        b.setValue(String.valueOf(value));
        addRenderableWidget(b);
        return b;
    }

    private void set(BlockPos p) {
        x.setValue(String.valueOf(p.getX()));
        y.setValue(String.valueOf(p.getY()));
        z.setValue(String.valueOf(p.getZ()));
    }

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

    @Override
    public boolean isPauseScreen() {
        return false;
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
        int c = 0xFFE0E0E0;
        g.text(font, title, l + 10, t + 8, 0xFFD8E0A0, true);
        if (has()) {
            int[] v = info;
            g.text(font, Component.translatable("screen.harddayz.mortar_set", v[0] / 10f, v[1] / 10f, v[2] + 1, v[20], v[21]), l + 10, t + 22, c, false);
            if (v[3] == 1) {
                g.text(font, Component.translatable("screen.harddayz.mortar_target", v[7], String.format(java.util.Locale.ROOT, "%.1f", v[13] / 20f)),
                        l + 10, t + 34, c, false);
            }
            g.text(font, Component.translatable("screen.harddayz.mortar_wind", v[11], v[12]), l + 10, t + 46, 0xFFB0D0FF, false);
            if (v[8] == 1) {
                g.text(font, Component.translatable("screen.harddayz.mortar_last",
                        Component.translatable(v[9] >= 0 ? "message.harddayz.mortar_over" : "message.harddayz.mortar_short"), Math.abs(v[9]),
                        Component.translatable(v[10] >= 0 ? "message.harddayz.mortar_right" : "message.harddayz.mortar_left"), Math.abs(v[10])),
                        l + 10, t + 58, 0xFFFFC060, false);
            }
            if (v[19] > 0) {
                g.text(font, Component.translatable("screen.harddayz.mortar_queue", v[19]), l + 200, t + 8, 0xFFFF8060, false);
            }
        }
        g.text(font, "X", l + 12, t + 79, c, false);
        g.text(font, "Y", l + 102, t + 79, c, false);
        g.text(font, "Z", l + 192, t + 79, c, false);
        Component src = sources.isEmpty() ? Component.translatable("screen.harddayz.src_none") : labels.get(index);
        g.centeredText(font, src, l + 75, t + 103, 0xFFB0B0B0);
        g.text(font, Component.translatable("screen.harddayz.mortar_fwd"), l + 10, t + 155, c, false);
        g.text(font, Component.translatable("screen.harddayz.mortar_right_lbl"), l + 102, t + 155, c, false);
    }
}
