package com.harddayz.client;

import com.harddayz.entity.Drone;

import net.minecraft.sounds.SoundSource;

/** Мотор дрона: у «Шахеда» — низкое «мопедное» жужжание, слышное издалека; у квадрокоптеров — высокий писк. */
public class DroneSound extends RangeSound {
    private final Drone drone;
    private boolean passed;
    private int duck;
    private float duckK = 1;
    /** Обороты мотора: плавно заводится и глохнет. */
    private float power;
    private float basePitch = 1f;

    public DroneSound(Drone drone) {
        super(drone.kind().jetDrone() ? com.harddayz.registry.ModSounds.FLY_JETDRONE.get()
                : drone.kind().jet() ? com.harddayz.registry.ModSounds.JET_LOOP.get()
                : drone.kind().shahedLike() ? com.harddayz.registry.ModSounds.ENGINE_LOOP.get() : com.harddayz.registry.ModSounds.FPV_LOOP.get(),
                SoundSource.HOSTILE, drone.kind().jet() ? 300 : drone.kind().autonomous ? 230 : 80);
        this.drone = drone;
        this.looping = true;
        boolean shahed = drone.kind().autonomous;
        boolean jet = drone.kind() == Drone.Kind.SHAHED_238 || drone.kind() == Drone.Kind.GERAN5 || drone.kind() == Drone.Kind.BANDEROL;
        this.pitch = jet ? 1.0f : shahed ? (drone.kind() == Drone.Kind.SHAHED_131 ? 1.25f : drone.kind() == Drone.Kind.GERBERA ? 1.15f : 1.0f) : 1.0f;
        this.basePitch = this.pitch;
        this.x = drone.getX();
        this.y = drone.getY();
        this.z = drone.getZ();
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
        if (!passed && drone.engineOn() && drone.kind().autonomous) {
            var k = drone.kind();
            var s = k.jetDrone() ? com.harddayz.registry.ModSounds.FLYBY_JETDRONE.get()
                    : k.jet() ? com.harddayz.registry.ModSounds.FLYBY_CRUISE.get() : k.shahedLike() ? com.harddayz.registry.ModSounds.FLYBY_SHAHED.get()
                    : com.harddayz.registry.ModSounds.FLYBY_DRONE.get();
            if (Flyby.check(drone, s, k.jet() ? 70 : 50, 1f)) {
                passed = true;
                duck = 140;
            }
        }
        if (duck > 0 && --duck == 0) {
            passed = false; // зайдёт на второй круг — пролёт прозвучит снова
        }
        duckK += ((duck > 40 ? 0.2f : 1f) - duckK) * 0.2f;
        power += ((drone.engineOn() ? 1f : 0f) - power) * 0.25f;
        volume = power * gainHere() * duckK;
        if (!drone.kind().autonomous) {
            pitch = 0.9f + (float) Math.min(0.5, drone.getDeltaMovement().length() * 0.4);
        } else {
            // допплер: на подлёте тон выше, после пролёта резко падает
            var v = drone.position().subtract(drone.xo, drone.yo, drone.zo).scale(20);
            var to = ears().subtract(drone.position());
            double len = to.length();
            double toward = len < 5 ? 0 : v.dot(to) / len; // на самом дроне допплера нет
            pitch = basePitch * (float) net.minecraft.util.Mth.clamp(343.0 / (343.0 - toward), 0.7, 1.4);
        }
    }
}
