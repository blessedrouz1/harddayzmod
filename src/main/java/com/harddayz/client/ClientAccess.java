package com.harddayz.client;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;

import net.minecraft.client.Minecraft;

/** Точка входа из общего кода в клиентский (вызывается только на клиенте). */
public final class ClientAccess {
    private ClientAccess() {
    }

    public static void openLauncher(Launcher l) {
        Minecraft.getInstance().gui.setScreen(new LauncherScreen(l));
    }

    public static java.util.List<String> lives = java.util.List.of();

    public static void lives(java.util.List<String> rows) {
        lives = rows;
    }

    public static void matchEnd(String winner, java.util.List<String> rows) {
        Minecraft.getInstance().gui.setScreen(new MatchEndScreen(winner, rows));
    }

    public static void openTargetLog(boolean offhand) {
        Minecraft.getInstance().gui.setScreen(new TargetLogScreen(offhand));
    }

    /** Ввод водителя: {газ (+1/-1), руль (+1 влево / -1 вправо), тормоз}. */
    public static float[] driveInput() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            return new float[] {0, 0, 0};
        }
        var in = mc.player.input.keyPresses;
        float fwd = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        float turn = (in.left() ? 1 : 0) - (in.right() ? 1 : 0);
        return new float[] {fwd, turn, in.jump() ? 1 : 0};
    }

    public static boolean isPilotingLocally(Drone d) {
        return DronePilot.isPiloting(d);
    }

    /** Клиентский тик дрона: звук мотора и подхват управления, если пилот — мы. */
    public static void droneClientTick(Drone d) {
        Minecraft mc = Minecraft.getInstance();
        if (!d.soundStarted) {
            d.soundStarted = true;
            mc.getSoundManager().play(new DroneSound(d));
        }
        if (mc.player != null && d.pilotId() == mc.player.getId() && !DronePilot.active()) {
            DronePilot.start(d);
        }
        DronePilot.step(d);
    }
}
