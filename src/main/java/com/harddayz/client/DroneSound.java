package com.harddayz.client;

import com.harddayz.entity.Drone;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Мотор дрона: у «Шахеда» — низкое «мопедное» жужжание, слышное издалека; у квадрокоптеров — высокий писк. */
public class DroneSound extends AbstractTickableSoundInstance {
    private final Drone drone;
    private float basePitch = 1f;

    public DroneSound(Drone drone) {
        super((drone.kind() == Drone.Kind.SHAHED_238 || drone.kind() == Drone.Kind.GERAN5 || drone.kind() == Drone.Kind.BANDEROL || drone.kind() == Drone.Kind.CRUISE) ? com.harddayz.registry.ModSounds.JET_LOOP.get()
                : drone.kind().autonomous ? com.harddayz.registry.ModSounds.ENGINE_LOOP.get() : com.harddayz.registry.ModSounds.FPV_LOOP.get(),
                SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        boolean shahed = drone.kind().autonomous;
        boolean jet = drone.kind() == Drone.Kind.SHAHED_238 || drone.kind() == Drone.Kind.GERAN5 || drone.kind() == Drone.Kind.BANDEROL;
        this.volume = 0;
        this.pitch = jet ? 1.0f : shahed ? (drone.kind() == Drone.Kind.SHAHED_131 ? 1.25f : drone.kind() == Drone.Kind.GERBERA ? 1.15f : 1.0f) : 1.0f;
        this.basePitch = this.pitch;
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (drone.isRemoved()) {
            stop();
            return;
        }
        x = drone.getX();
        y = drone.getY();
        z = drone.getZ();
        // заглушённый мотор не слышно; заведённый жужжит и без пилота
        volume = !drone.engineOn() ? 0 : drone.kind().autonomous ? 6.0f : 2.0f;
        if (!drone.kind().autonomous) {
            pitch = 0.9f + (float) Math.min(0.5, drone.getDeltaMovement().length() * 0.4);
        } else {
            // допплер: на подлёте тон выше, после пролёта резко падает
            var me = net.minecraft.client.Minecraft.getInstance().player;
            if (me != null) {
                var v = drone.position().subtract(drone.xo, drone.yo, drone.zo).scale(20);
                var to = me.getEyePosition().subtract(drone.position());
                double len = to.length();
                double toward = len < 0.1 ? 0 : v.dot(to) / len;
                pitch = basePitch * (float) net.minecraft.util.Mth.clamp(343.0 / (343.0 - toward), 0.7, 1.4);
            }
        }
    }
}
