package com.harddayz.client;

import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Звук со своей дальностью. Ванильное затухание запоминается один раз при запуске (у мотора, который стартует с нулевой
 * громкости, это всего 16 блоков), а громкость выше 1 не делает звук громче. Поэтому затухание движка выключено
 * и громкость по расстоянию до камеры считается здесь каждый тик.
 */
abstract class RangeSound extends AbstractTickableSoundInstance {
    protected double range;

    protected RangeSound(SoundEvent sound, SoundSource source, double range) {
        super(sound, source, SoundInstance.createUnseededRandom());
        this.range = range;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.delay = 0;
        this.volume = 0;
    }

    /** Откуда слышит игрок: камера (у пилота дрона она на дроне). */
    static Vec3 ears() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    }

    /** Доля громкости на расстоянии d: вблизи 1, у границы дальности 0. */
    static float gain(double d, double range) {
        if (d >= range) {
            return 0;
        }
        double ref = range * 0.4;
        return (float) (ref / (ref + d) * (1 - d / range));
    }

    protected float gainHere() {
        return gain(ears().distanceTo(new Vec3(x, y, z)), range);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** Одиночный звук в точке. alive: пока true, звук идёт, потом быстро гаснет (снаряд уже взорвался). */
    static final class OneShot extends RangeSound {
        private final float loud;
        private final @Nullable BooleanSupplier alive;
        private float fade = 1;

        OneShot(SoundEvent sound, SoundSource source, Vec3 at, double range, float loud, float pitch, @Nullable BooleanSupplier alive) {
            super(sound, source, range);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.loud = loud;
            this.pitch = pitch;
            this.alive = alive;
            this.volume = Mth.clamp(loud * gainHere(), 0, 1);
        }

        @Override
        public void tick() {
            if (alive != null && !alive.getAsBoolean()) {
                fade -= 0.34f;
                if (fade <= 0) {
                    stop();
                    return;
                }
            }
            volume = Mth.clamp(loud * gainHere(), 0, 1) * fade;
        }
    }

    /**
     * Запуск звука. Не сразу, а после текущего кадра: эту функцию зовут и из тика других звуков (пролёт дрона),
     * а движок в это время перебирает свой список звуков — новый звук посреди перебора роняет игру.
     */
    static void play(SoundEvent sound, SoundSource source, Vec3 at, double range, float loud, float pitch, @Nullable BooleanSupplier alive) {
        if (ears().distanceTo(at) < range) {
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> mc.getSoundManager().play(new OneShot(sound, source, at, range, loud, pitch, alive)));
        }
    }
}
