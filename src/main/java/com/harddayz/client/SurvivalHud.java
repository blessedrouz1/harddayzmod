package com.harddayz.client;

import com.harddayz.network.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Режим выживания внизу экрана: таймер, полоска «тебя видят», наводка и задача раунда. */
final class SurvivalHud {
    private static Net.SurvivalState state;
    private static long at;

    private SurvivalHud() {
    }

    static void state(Net.SurvivalState s) {
        state = s;
        at = System.currentTimeMillis();
    }

    static boolean active() {
        return state != null && System.currentTimeMillis() - at < 3000;
    }

    /** Точка задачи (x, z) или null. */
    static int[] task() {
        return active() && (state.task() == 1 || state.task() == 2) && !state.taskDone() ? new int[] {state.taskX(), state.taskZ()} : null;
    }

    private static String time(int s) {
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    static void render(Gfx g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui || mc.player == null) {
            return;
        }
        Net.SurvivalState s = state;
        float k = 0.7f;
        var pose = g.g.pose();
        pose.pushPose();
        pose.translate(g.guiWidth() / 2f, g.guiHeight() - 62, 0);
        pose.scale(k, k, 1);
        Component line = Component.translatable(s.lull() ? "hud.harddayz.surv_line_lull" : "hud.harddayz.surv_line", s.round(), time(s.time()), time(s.left()),
                s.drones(), s.alive());
        int w = mc.font.width(line) + 8;
        g.fill(-w / 2, 0, w / 2, 12, s.lull() ? 0x90104018 : 0x80101010);
        g.text(mc.font, line, -w / 2 + 4, 2, s.lull() ? 0xFF80FF90 : 0xFFE0E0D0, true);
        int y = -14;
        // засечка: полоска растёт, пока дрон тебя держит
        if (s.handoff() >= 0) {
            boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
            Component h = Component.translatable("hud.harddayz.surv_handoff", s.handoff());
            int hw = mc.font.width(h) + 10;
            g.fill(-hw / 2, y, hw / 2, y + 12, blink ? 0xD0A01010 : 0xD0601010);
            g.text(mc.font, h, -hw / 2 + 5, y + 2, 0xFFFFFFFF, true);
            y -= 14;
        } else if (s.heat() > 0) {
            int bw = 120;
            g.fill(-bw / 2 - 1, y + 3, bw / 2 + 1, y + 10, 0xA0000000);
            g.fill(-bw / 2, y + 4, -bw / 2 + bw * s.heat() / 100, y + 9, s.heat() > 70 ? 0xFFFF3030 : 0xFFFFA030);
            g.text(mc.font, Component.translatable("hud.harddayz.surv_heat"), -bw / 2 - mc.font.width(Component.translatable("hud.harddayz.surv_heat")) - 4,
                    y + 2, 0xFFFFB060, true);
            y -= 14;
        }
        // задача раунда
        if (s.task() > 0) {
            String where = "";
            if (s.task() != 3) {
                double dx = s.taskX() + 0.5 - mc.player.getX(), dz = s.taskZ() + 0.5 - mc.player.getZ();
                where = "  " + (int) Math.hypot(dx, dz) + "м " + RadarPanel.compass(dx, dz);
            }
            Component t = s.taskDone() ? Component.translatable("hud.harddayz.surv_task_ok")
                    : Component.translatable("hud.harddayz.surv_task_" + s.task(), s.taskHave(), s.taskNeed()).append(where);
            int tw = mc.font.width(t) + 8;
            g.fill(-tw / 2, y, tw / 2, y + 12, 0x80103030);
            g.text(mc.font, t, -tw / 2 + 4, y + 2, s.taskDone() ? 0xFF80FF90 : 0xFF80E0FF, true);
        }
        pose.popPose();
    }
}
