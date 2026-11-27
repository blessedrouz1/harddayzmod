package com.harddayz.client;

import java.util.ArrayList;
import java.util.List;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Missile;
import com.harddayz.network.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Радар сбоку экрана: что летает вокруг (разведчики, ударные дроны, ракеты с точкой падения), откуда был пуск
 * и сколько до подлёта, где своя задача. Север вверху. Показывается в режиме выживания или с планшетом РЛС.
 */
final class RadarPanel {
    private static final int RANGE = 320;
    private static final List<Launch> LAUNCHES = new ArrayList<>();

    private record Launch(Net.SurvivalLaunch s, long at) {
    }

    private RadarPanel() {
    }

    static void launch(Net.SurvivalLaunch s) {
        LAUNCHES.add(new Launch(s, System.currentTimeMillis()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // предупреждение на экран тем, кому это летит
        double d = Math.hypot(mc.player.getX() - s.tx(), mc.player.getZ() - s.tz());
        if (d < 90) {
            Component what = Component.translatable("radar.harddayz.weapon." + s.weapon());
            Component msg = Component.translatable("message.harddayz.surv_launch", what, s.count(), compass(s.sx() - s.tx(), s.sz() - s.tz()),
                    String.format("%.0f", s.eta() / 20.0));
            ClientSetup.alert(msg.getString());
        }
    }

    static String compass(double dx, double dz) {
        String[] n = Component.translatable("radar.harddayz.compass").getString().split(",");
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        int i = Math.floorMod((int) Math.round(a / 45), 8);
        return n.length == 8 ? n[i] : String.valueOf((int) a);
    }

    static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || DronePilot.active()) {
            return false;
        }
        if (SurvivalHud.active()) {
            return true;
        }
        for (var s : mc.player.getInventory().items) {
            if (s.is(com.harddayz.registry.ModItems.RADAR_DISPLAY.get())) {
                return true;
            }
        }
        return false;
    }

