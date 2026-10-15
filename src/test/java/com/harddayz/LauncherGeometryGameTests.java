package com.harddayz;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;
import com.harddayz.entity.LauncherGeometry;
import com.harddayz.entity.Missile;
import com.harddayz.entity.Vehicle;
import com.harddayz.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("harddayz")
@PrefixGameTestTemplate(false)
public class LauncherGeometryGameTests {
    private static void near(GameTestHelper h, Vec3 actual, Vec3 expected, String message) {
        h.assertTrue(actual.distanceTo(expected) < 0.0001, message + ": " + actual + " vs " + expected);
    }

    @GameTest(template = "empty")
    public static void modelAnchorsFollowRotations(GameTestHelper h) {
        // Coordinates independently read from grad.geo.json: last visible nose and both pivots.
        near(h, LauncherGeometry.muzzle(Launcher.Kind.GRAD, 40, 0, 0, 0, 0),
                new Vec3(18.5 / 16, 49.5 / 16, 11.0 / 16), "Grad nose");
        near(h, LauncherGeometry.muzzle(Launcher.Kind.GRAD, 40, 90, 0, 0, 0),
                new Vec3(-11.0 / 16, 49.5 / 16, 18.5 / 16), "Body yaw");
        near(h, LauncherGeometry.muzzle(Launcher.Kind.GRAD, 40, 0, 90, 0, 0),
                new Vec3(-33.0 / 16, 49.5 / 16, -3.5 / 16), "Turret pivot");
        near(h, LauncherGeometry.muzzle(Launcher.Kind.GRAD, 40, 0, 0, 90, 0),
                new Vec3(18.5 / 16, 69.0 / 16, -35.5 / 16), "Rack pivot");
        near(h, LauncherGeometry.direction(Launcher.Kind.GRAD, 90, 0, 45, 0),
                new Vec3(-Math.sqrt(0.5), Math.sqrt(0.5), 0), "Launch axis");
        near(h, LauncherGeometry.direction(Launcher.Kind.ISKANDER, 0, 0, 0, 1),
                new Vec3(0, 1, 0), "Raised erector");
        near(h, LauncherGeometry.direction(Launcher.Kind.RAMP, 0, 0, 0, 0),
                new Vec3(0, Math.sin(Math.toRadians(15)), Math.cos(Math.toRadians(15))), "Built-in ramp tilt");
        h.succeed();
    }

    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = Launcher.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void angle(Launcher launcher, String name, float value) throws Exception {
        var smooth = (Vehicle.Smooth) field(name).get(launcher);
        smooth.prev = smooth.value = value;
    }

    @SuppressWarnings("unchecked")
    private static void ammo(Launcher launcher, int count) throws Exception {
        launcher.getEntityData().set((EntityDataAccessor<Integer>) field("AMMO").get(null), count);
    }

    @GameTest(template = "empty")
    public static void projectilesSpawnAtModelAnchors(GameTestHelper h) throws Exception {
        var kinds = new Launcher.Kind[]{Launcher.Kind.GRAD, Launcher.Kind.GRAD_P, Launcher.Kind.TOS,
                Launcher.Kind.SMERCH, Launcher.Kind.URAGAN, Launcher.Kind.ISKANDER, Launcher.Kind.TOCHKA,
                Launcher.Kind.S400, Launcher.Kind.HIMARS, Launcher.Kind.KALIBR, Launcher.Kind.MORTAR,
                Launcher.Kind.SHAHED, Launcher.Kind.RAMP};
        for (var kind : kinds) {
            Launcher launcher = ModEntities.LAUNCHER.get().create(h.getLevel());
            launcher.setKind(kind);
            launcher.setPos(h.absolutePos(new BlockPos(1, 25, 1)).getCenter());
            launcher.setYRot(73);
            ammo(launcher, kind.capacity);
            angle(launcher, "turret", 37);
            angle(launcher, "samTurret", 37);
            angle(launcher, "elevation", 45);
            angle(launcher, "erect", 1);
            field("target").set(launcher, launcher.blockPosition().offset(0, 0, 500));
            boolean ballistic = kind == Launcher.Kind.ISKANDER || kind == Launcher.Kind.TOCHKA;
            field("timer").setInt(launcher, ballistic ? 49 : 0);
            String method = switch (kind) {
                case ISKANDER, TOCHKA -> "fireIskander";
                case S400 -> "fireS400Ground";
                case HIMARS -> "fireHimars";
                case KALIBR -> "fireKalibr";
                case MORTAR -> "fireMortar";
                case SHAHED, RAMP -> "fireShahed";
                default -> "fireGrad";
            };
            Vec3 expected = launcher.position().add(LauncherGeometry.muzzle(kind, kind.capacity, 73, 37, 45, 1));
            var fire = Launcher.class.getDeclaredMethod(method, net.minecraft.server.level.ServerLevel.class);
            fire.setAccessible(true);
            java.util.Set<Integer> before = new java.util.HashSet<>();
            for (Entity e : h.getLevel().getAllEntities()) before.add(e.getId());
            fire.invoke(launcher, h.getLevel());
            int found = 0;
            for (Entity e : h.getLevel().getAllEntities()) {
                if (!before.contains(e.getId()) && (e instanceof Missile || e instanceof Drone)) {
                    try {
                        near(h, e.position(), expected, "Spawn offset for " + kind);
                        h.assertTrue(e.getYRot() == e.yRotO && e.getXRot() == e.xRotO, "Spawn rotation interpolation for " + kind);
                        found++;
                    } finally { e.discard(); }
                }
            }
            h.assertTrue(found == 1 && launcher.ammo() == kind.capacity - 1, "Launch failed for " + kind);
        }
        h.succeed();
    }
}
