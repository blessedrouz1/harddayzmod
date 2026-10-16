package com.harddayz.client;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Пролёт мимо игрока: заранее сведённый звук с допплером ставится в точку наибольшего сближения так, чтобы его пик
 * (1.5 с от начала записи) совпал с моментом, когда снаряд проносится рядом.
 */
final class Flyby {
    /** Пик в записи пролёта, секунд от начала. */
    private static final double PEAK = 1.5;

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
        if (v2 < 4) {
            return false;
        }
        Vec3 r = e.position().subtract(mc.player.getEyePosition());
        double tc = -r.dot(v) / v2; // через сколько секунд ближе всего
        if (tc > PEAK || tc < PEAK - 0.3) {
            return false;
        }
        Vec3 closest = r.add(v.scale(tc));
        if (closest.length() > maxDist) {
            return false;
        }
        // летит в землю рядом с игроком — это подлёт, а не пролёт
        if (v.y < -0.5 * Math.sqrt(v2) && closest.length() < 25) {
            return false;
        }
        Vec3 at = mc.player.getEyePosition().add(closest);
        mc.level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, 1f, false);
        return true;
    }
}
