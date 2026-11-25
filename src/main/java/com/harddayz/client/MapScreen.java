package com.harddayz.client;

import com.harddayz.world.FrontMap;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/** Большая карта (M): тащить, масштаб, координаты в буфер для пультов и прицела миномёта, своя метка. */
public class MapScreen extends HdScreen {
    private static double scale = 4;
    private double cx;
    private double cz;
    private Component note = Component.empty();

    public MapScreen() {
        super(Component.translatable("screen.harddayz.map"));
        var p = net.minecraft.client.Minecraft.getInstance().player;
        cx = p == null ? 0 : p.getX();
        cz = p == null ? 0 : p.getZ();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void background(Gfx g, int mouseX, int mouseY, float a) {
        MiniMap.draw(g, 0, 0, width, height, cx, cz, scale, true);
    }

    @Override
    protected void content(Gfx g, int mouseX, int mouseY, float a) {
        g.fill(0, height - 24, width, height, 0xC0101410);
        g.text(font, Component.translatable("screen.harddayz.map_hint"), 6, height - 20, 0xFFB8C0A8, false);
        g.text(font, note, 6, height - 10, 0xFFFFE080, false);
        String at = (int) wx(mouseX) + " " + (int) wz(mouseY);
        g.text(font, at, width - font.width(at) - 6, height - 16, 0xFFFFFFFF, false);
    }

    private double wx(double mx) {
        return cx + (mx - width / 2.0) * scale;
    }

    private double wz(double my) {
        return cz + (my - height / 2.0) * scale;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0) {
            cx -= dx * scale;
            cz -= dy * scale;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        double x = wx(mx), z = wz(my);
        scale = Mth.clamp(scale * (d > 0 ? 0.8 : 1.25), 0.5, MiniMap.front != null ? 12 : 3);
        cx = x - (mx - width / 2.0) * scale;
        cz = z - (my - height / 2.0) * scale;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 1) {
            return super.mouseClicked(mx, my, button);
        }
        int x = Mth.floor(wx(mx)), z = Mth.floor(wz(my));
        if (Screen.hasShiftDown()) {
            MiniMap.mark = MiniMap.mark != null && Math.hypot(MiniMap.mark[0] - x, MiniMap.mark[1] - z) < 6 * scale ? null : new int[] {x, z};
            return true;
        }
        int y = minecraft.player.getBlockY();
        if (ClientBlasts.loaded(minecraft.level, x, z)) {
            y = minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        } else if (MiniMap.front != null) {
            FrontMap m = MiniMap.front;
            y = (int) Math.round(m.height(x, z, m.road(x, z))) + 1;
        }
        minecraft.keyboardHandler.setClipboard(x + " " + y + " " + z);
        note = Component.translatable("screen.harddayz.map_copied", x, y, z);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientSetup.MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
