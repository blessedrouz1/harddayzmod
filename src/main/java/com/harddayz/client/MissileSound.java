package com.harddayz.client;

import com.harddayz.entity.Missile;
import com.harddayz.registry.ModSounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Звук летящего снаряда: реактивные — рёв двигателя, баллистика — низкий гул, зенитные — свист. */
public class MissileSound extends AbstractTickableSoundInstance {
    private final Missile m;
    private final float base;
    private float loud;
    private boolean passed;
    private int duck;

    private static SoundEvent pick(Missile m) {
        if (m.look() == 7) {
            return ModSounds.FLY_MORTAR.get();
        }
        if (m.kind() == Missile.Kind.SAM || m.look() == 5) {
            return ModSounds.FLY_JET.get();
        }
        if (m.kind() == Missile.Kind.BALLISTIC) {
            return ModSounds.FLY_MISSILE.get();
        }
        return ModSounds.FLY_ROCKET.get();
    }

    public static boolean wants(Missile m) {
        return m.kind() != Missile.Kind.BOMB || m.look() == 6; // мина летит молча, её слышно на подлёте // гранаты молчат, выстрел РПГ шипит
    }

    public MissileSound(Missile m) {
        super(pick(m), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.m = m;
        this.looping = true;
        this.delay = 0;
        this.base = switch (m.kind()) {
            case SAM -> 1.35f;
            default -> 1f;
        };
        this.volume = m.look() == 7 ? 0.8f : m.kind() == Missile.Kind.BALLISTIC ? 5f : 2.5f;
        this.loud = this.volume;
        this.pitch = base;
        this.x = m.getX();
        this.y = m.getY();
        this.z = m.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (m.isRemoved()) {
            stop();
            return;
        }
        x = m.getX();
        y = m.getY();
        z = m.getZ();
        if (!passed && m.kind() != Missile.Kind.SAM && m.look() != 5 && m.look() != 6 && m.look() != 8) {
            var s = m.look() == 7 ? ModSounds.FLYBY_MORTAR.get() : m.kind() == Missile.Kind.BALLISTIC ? ModSounds.FLYBY_BALLISTIC.get()
                    : ModSounds.FLYBY_MLRS.get();
            if (Flyby.check(m, s, m.kind() == Missile.Kind.BALLISTIC ? 60 : 40, m.kind() == Missile.Kind.BALLISTIC ? 5f : 3f)) {
                passed = true;
                duck = 140;
            }
        }
        if (duck > 0) {
            duck--;
        }
        volume = duck > 0 ? loud * 0.2f : loud;
        pitch = base * (0.9f + (float) Math.min(0.3, m.getDeltaMovement().length() * 0.05));
    }
}
