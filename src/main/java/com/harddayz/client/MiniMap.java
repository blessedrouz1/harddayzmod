package com.harddayz.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.harddayz.HardDayz;
import com.harddayz.network.Net;
import com.harddayz.world.FrontBuildings;
import com.harddayz.world.FrontGenerator;
import com.harddayz.world.FrontMap;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Миникарта. На фронтовой карте весь план рисуется по сиду (в фоне, один раз), в обычном мире — местность вокруг
 * игрока по загруженным чанкам. Поверх — метки, которые присылает сервер.
 */
final class MiniMap {
    /** Блоков на пиксель плана фронтовой карты. */
    private static final int STEP = 3;
    private static final int PLAN = FrontMap.HALF * 2 / STEP;
    private static final int LIVE = 256;
    private static final ResourceLocation TEX = new ResourceLocation(HardDayz.MODID, "dynamic/minimap");
    private static final int[] SIDE = {0xFF6080FF, 0xFFFF5050, 0xFF50E050, 0xFFFFE050};

    private static List<String> rows = List.of();
    static @Nullable FrontMap front;
    private static @Nullable DynamicTexture texture;
    private static boolean plan;
    private static int liveX;
    private static int liveZ;
    private static int ticks;
    private static @Nullable ClientLevel world;
    /** Своя метка: x, z. */
    static @Nullable int[] mark;

    private MiniMap() {
    }

    static void state(List<String> r) {
        rows = r;
    }

