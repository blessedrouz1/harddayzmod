package com.harddayz.client;

import com.harddayz.entity.Launcher;
import com.harddayz.registry.ModSounds;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Мотор техники: дизель (грузовики, БМП, Т-72), турбина (Abrams), винты (вертолёты). Громче и выше на ходу. */
public class VehicleSound extends AbstractTickableSoundInstance {
    private final Launcher v;
    private final float basePitch;

    public VehicleSound(Launcher v) {
        super(v.kind() == Launcher.Kind.ABRAMS ? ModSounds.TURBINE_LOOP.get() : v.kind().heli() ? ModSounds.HELI_LOOP.get() : ModSounds.DIESEL_LOOP.get(),
                SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.v = v;
        this.looping = true;
        this.delay = 0;
        this.volume = 0;
        this.basePitch = switch (v.kind()) {
            case PICKUP_MG, PICKUP_SAM -> 1.45f;
            case BMP2, BRADLEY -> 1.0f;
            case T72, TOS -> 0.85f;
            case ABRAMS -> 1.0f;
            case KA52 -> 1.05f;
            case MI8 -> 0.9f;
            default -> 1.15f;
        };
        this.pitch = basePitch;
        this.x = v.getX();
        this.y = v.getY();
        this.z = v.getZ();
    }

    public static boolean wants(Launcher l) {
        return l.kind().mobile && !l.kind().samLike() && l.kind() != Launcher.Kind.EW;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (v.isRemoved()) {
            stop();
            return;
        }
        x = v.getX();
        y = v.getY();
        z = v.getZ();
        boolean on = !v.getPassengers().isEmpty() || (v.kind().heli() && !v.onGround());
        double sp = v.getDeltaMovement().horizontalDistance();
        float target = on ? (v.kind().heli() ? 3.5f : 1.4f + (float) Math.min(1.6, sp * 4)) : 0;
        volume += (target - volume) * 0.1f;
        pitch = basePitch * (0.85f + (float) Math.min(0.4, sp * 0.8));
        // лязг гусениц
        if (on && sp > 0.05 && v.tickCount % Math.max(3, (int) (8 - sp * 12)) == 0
                && (v.kind().tank() || v.kind() == Launcher.Kind.BMP2 || v.kind() == Launcher.Kind.BRADLEY || v.kind() == Launcher.Kind.TOS)) {
            v.level().playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.CHAIN_STEP, SoundSource.NEUTRAL, 0.8f, 0.5f + v.getRandom().nextFloat() * 0.2f,
                    false);
        }
    }
}
