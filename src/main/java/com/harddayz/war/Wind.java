package com.harddayz.war;

import net.minecraft.world.phys.Vec3;

/** Ветер над картой: медленно меняет направление и силу. Сносит мины, ракеты, газ и дым. */
public final class Wind {
    private static final java.util.Random R = new java.util.Random();
    private static double ang = R.nextDouble() * Math.PI * 2;
    private static double speed = 0.4;
    private static double wantAng = ang;
    private static double wantSpeed = speed;
    private static long last = Long.MIN_VALUE;

    private Wind() {
    }

    private static void update(long now) {
        if (now == last) {
            return;
        }
        long steps = last == Long.MIN_VALUE ? 1 : Math.min(200, Math.max(1, now - last));
        last = now;
        if (now % 2400 == 0) {
            wantAng += R.nextGaussian() * 0.9;
            wantSpeed = Math.max(0.05, Math.min(1, wantSpeed + R.nextGaussian() * 0.3));
        }
        for (int i = 0; i < steps; i++) {
            ang += (wantAng - ang) * 0.002;
            speed += (wantSpeed - speed) * 0.002;
        }
    }

    /** Куда дует, длина 0..1 (1 — около 10 м/с). */
    public static Vec3 vec(long now) {
        update(now);
        return new Vec3(-Math.sin(ang) * speed, 0, Math.cos(ang) * speed);
    }

    /** Откуда дует, градусы по компасу (0 — север). */
    public static int fromDeg(long now) {
        Vec3 v = vec(now);
        double to = Math.toDegrees(Math.atan2(v.x, -v.z));
        return (int) Math.round(((to + 180) % 360 + 360) % 360);
    }

    /** Сила, м/с. */
    public static int ms(long now) {
        return (int) Math.round(vec(now).length() * 10);
    }
}
