package com.harddayz.client;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;

import net.minecraft.client.Minecraft;

/** Точка входа из общего кода в клиентский (вызывается только на клиенте). */
public final class ClientAccess {
    private ClientAccess() {
    }

    public static void openLauncher(Launcher l) {
        Minecraft.getInstance().setScreen(new LauncherScreen(l));
    }

    public static void mortarInfo(com.harddayz.network.Net.MortarInfo p) {
        var mc = Minecraft.getInstance();
        if (p.open() && mc.level != null && mc.level.getEntity(p.entity()) instanceof Launcher l) {
            mc.setScreen(new MortarScreen(l));
        }
        MortarScreen.onInfo(p);
    }

    public static java.util.List<String> lives = java.util.List.of();

    public static void vehicleSound(com.harddayz.entity.Launcher l) {
        if (loopDead(l.soundLoop)) {
            VehicleSound s = new VehicleSound(l);
            l.soundLoop = s;
            Minecraft.getInstance().getSoundManager().play(s);
        }
    }

    /** Петля не запущена или её выкинул движок (кончились каналы, перезагрузка звука) — пора запускать снова. */
    private static boolean loopDead(Object loop) {
        return !(loop instanceof net.minecraft.client.resources.sounds.SoundInstance s) || !Minecraft.getInstance().getSoundManager().isActive(s);
    }

    /** Полёт снаряда: пролёт и петля. Петель немного, поэтому пробуем снова, пока снаряд подлетает и есть место. */
    public static void missileSound(com.harddayz.entity.Missile m) {
        MissileSound.flyby(m);
        if (m.tickCount % 5 != 2 || !MissileSound.wants(m) || !loopDead(m.soundLoop)) {
            return;
        }
        double d = RangeSound.ears().distanceTo(m.position());
        int active = MissileSound.active();
        if (d > MissileSound.rangeOf(m) || active >= 10 || active >= 6 && d > 120) {
            return;
        }
        MissileSound s = new MissileSound(m);
        m.soundLoop = s;
        Minecraft.getInstance().getSoundManager().play(s);
    }

    /** Свист на подлёте: запись кончается ровно к взрыву. Если снаряд исчез раньше (сбит, раскрылась кассета), свист обрывается. */
    public static void incoming(net.minecraft.world.entity.Entity e, net.minecraft.sounds.SoundEvent sound, net.minecraft.world.phys.Vec3 hit, double range) {
        RangeSound.play(sound, net.minecraft.sounds.SoundSource.HOSTILE, hit.add(0, 2, 0), range, 1f, 1f, () -> !e.isRemoved());
    }

    /** Пуски, звук которых ещё летит до камеры: пакет и сколько тиков назад. */
    private static final java.util.List<LaunchWave> LAUNCHES = new java.util.ArrayList<>();

    private static final class LaunchWave {
        final com.harddayz.network.Net.LaunchSound s;
        int age;

        LaunchWave(com.harddayz.network.Net.LaunchSound s) {
            this.s = s;
        }
    }

    public static void launchSound(com.harddayz.network.Net.LaunchSound s) {
        LAUNCHES.add(new LaunchWave(s));
    }

    static void tickLaunches(boolean inWorld) {
        if (!inWorld) {
            LAUNCHES.clear();
            return;
        }
        LAUNCHES.removeIf(w -> {
            var at = new net.minecraft.world.phys.Vec3(w.s.x(), w.s.y(), w.s.z());
            if (w.age++ * ClientBlasts.SOUND_SPEED < RangeSound.ears().distanceTo(at)) {
                return w.age > 200;
            }
            var ev = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(w.s.sound());
            RangeSound.play(ev, net.minecraft.sounds.SoundSource.HOSTILE, at, w.s.range(), 1f, w.s.pitch(), null);
            return true;
        });
    }

    public static void survival(com.harddayz.network.Net.SurvivalState p) {
        SurvivalHud.state(p);
    }

    public static void mapInfo(com.harddayz.network.Net.FrontInfo p) {
        MiniMap.info(p);
    }

    public static void mapState(com.harddayz.network.Net.MapState p) {
        MiniMap.state(p.rows());
    }

