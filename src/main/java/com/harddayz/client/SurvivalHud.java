package com.harddayz.client;

import com.harddayz.network.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Таймер режима выживания внизу экрана: раунд, сколько прошло, сколько до конца раунда или затишья. */
final class SurvivalHud {
    private static Net.SurvivalState state;
    private static long at;

    private SurvivalHud() {
    }

    static void state(Net.SurvivalState s) {
        state = s;
        at = System.currentTimeMillis();
    }

    private static String time(int s) {
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    static void render(Gfx g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (state == null || System.currentTimeMillis() - at > 3000 || mc.options.hideGui || mc.player == null) {
            return;
        }
        Net.SurvivalState s = state;
        Component line = Component.translatable(s.lull() ? "hud.harddayz.surv_line_lull" : "hud.harddayz.surv_line", s.round(), time(s.time()), time(s.left()),
                s.drones(), s.alive());
        float k = 0.7f;
        int w = mc.font.width(line) + 8;
        var pose = g.g.pose();
        pose.pushPose();
        pose.translate(g.guiWidth() / 2f, g.guiHeight() - 62, 0);
        pose.scale(k, k, 1);
        boolean hot = s.seen() && (System.currentTimeMillis() / 300) % 2 == 0;
        g.fill(-w / 2, 0, w / 2, 12, hot ? 0xB0801010 : s.lull() ? 0x90104018 : 0x80101010);
        g.text(mc.font, line, -w / 2 + 4, 2, hot || s.seen() ? 0xFFFF6060 : s.lull() ? 0xFF80FF90 : 0xFFE0E0D0, true);
        pose.popPose();
    }
}
