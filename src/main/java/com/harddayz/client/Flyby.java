package com.harddayz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Пролёт мимо игрока: заранее сведённый звук с допплером ставится в точку наибольшего сближения так, чтобы его пик
 * (1.5 с от начала записи) совпал с моментом, когда звук ближайшей точки дойдёт до камеры.
 */
final class Flyby {
    /** Пик в записи пролёта, секунд от начала. */
    private static final double PEAK = 1.5;
    /** В залпе слышно не больше стольких пролётов за секунду, иначе каша. */
    private static final int PER_SECOND = 4;
    private static final java.util.ArrayDeque<Long> RECENT = new java.util.ArrayDeque<>();

    private Flyby() {
    }

    /** true — звук пролёта запущен сейчас. */
    static boolean check(Entity e, SoundEvent sound, double maxDist, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getCameraEntity() == e) {
            return false;
        }
        Vec3 v = e.position().subtract(e.xo, e.yo, e.zo).scale(20); // блоков в секунду
        double v2 = v.lengthSqr();
        if (v2 < 100) {
            return false; // висит или еле ползёт — это не пролёт
        }
        Vec3 ears = RangeSound.ears();
        Vec3 r = e.position().subtract(ears);
        double tc = -r.dot(v) / v2; // через сколько секунд ближе всего
        if (tc <= 0) {
            return false;
        }
        Vec3 closest = r.add(v.scale(tc));
        double heard = tc + closest.length() / 340.0; // звук ещё летит до камеры
        if (heard > PEAK || heard < PEAK - 0.3 || closest.length() > maxDist) {
            return false;
        }
        // летит в землю рядом с игроком — это подлёт, а не пролёт
        if (v.y < -0.5 * Math.sqrt(v2) && closest.length() < 25) {
            return false;
        }
        long now = mc.level.getGameTime();
        RECENT.removeIf(t -> t > now || now - t > 20);
        if (RECENT.size() >= PER_SECOND) {
            return false;
        }
        RECENT.add(now);
        RangeSound.play(sound, SoundSource.HOSTILE, ears.add(closest), maxDist * 2.2, volume, 1f, null);
        return true;
    }
}
