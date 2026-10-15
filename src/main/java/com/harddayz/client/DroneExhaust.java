package com.harddayz.client;

import com.harddayz.entity.Drone;
import com.harddayz.registry.ModParticles;
import net.minecraft.world.phys.Vec3;

/** Local exhaust follows the rendered jet nozzle; no particle packets or unbounded per-drone caches. */
final class DroneExhaust {
    private DroneExhaust() {}

    static void tick(Drone drone) {
        if ((drone.kind() != Drone.Kind.SHAHED_238 && drone.kind() != Drone.Kind.GERAN5)
                || !drone.engineOn() || !drone.isAlive() || drone.onGround()) return;
        Vec3 previous = new Vec3(drone.xo, drone.yo, drone.zo);
        Vec3 travel = drone.position().subtract(previous);
        // Parked/prelaunch drones and teleports must not draw a smoke line across the world.
        if (travel.lengthSqr() < 0.0001 || travel.lengthSqr() > 64) return;
        float scale = drone.kind() == Drone.Kind.GERAN5 ? 0.85f : 1;
        Vec3 direction = Vec3.directionFromRotation(drone.getXRot(), drone.getYRot());
        Vec3 tail = nozzle(drone.position(), direction, scale);
        Vec3 oldTail = nozzle(previous, Vec3.directionFromRotation(drone.xRotO, drone.yRotO), scale);
        var level = drone.level();
        int steps = Math.min(6, Math.max(1, (int) Math.ceil(tail.distanceTo(oldTail) / 0.6)));
        for (int i = 0; i < steps; i++) {
            Vec3 point = tail.lerp(oldTail, i / (double) steps);
            var random = level.random;
            level.addAlwaysVisibleParticle(ModParticles.JET_SMOKE.get(), true, point.x, point.y, point.z,
                    -direction.x * 0.035 + random.nextGaussian() * 0.008,
                    -direction.y * 0.035 + 0.006, -direction.z * 0.035 + random.nextGaussian() * 0.008);
        }
        level.addAlwaysVisibleParticle(ModParticles.JET_FIRE.get(), true, tail.x, tail.y, tail.z,
                -direction.x * 0.16, -direction.y * 0.16, -direction.z * 0.16);
    }

    static Vec3 nozzle(Vec3 position, Vec3 direction, float scale) {
        // shahed238.geo.json nozzle ends at z=24.2 px; renderer lifts the model by 0.25 blocks.
        return position.add(0, 0.25, 0).subtract(direction.scale(24.2 / 16 * scale));
    }
}