    /** Где «уши»: камера игрока или его дрона. */
    public static net.minecraft.world.phys.Vec3 ears() {
        return RangeSound.ears();
    }

    /** «Шахед» или крылатая пикирует рядом: нарастающий вой, который кончается к взрыву. */
    public static void droneIncoming(com.harddayz.entity.Drone d) {
        var k = d.kind();
        if (d.incomingPlayed || k.warhead < 2 || k == com.harddayz.entity.Drone.Kind.INTERCEPTOR || Minecraft.getInstance().getCameraEntity() == d) {
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        var v = d.position().subtract(d.xo, d.yo, d.zo);
        if (v.y > -0.25) {
            return;
        }
        double len = k.jet() ? 3.0 : k.shahedLike() ? 3.2 : 2.4;
        int top = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, d.getBlockX(), d.getBlockZ());
        double ticks = (d.getY() - top) / -v.y;
        if (ticks > len * 20 || ticks < len * 10) {
            return; // рано или уже поздно: обрывок записи хуже тишины
        }
        var hit = new net.minecraft.world.phys.Vec3(d.getX() + v.x * ticks, top, d.getZ() + v.z * ticks);
        if (hit.distanceTo(RangeSound.ears()) > 160) {
            return;
        }
        d.incomingPlayed = true;
        RangeSound.play(k.jetDrone() ? com.harddayz.registry.ModSounds.INCOMING_JETDRONE.get()
                : k.jet() ? com.harddayz.registry.ModSounds.INCOMING_CRUISE.get()
                : k.shahedLike() ? com.harddayz.registry.ModSounds.INCOMING_SHAHED.get() : com.harddayz.registry.ModSounds.INCOMING_DRONE.get(), net.minecraft.sounds.SoundSource.HOSTILE, hit.add(0, 2, 0), 170, 1f, 1f,
                () -> !d.isRemoved() && d.getY() - d.yo < -0.05); // вышел из пике — вой стихает
    }

    /** Толчок камеры водителя при ударе машины. */
    public static void bump(float strength) {
        ClientBlasts.bump(strength);
    }

    public static void openWarMenu(com.harddayz.network.Net.OpenWarMenu m) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchScreen ms && m.active()) {
            ms.update(m);
        } else if (m.active()) {
            mc.setScreen(new MatchScreen(m));
        } else if (!(mc.screen instanceof WarSetupScreen)) {
            mc.setScreen(new WarSetupScreen());
        }
    }

    public static net.minecraft.world.phys.Vec3 localPlayerPos() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.position();
    }

    public static void lives(java.util.List<String> rows) {
        lives = rows;
    }

    public static void matchEnd(String winner, java.util.List<String> rows) {
        Minecraft.getInstance().setScreen(new MatchEndScreen(winner, rows));
    }

    public static void openTargetLog(boolean offhand) {
        Minecraft.getInstance().setScreen(new TargetLogScreen(offhand));
    }

    /** Ввод водителя: {газ (+1/-1), руль (+1 влево / -1 вправо), тормоз}. */
    public static float[] driveInput() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return new float[] {0, 0, 0, 0};
        }
        var in = mc.player.input;
        float fwd = (in.up ? 1 : 0) - (in.down ? 1 : 0);
        float turn = (in.left ? 1 : 0) - (in.right ? 1 : 0);
        return new float[] {fwd, turn, in.jumping ? 1 : 0, mc.options.keySprint.isDown() ? 1 : 0};
    }

    public static boolean isPilotingLocally(Drone d) {
        return DronePilot.isPiloting(d);
    }

    /** Клиентский тик дрона: звук мотора и подхват управления, если пилот — мы. */
    public static void droneClientTick(Drone d) {
        Minecraft mc = Minecraft.getInstance();
        if (d.tickCount % 20 == 1 && loopDead(d.soundLoop)) {
            DroneSound s = new DroneSound(d);
            d.soundLoop = s;
            mc.getSoundManager().play(s);
        }
        if (mc.player != null && d.pilotId() == mc.player.getId() && !DronePilot.active()) {
            DronePilot.start(d);
        }
        DronePilot.step(d);
        DroneExhaust.tick(d);
    }
}