    static void info(Net.FrontInfo p) {
        clear();
        if (!p.front()) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            FrontMap m = new FrontMap(p.seed());
            FrontBuildings b = new FrontBuildings(m);
            return new Object[] {m, paint(m, b), regions(m)};
        }, net.minecraft.Util.backgroundExecutor()).thenAcceptAsync(o -> {
            front = (FrontMap) o[0];
            regions = (byte[]) o[2];
            upload((NativeImage) o[1]);
            plan = true;
        }, Minecraft.getInstance());
    }

    private static void clear() {
        front = null;
        plan = false;
        rows = List.of();
        mark = null;
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEX);
            texture = null;
        }
    }

    private static void upload(NativeImage img) {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEX);
        }
        texture = new DynamicTexture(img);
        Minecraft.getInstance().getTextureManager().register(TEX, texture);
    }

    // ------------------------------------------------------------------ план фронтовой карты

    private static int abgr(int rgb, double k) {
        int r = (int) Mth.clamp((rgb >> 16 & 255) * k, 0, 255), g = (int) Mth.clamp((rgb >> 8 & 255) * k, 0, 255), b = (int) Mth.clamp((rgb & 255) * k, 0, 255);
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    /** Территории крупной сеткой: индекс места в front.sites. */
    private static final int CELL = 50;
    private static final int GRID = FrontMap.HALF * 2 / CELL;
    private static byte[] regions = new byte[0];

    private static byte[] regions(FrontMap m) {
        byte[] out = new byte[GRID * GRID];
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                out[i * GRID + j] = (byte) m.sites.indexOf(m.region(-FrontMap.HALF + (i + 0.5) * CELL, -FrontMap.HALF + (j + 0.5) * CELL));
            }
        }
        return out;
    }

    /** Чья территория: номер цвета или -1. */
    private static int owner(FrontMap.Site s) {
        boolean match = false;
        for (String r : rows) {
            String[] p = r.split(";", -1);
            if (p[0].equals("point") && p.length >= 5) {
                match = true;
                if (p[4].equals(s.name())) {
                    return (int) num(p[3]);
                }
            }
        }
        return match && s == front.west ? 0 : match && s == front.east ? 1 : -1;
    }

    private static NativeImage paint(FrontMap m, FrontBuildings b) {
        NativeImage img = new NativeImage(PLAN, PLAN, false);
        for (int i = 0; i < PLAN; i++) {
            for (int j = 0; j < PLAN; j++) {
                double x = -FrontMap.HALF + (i + 0.5) * STEP, z = -FrontMap.HALF + (j + 0.5) * STEP;
                img.setPixelRGBA(i, j, color(m, x, z));
            }
        }
        for (int[] f : b.footprints()) {
            for (int i = (f[0] + 2 + FrontMap.HALF) / STEP; i <= (f[2] - 2 + FrontMap.HALF) / STEP; i++) {
                for (int j = (f[1] + 2 + FrontMap.HALF) / STEP; j <= (f[3] - 2 + FrontMap.HALF) / STEP; j++) {
                    boolean big = f[2] - f[0] > 80;
                    if (!big && i >= 0 && j >= 0 && i < PLAN && j < PLAN) {
                        img.setPixelRGBA(i, j, abgr(0x8C8478, 1));
                    }
                }
            }
        }
        return img;
    }

    private static int color(FrontMap m, double x, double z) {
        double river = m.river(x, z);
        boolean city = m.inCity(x, z);
        FrontMap.RoadHit road = m.road(x, z);
        if (city) {
            FrontMap.StreetHit st = m.street(x, z);
            if (river < FrontMap.RIVER_HALF) {
                return st.h <= 5 && m.bridge(st.hi) ? abgr(0x605E5A, 1) : abgr(0x3D6FA8, 1);
            }
            FrontMap.District d = m.district(x, z);
            if (d != m.district(x + STEP, z) || d != m.district(x, z + STEP)) {
                return abgr(0x2A2622, 1); // граница районов
            }
            return Math.min(st.v, st.h) <= 5 ? abgr(0x5E5C58, 1) : abgr(d.zone().color, 1);
        }
        boolean border = m.region(x, z) != m.region(x + STEP, z) || m.region(x, z) != m.region(x, z + STEP);
        if (border && Math.floorMod((int) (x + z) / STEP, 5) < 3) {
            return abgr(0x1E1C18, 1); // граница территорий — пунктир
        }
        if (road.d < 4.5) {
            return abgr(0x55524E, 1);
        }
        if (m.rail(x, z).d < 2.2) {
            return abgr(0x3A2E28, 1);
        }
        if (river < FrontMap.RIVER_HALF + 1) {
            return abgr(0x3D6FA8, 1);
        }
        double slope = m.ground(x, z) - m.ground(x - 6, z - 6);
        double k = Mth.clamp(1 + slope * 0.06, 0.75, 1.25);
        FrontMap.Site s = m.siteAt(x, z, 0);
        if (s != null) {
            return abgr(s.type() == FrontMap.Type.VILLAGE ? 0x8FA35E : 0x9A9890, 1);
        }
        if (m.trench(x, z) < 2) {
            return abgr(0x4A3A28, 1);
        }
        FrontMap.Field f = m.field(x, z);
        if (FrontGenerator.hedge(m, f) && f.edge < 4) {
            return abgr(0x3F6B2C, k);
        }
        return abgr(switch (FrontGenerator.field(m, f, (int) x, (int) z)) {
            case 0 -> 0xC2B04C;
            case 1 -> 0x86643F;
            case 2 -> 0xB9A63A;
            case 3 -> 0x86A24A;
            case 5 -> 0x39662B;
            default -> 0x7A9E48;
        }, k);
    }

    // ------------------------------------------------------------------ обычный мир

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != world) {
            if (mc.level == null) {
                clear();
            }
            world = mc.level;
        }
        if (mc.level == null || mc.player == null || front != null || ++ticks % 20 != 0 || !com.harddayz.ClientConfig.minimap() && !(mc.screen instanceof MapScreen)) {
            return;
        }
        if (mc.level.dimensionType().hasCeiling()) {
            return;
        }
        NativeImage img = new NativeImage(LIVE, LIVE, false);
        liveX = mc.player.getBlockX() - LIVE / 2;
        liveZ = mc.player.getBlockZ() - LIVE / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < LIVE; i++) {
            int prev = Integer.MIN_VALUE;
            for (int j = 0; j < LIVE; j++) {
                int x = liveX + i, z = liveZ + j;
                if (!ClientBlasts.loaded(mc.level, x, z)) {
                    img.setPixelRGBA(i, j, 0xFF181818);
                    continue;
                }
                int y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int c = mc.level.getBlockState(p.set(x, y, z)).getMapColor(mc.level, p).col;
                img.setPixelRGBA(i, j, abgr(c, prev == Integer.MIN_VALUE ? 0.9 : Mth.clamp(0.9 + (y - prev) * 0.08, 0.6, 1.15)));
                prev = y;
            }
        }
        upload(img);
        plan = false;
    }

    // ------------------------------------------------------------------ рисование

    /** Кусок карты: экранный квадрат x, y, w, h показывает местность вокруг (cx, cz), k блоков на пиксель. */
    static void draw(Gfx g, int x, int y, int w, int h, double cx, double cz, double k, boolean labels) {
        g.fill(x, y, x + w, y + h, 0xFF14181A);
        g.g.enableScissor(x, y, x + w, y + h);
        if (texture != null) {
            double step = plan ? STEP : 1, size = plan ? PLAN : LIVE;
            double x0 = plan ? -FrontMap.HALF : liveX, z0 = plan ? -FrontMap.HALF : liveZ;
            double sx = x + w / 2.0 + (x0 - cx) / k, sy = y + h / 2.0 + (z0 - cz) / k;
            var pose = g.g.pose();
            pose.pushPose();
            pose.translate(sx, sy, 0);
            float s = (float) (step / k);
            pose.scale(s, s, 1);
            g.g.blit(TEX, 0, 0, 0, 0, (int) size, (int) size, (int) size, (int) size);
            pose.popPose();
        }
        var font = Minecraft.getInstance().font;
        List<Runnable> over = new ArrayList<>();
        if (front != null) { // закраска территорий цветом владельца
            int[] own = new int[front.sites.size()];
            for (int i = 0; i < own.length; i++) {
                own[i] = owner(front.sites.get(i));
            }
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    int o = own[regions[i * GRID + j]];
                    if (o < 0) {
                        continue;
                    }
                    double wx = -FrontMap.HALF + i * CELL, wz = -FrontMap.HALF + j * CELL;
                    int ax = (int) Math.floor(x + w / 2.0 + (wx - cx) / k), ay = (int) Math.floor(y + h / 2.0 + (wz - cz) / k);
                    int bx = (int) Math.floor(x + w / 2.0 + (wx + CELL - cx) / k), by = (int) Math.floor(y + h / 2.0 + (wz + CELL - cz) / k);
                    if (bx > x && by > y && ax < x + w && ay < y + h) {
                        g.fill(ax, ay, bx, by, 0x38000000 | SIDE[o & 3] & 0xFFFFFF);
                    }
                }
            }
        }
        if (front != null && labels) {
            if (k < 5) { // районы города
                for (FrontMap.District d : front.districts) {
                    int px = (int) (x + w / 2.0 + (d.x() - cx) / k), py = (int) (y + h / 2.0 + (d.z() - cz) / k);
                    over.add(() -> g.centeredText(font, d.zone().title, px, py, 0xFFE8E0C0));
                }
            }
            for (FrontMap.Site s : front.sites) {
                int px = (int) (x + w / 2.0 + (s.x() - cx) / k), py = (int) (y + h / 2.0 + (s.z() - cz) / k);
                over.add(() -> g.centeredText(font, s.name(), px, py - 14, 0xFFF0EAD0));
            }
        }
        for (String r : rows) {
            String[] p = r.split(";", -1);
            if (p.length < 5) {
                continue;
            }
            double wx = num(p[1]), wz = num(p[2]);
            int v = (int) num(p[3]);
            int px = (int) (x + w / 2.0 + (wx - cx) / k), py = (int) (y + h / 2.0 + (wz - cz) / k);
            boolean edge = px < x + 3 || py < y + 3 || px > x + w - 4 || py > y + h - 4;
            if (edge && !(p[0].equals("hq") || p[0].equals("point")) && !p[0].equals("launch") && !p[0].equals("city")) {
                continue;
            }
            int ex = Mth.clamp(px, x + 4, x + w - 5), ey = Mth.clamp(py, y + 4, y + h - 5);
            switch (p[0]) {
                case "point" -> {
                    int c = v < 0 ? 0xFFD8D8D8 : SIDE[v & 3];
                    g.fill(ex - 3, ey - 3, ex + 4, ey + 4, 0xFF000000);
                    g.fill(ex - 2, ey - 2, ex + 3, ey + 3, c);
                }
                case "hq" -> {
                    g.fill(ex - 4, ey - 4, ex + 5, ey + 5, 0xFF000000);
                    g.fill(ex - 3, ey - 3, ex + 4, ey + 4, SIDE[v & 3]);
                    g.fill(ex - 1, ey - 2, ex + 2, ey + 3, 0xFFFFFFFF);
                }
                case "store" -> g.fill(px - 2, py - 2, px + 3, py + 2, 0xFFC09050);
                case "object" -> g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFB040);
                case "city" -> ring(g, px, py, (int) (v / k), 0xA0FFC040);
                case "ally" -> {
                    arrow(g, px, py, v, 0xFF60F080);
                    if (labels) {
                        over.add(() -> g.centeredText(font, p[4], px, py + 5, 0xFF80FFA0));
                    }
                }
                case "squad" -> {
                    g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
                    g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF60F080);
                }
                case "enemy" -> {
                    g.fill(px - 2, py - 2, px + 3, py + 3, 0xFF000000);
                    g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFF4040);
                }
                case "vehicle" -> g.fill(px - 2, py - 1, px + 3, py + 2, 0xFF80C0FF);
                case "contact" -> g.fill(px - 1, py - 1, px + 2, py + 2, ((int) Mth.clamp(255 - v * 2, 80, 255) << 24) | 0xFF6030);
                case "launch" -> ring(g, px, py, Math.max(2, (int) (v / k)), 0xC0FF5050);
                default -> {
                }
            }
        }
        if (mark != null) {
            int px = Mth.clamp((int) (x + w / 2.0 + (mark[0] - cx) / k), x + 3, x + w - 4), py = Mth.clamp((int) (y + h / 2.0 + (mark[1] - cz) / k), y + 3, y + h - 4);
            g.fill(px - 3, py, px + 4, py + 1, 0xFFFF40FF);
            g.fill(px, py - 3, px + 1, py + 4, 0xFFFF40FF);
        }
        var me = Minecraft.getInstance().player;
        if (me != null) {
            int px = (int) (x + w / 2.0 + (me.getX() - cx) / k), py = (int) (y + h / 2.0 + (me.getZ() - cz) / k);
            arrow(g, px, py, (int) me.getYRot(), 0xFFFFFFFF);
        }
        var cam = Minecraft.getInstance().getCameraEntity();
        if (cam != null && cam != me) {
            int px = (int) (x + w / 2.0 + (cam.getX() - cx) / k), py = (int) (y + h / 2.0 + (cam.getZ() - cz) / k);
            arrow(g, px, py, (int) cam.getYRot(), 0xFF40E0FF);
        }
        over.forEach(Runnable::run);
        g.g.disableScissor();
    }

    private static double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void ring(Gfx g, int cx, int cy, int r, int col) {
        int n = Mth.clamp(r * 4, 12, 160);
        for (int i = 0; i < n; i++) {
            double t = i * Math.PI * 2 / n;
            int px = cx + (int) Math.round(Math.cos(t) * r), py = cy + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
    }

    private static void arrow(Gfx g, int x, int y, int yaw, int col) {
        double a = Math.toRadians(yaw);
        double fx = -Math.sin(a), fz = Math.cos(a);
        for (int i = -3; i <= 4; i++) {
            int half = i < 0 ? 2 : Math.max(0, 2 - i / 2);
            for (int s = -half; s <= half; s++) {
                int px = (int) Math.round(x + fx * i - fz * s * 0.7), py = (int) Math.round(y + fz * i + fx * s * 0.7);
                g.fill(px, py, px + 1, py + 1, i == 4 || Math.abs(s) == half ? 0xFF000000 | (col & 0x7F7F7F) : col);
            }
        }
    }

    static void render(Gfx g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || texture == null || !com.harddayz.ClientConfig.minimap() || mc.options.hideGui || mc.options.renderDebug || DronePilot.active()
                || mc.screen instanceof MapScreen) {
            return;
        }
        int size = Math.min(104, g.guiHeight() / 3);
        int x = 6, y = 6;
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xC0101410);
        draw(g, x, y, size, size, mc.player.getX(), mc.player.getZ(), front != null ? 3 : 2, false);
        g.outline(x - 1, y - 1, size + 2, size + 2, 0xFF5A6A40);
        g.centeredText(mc.font, "N", x + size / 2, y + 1, 0xFFE0E0C0);
        g.text(mc.font, mc.player.getBlockX() + " " + mc.player.getBlockZ(), x, y + size + 4, 0xFFC8D0B8, true);
        FrontMap.Site s = front == null ? null : front.siteAt(mc.player.getX(), mc.player.getZ(), 10);
        if (s != null) {
            String name = s.name();
            if (s.type() == FrontMap.Type.CITY && front.inCity(mc.player.getX(), mc.player.getZ())) {
                name += " · " + front.district(mc.player.getX(), mc.player.getZ()).zone().title;
            }
            g.text(mc.font, name, x, y + size + 14, 0xFFE0C060, true);
        }
        if (front != null) {
            FrontMap.Site reg = front.region(mc.player.getX(), mc.player.getZ());
            int o = owner(reg);
            g.text(mc.font, "▪ " + reg.name(), x, y + size + (s != null ? 24 : 14), o < 0 ? 0xFFA8A898 : SIDE[o & 3], true);
        }
    }
}
