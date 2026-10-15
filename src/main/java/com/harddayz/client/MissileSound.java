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

    private static SoundEvent pick(Missile m) {
        if (m.kind() == Missile.Kind.SAM || m.look() == 5) {
            return ModSounds.FLY_JET.get();
        }
        if (m.kind() == Missile.Kind.BALLISTIC && m.look() == 0) {
            return ModSounds.FLY_MISSILE.get();
        }
        return ModSounds.FLY_ROCKET.get();
    }

    public static boolean wants(Missile m) {
        return m.kind() != Missile.Kind.BOMB || m.look() == 6; // гранаты молчат, выстрел РПГ шипит
    }

    public MissileSound(Missile m) {
        super(pick(m), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.m = m;
        this.looping = true;
        this.delay = 0;
        this.base = switch (m.kind()) {
            case BALLISTIC -> m.look() == 0 ? 0.7f : 1.05f;
            case SAM -> 1.35f;
            default -> m.look() == 7 ? 1.4f : 1f;
        };
        this.volume = m.kind() == Missile.Kind.BALLISTIC ? 5f : 2.5f;
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
        pitch = base * (0.9f + (float) Math.min(0.3, m.getDeltaMovement().length() * 0.05));
    }
}