    static void render(Gfx g, float pt) {
        if (!shown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        int r = 46;
        int cx = g.guiWidth() - r - 8, cy = g.guiHeight() / 2 - 20;
        double k = r / (double) RANGE;
        g.fill(cx - r - 2, cy - r - 2, cx + r + 3, cy + r + 3, 0x90081410);
        ring(g, cx, cy, r, 0xC02E8A60);
        ring(g, cx, cy, r / 2, 0x602E8A60);
        g.fill(cx - r, cy, cx + r, cy + 1, 0x402E8A60);
        g.fill(cx, cy - r, cx + 1, cy + r, 0x402E8A60);
        g.text(mc.font, "N", cx - 2, cy - r - 1, 0xFF7CE0A8, false);
        double ang = (System.currentTimeMillis() % 3000) / 3000.0 * Math.PI * 2;
        for (int i = 0; i < r; i += 2) {
            int x = cx + (int) (Math.sin(ang) * i), y = cy - (int) (Math.cos(ang) * i);
            g.fill(x, y, x + 1, y + 1, 0x7040C890);
        }
        // задача раунда
        int[] t = SurvivalHud.task();
        if (t != null) {
            dot(g, cx, cy, r, k, t[0] - me.getX(), t[1] - me.getZ(), 0xFF40FF80, 3, true);
        }
        // союзники
        for (Player p : mc.level.players()) {
            if (p != me && !p.isSpectator()) {
                dot(g, cx, cy, r, k, p.getX() - me.getX(), p.getZ() - me.getZ(), 0xFFFFFFFF, 1, false);
            }
        }
        // что летает
        for (Entity e : mc.level.entitiesForRendering()) {
            double dx = e.getX() - me.getX(), dz = e.getZ() - me.getZ();
            if (dx * dx + dz * dz > (double) RANGE * RANGE) {
                continue;
            }
            if (e instanceof Drone d && d.kind().autonomous) {
                boolean scout = d.kind() == Drone.Kind.WING_RECON;
                dot(g, cx, cy, r, k, dx, dz, scout ? 0xFF60B0FF : 0xFFFFA030, scout ? 2 : 2, false);
            } else if (e instanceof Missile m && m.isThreat()) {
                dot(g, cx, cy, r, k, dx, dz, 0xFFFF4040, 1, false);
                Vec3 hit = impact(m);
                if (hit != null) {
                    int hx = cx + (int) Mth.clamp((hit.x - me.getX()) * k, -r, r), hz = cy + (int) Mth.clamp((hit.z - me.getZ()) * k, -r, r);
                    g.fill(hx - 2, hz, hx + 3, hz + 1, 0xFFFF4040);
                    g.fill(hx, hz - 2, hx + 1, hz + 3, 0xFFFF4040);
                }
            }
        }
        // пуски: стрелка на краю со стороны батареи и отсчёт до подлёта
        long now = System.currentTimeMillis();
        LAUNCHES.removeIf(l -> now - l.at > l.s.eta() * 50L + 1500);
        int line = 0;
        for (Launch l : LAUNCHES) {
            double dx = l.s.sx() - me.getX(), dz = l.s.sz() - me.getZ(), len = Math.max(1, Math.hypot(dx, dz));
            int ex = cx + (int) (dx / len * (r - 3)), ez = cy + (int) (dz / len * (r - 3));
            boolean blink = (now / 250) % 2 == 0;
            g.fill(ex - 2, ez - 2, ex + 3, ez + 3, blink ? 0xFFFF3030 : 0xFF801010);
            int left = (int) Math.max(0, l.s.eta() / 20.0 - (now - l.at) / 1000.0);
            if (line < 4) {
                String txt = Component.translatable("radar.harddayz.weapon." + l.s.weapon()).getString() + " ×" + l.s.count() + "  " + left + "с";
                g.text(mc.font, txt, cx - r, cy + r + 6 + line * 10, 0xFFFF8070, true);
                line++;
            }
            // точка падения
            dot(g, cx, cy, r, k, l.s.tx() - me.getX(), l.s.tz() - me.getZ(), blink ? 0xFFFF2020 : 0x80FF2020, 2, false);
        }
        g.text(mc.font, RANGE + "м", cx + r - 22, cy + r - 8, 0x905AA080, false);
    }

    /** Куда упадёт неуправляемая ракета или мина: просчёт дуги вперёд. */
    private static Vec3 impact(Missile m) {
        if (m.kind() != Missile.Kind.GRAD) {
            return null;
        }
        var level = Minecraft.getInstance().level;
        Vec3 p = m.position(), v = m.position().subtract(m.xo, m.yo, m.zo);
        for (int i = 0; i < 400; i++) {
            v = v.add(0, -Missile.Kind.GRAD.gravity, 0);
            p = p.add(v);
            int x = Mth.floor(p.x), z = Mth.floor(p.z);
            double ground = ClientBlasts.loaded(level, x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : 64;
            if (p.y <= ground && v.y < 0) {
                return p;
            }
        }
        return null;
    }

    private static void dot(Gfx g, int cx, int cy, int r, double k, double dx, double dz, int col, int s, boolean clamp) {
        double x = dx * k, y = dz * k, d = Math.hypot(x, y);
        if (d > r - 1) {
            if (!clamp) {
                return;
            }
            x *= (r - 2) / d;
            y *= (r - 2) / d;
        }
        int px = cx + (int) x, py = cy + (int) y;
        g.fill(px - s, py - s, px + s + 1, py + s + 1, col);
    }

    private static void ring(Gfx g, int cx, int cy, int r, int col) {
        int n = r * 5;
        for (int i = 0; i < n; i++) {
            double t = i * Math.PI * 2 / n;
            g.fill(cx + (int) Math.round(Math.cos(t) * r), cy + (int) Math.round(Math.sin(t) * r), cx + (int) Math.round(Math.cos(t) * r) + 1,
                    cy + (int) Math.round(Math.sin(t) * r) + 1, col);
        }
    }
}
