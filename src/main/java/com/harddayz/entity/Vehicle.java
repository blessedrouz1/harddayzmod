package com.harddayz.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Колёсная техника: на ней можно ездить (водитель управляет со своего клиента, как лодкой),
 * плавные анимации (сервер присылает только цели, клиент сам плавно доводит детали),
 * вращение колёс и поворот передних колёс, выдвижные опоры.
 */
public abstract class Vehicle extends Entity {
    protected static final EntityDataAccessor<Float> HEALTH = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);
    /** Цель выдвижения опор 0..1 (во время пуска машина стоит на домкратах). */
    protected static final EntityDataAccessor<Float> OUTRIG = SynchedEntityData.defineId(Vehicle.class, EntityDataSerializers.FLOAT);

    protected @Nullable UUID owner;

    /** Скорость вдоль кузова (блоков/тик), считает тот, кто управляет движением. */
    protected double speed;
    /** Плавные детали: текущее значение, скорость изменения, прошлый тик. */
    protected final Smooth outrig = new Smooth(0.02f, 0.002f);

    // клиентская анимация колёс
    public float wheelSpin;
    private boolean engineSound;
    public float prevWheelSpin;
    public float steer;
    public float prevSteer;
    private Vec3 lastPos = Vec3.ZERO;
    private float lastYaw;
    private double movedServer;

    protected Vehicle(EntityType<? extends Vehicle> type, Level level) {
        super(type, level);
    }

    /** Плавное значение с разгоном и торможением (не рывками). */
    public static final class Smooth {
        public float value;
        public float prev;
        private float vel;
        private final float maxSpeed;
        private final float accel;

        public Smooth(float maxSpeed, float accel) {
            this.maxSpeed = maxSpeed;
            this.accel = accel;
        }

        public void tick(float target, boolean angle) {
            prev = value;
            float diff = angle ? Mth.wrapDegrees(target - value) : target - value;
            // желаемая скорость: пропорциональна расстоянию, но с ограничением, чтобы плавно тормозить у цели
            float desired = Mth.clamp(diff * 0.18f, -maxSpeed, maxSpeed);
            vel += Mth.clamp(desired - vel, -accel, accel);
            if (Math.abs(diff) < accel * 2 && Math.abs(vel) < accel * 2) {
                value = angle ? value + diff : target;
                vel = 0;
            } else {
                value += vel;
            }
        }

        public float get(float pt, boolean angle) {
            return angle ? Mth.rotLerp(pt, prev, value) : Mth.lerp(pt, prev, value);
        }

        public boolean near(float target, float eps, boolean angle) {
            float d = angle ? Mth.wrapDegrees(target - value) : target - value;
            return Math.abs(d) < eps && Math.abs(vel) < eps;
        }
    }

    // ------------------------------------------------------------------ свойства машины

    public abstract float maxHealth();

    /** Место водителя: (вправо, вверх, вперёд) от центра машины. */
    protected abstract Vec3 seat();

    protected double maxSpeed() {
        return 0.55;
    }

    /** Разгон за тик: тяжёлые машины набирают скорость медленно. */
    protected double accel() {
        return 0.012;
    }

    public float wheelRadius() {
        return 0.6f;
    }

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> FACTION = net.minecraft.network.syncher.SynchedEntityData
            .defineId(Vehicle.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** Фракция хозяина (для опознавательных знаков), -1 — без фракции. */
    public int faction() {
        return entityData.get(FACTION);
    }

    public float health() {
        return entityData.get(HEALTH);
    }

    public void setOwner(Player p) {
        owner = p.getUUID();
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public float outrig(float pt) {
        return outrig.get(pt, false);
    }

    /** Можно ли сейчас ехать (не во время пуска). */
    protected boolean canDrive() {
        return entityData.get(OUTRIG) <= 0 && outrig.value < 0.02f;
    }

    /** Машина стоит (для приказа на пуск). */
    public boolean stationary() {
        return movedServer < 0.03;
    }

    // ------------------------------------------------------------------ езда

    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < seats();
    }

    /** Мест в машине. */
    protected int seats() {
        return 1;
    }

    /** Место пассажира с номером i (локальные координаты), по умолчанию — водительское. */
    protected Vec3 seat(int i) {
        return seat();
    }

    @Override
    public float maxUpStep() {
        return 1.1f;
    }

    /** Плавное движение машины у других игроков (позиции с сервера). */
    private final com.harddayz.Compat.Lerp lerp = new com.harddayz.Compat.Lerp(3);

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerp.set(x, y, z, yRot, xRot);
    }

    /** Точка посадки пассажира (как getPassengerRidingPosition в новых версиях): ноги игрока на 0.6 ниже точки места. */
    public Vec3 seatPosition(Entity passenger) {
        Vec3 s = seat(Math.max(0, getPassengers().indexOf(passenger)));
        return local(s.x, s.y, s.z);
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        Vec3 p = seatPosition(passenger);
        move.accept(passenger, p.x, p.y - (passenger instanceof Player ? 0.6 : 0) + (passenger instanceof Player ? 0 : passenger.getMyRidingOffset()), p.z);
        float dy = Mth.wrapDegrees(getYRot() - yRotO);
        passenger.setYRot(passenger.getYRot() + dy);
        passenger.setYHeadRot(passenger.getYHeadRot() + dy);
    }

    protected Vec3 local(double right, double up, double fwd) {
        double y = Math.toRadians(getYRot());
        Vec3 f = new Vec3(-Math.sin(y), 0, Math.cos(y));
        Vec3 r = new Vec3(-Math.cos(y), 0, -Math.sin(y));
        return position().add(f.scale(fwd)).add(r.scale(right)).add(0, up, 0);
    }

    @Override
    public void tick() {
        super.tick();
        outrig.tick(entityData.get(OUTRIG), false);
        if (isControlledByLocalInstance()) {
            drive();
        } else if (level().isClientSide()) {
            lerp.tick(this);
        }
        if (level().isClientSide()) {
            if (!engineSound && this instanceof Launcher l && com.harddayz.client.VehicleSound.wants(l)) {
                engineSound = true;
                com.harddayz.client.ClientAccess.vehicleSound(l);
            }
            damageFx();
            animateWheels();
        } else {
            if (tickCount % 40 == 1) {
                entityData.set(FACTION, com.harddayz.war.War.factionIndex(owner));
            }
            movedServer = position().subtract(lastPos).horizontalDistance();
            lastPos = position();
        }
    }

    protected void drive() {
        float fwd = 0;
        float turn = 0;
        boolean brake = false;
        LivingEntity driver = getControllingPassenger();
        if (driver != null && level().isClientSide() && canDrive() && !(this instanceof Launcher l && l.kind().gunner() && !l.crewed())) {
            float[] in = com.harddayz.client.ClientAccess.driveInput();
            fwd = in[0];
            turn = in[1];
            brake = in[2] > 0;
        }
        double max = maxSpeed();
        if (fwd > 0) {
            speed = Math.min(max, speed + accel());
        } else if (fwd < 0) {
            speed = Math.max(-max * 0.4, speed - (speed > 0 ? 0.03 : 0.01));
        } else {
            speed *= 0.96;
        }
        if (brake) {
            speed *= 0.82;
        }
        if (Math.abs(speed) < 0.002) {
            speed = 0;
        }
        if (turn != 0 && Math.abs(speed) > 0.01) {
            float rate = (float) (2.6 * Math.min(1, Math.abs(speed) / 0.2)) * Math.signum((float) speed);
            setYRot(getYRot() - turn * rate);
        }
        double y = Math.toRadians(getYRot());
        Vec3 v = new Vec3(-Math.sin(y) * speed, getDeltaMovement().y, Math.cos(y) * speed);
        v = onGround() ? new Vec3(v.x, Math.max(0, v.y) - 0.04, v.z) : v.add(0, -0.08, 0);
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        if (horizontalCollision) {
            speed *= 0.4;
        }
        if (Math.abs(speed) > 0.05 && tickCount % 6 == 0) {
            level().playSound(driver instanceof Player p ? p : null, getX(), getY(), getZ(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL,
                    0.15f, 0.4f + (float) Math.abs(speed));
        }
    }

    /** Подбитая техника: ниже 60% — дым, ниже 30% — огонь. */
    private void damageFx() {
        float k = health() / Math.max(1, maxHealth());
        if (k > 0.6f || random.nextFloat() > 0.6f) {
            return;
        }
        Vec3 c = position().add((random.nextDouble() - 0.5) * 2, 2.0, (random.nextDouble() - 0.5) * 2);
        level().addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.SMOKE.get(), true, c.x, c.y, c.z, 0, 0.06, 0);
        if (k < 0.3f) {
            level().addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.FIRE.get(), true, c.x, c.y - 0.5, c.z, 0, 0.04, 0);
        }
    }

    private void animateWheels() {
        prevWheelSpin = wheelSpin;
        prevSteer = steer;
        Vec3 d = position().subtract(lastPos);
        double y = Math.toRadians(getYRot());
        double along = d.x * -Math.sin(y) + d.z * Math.cos(y);
        if (d.lengthSqr() < 4) {
            wheelSpin += (float) (along / wheelRadius());
        }
        float yawDelta = Mth.wrapDegrees(getYRot() - lastYaw);
        float targetSteer = Math.abs(along) > 0.005 ? Mth.clamp(-yawDelta * 9 * Math.signum((float) along), -30, 30) : steer * 0.9f;
        steer += (targetSteer - steer) * 0.25f;
        lastPos = position();
        lastYaw = getYRot();
    }

    // ------------------------------------------------------------------ служебное

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FACTION, -1);
        entityData.define(HEALTH, 100f);
        entityData.define(OUTRIG, 0f);
    }
}
