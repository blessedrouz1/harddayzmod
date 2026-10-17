package com.harddayz.entity;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.harddayz.weapon.Blast;
import com.harddayz.registry.ModEntities;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ракета. Три типа:
 * <ul>
 * <li>GRAD — неуправляемая реактивная, летит по баллистической дуге;</li>
 * <li>BALLISTIC — тактическая баллистическая: вертикальный старт, набор высоты, пикирование на цель;</li>
 * <li>SAM — зенитная: самонаведение на летящую цель, неконтактный подрыв рядом с ней.</li>
 * </ul>
 * Чанки по пути полёта подгружаются принудительно, поэтому ракета долетает и до далёкой цели.
 */
public class Missile extends Entity implements Threat {
    public enum Kind {
        GRAD(4.2f, 0.05),
        BALLISTIC(16f, 0),
        SAM(3f, 0),
        /** Граната, сброшенная с разведдрона. */
        BOMB(2.6f, 0.06);

        public final float power;
        public final double gravity;

        Kind(float power, double gravity) {
            this.power = power;
            this.gravity = gravity;
        }
    }

    public static final double GRAD_SPEED = 6.3;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Missile.class, EntityDataSerializers.INT);
    /** Как выглядит в полёте: 0 — по виду ракеты, 1 GMLRS, 2 С-400, 3 Patriot, 4 NASAMS, 5 Javelin, 6 выстрел РПГ, 9 ракета поста ПВО, 10 ракета ручного/пикапного комплекса. */
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(Missile.class, EntityDataSerializers.INT);

    public Missile look(int v) {
        entityData.set(LOOK, v);
        return this;
    }

    public boolean isThermo() {
        return thermo;
    }

    public int look() {
        return entityData.get(LOOK);
    }

    private Vec3 target = Vec3.ZERO;
    private int targetEntity = -1;
    private int launcherId = -1;
    private boolean diving;
    private @Nullable FlightChunks chunks;

    public Missile(EntityType<? extends Missile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static Missile launch(ServerLevel level, Kind kind, Vec3 pos, Vec3 velocity, Vec3 target, @Nullable Entity launcher) {
        Missile m = new Missile(ModEntities.MISSILE.get(), level);
        m.entityData.set(KIND, kind.ordinal());
        m.setPos(pos);
        m.origin = pos;
        m.setDeltaMovement(velocity);
        m.target = target;
        m.launcherId = launcher == null ? -1 : launcher.getId();
        if (launcher instanceof Launcher l) {
            // A vertical velocity has no yaw; retain the orientation of its rail.
            m.setYRot(l.getYRot() + (l.kind() == Launcher.Kind.ISKANDER || l.kind() == Launcher.Kind.TOCHKA ? 0 : l.turret(1)));
        }
        m.face(velocity);
        m.yRotO = m.getYRot();
        m.xRotO = m.getXRot();
        level.addFreshEntity(m);
        return m;
    }

    private @javax.annotation.Nullable java.util.UUID owner;

    public Missile owner(@javax.annotation.Nullable java.util.UUID uuid) {
        owner = uuid;
        return this;
    }

    @Override
    public @javax.annotation.Nullable java.util.UUID owner() {
        return owner;
    }

    /** Прибавка к шансу поражения (ПРО по баллистике, ПТРК по технике). */
    private float bonus;

    public Missile bonus(float k) {
        bonus = k;
        return this;
    }

    public Missile hunt(Entity e) {
        targetEntity = e.getId();
        return this;
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    /** Цель для ПВО: всё, что летит не от неё самой. */
    @Override
    public boolean isThreat() {
        return kind() != Kind.SAM && kind() != Kind.BOMB && look() != 8;
    }

    private @Nullable Vec3 origin;

    @Override
    public @Nullable Vec3 origin() {
        return origin;
    }

    @Override
    public String launchType() {
        if (look() == 7) {
            return "mortar";
        }
        return kind() == Kind.BALLISTIC ? "ballistic" : kind() == Kind.GRAD ? "mlrs" : "missile";
    }

    private final com.harddayz.Compat.Lerp lerp = new com.harddayz.Compat.Lerp(2);

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerp.set(x, y, z, yRot, xRot);
    }

    private void face(Vec3 v) {
        if (v.lengthSqr() < 1.0E-6) {
            return;
        }
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h > 1.0E-6) {
            setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        }
        setXRot((float) Math.toDegrees(-Math.atan2(v.y, h)));
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (level() instanceof ServerLevel level) {
            v = guide(level, v);
            setDeltaMovement(v);
            if (look() == 8 && flare(level, v)) {
                return;
            }
            if (tickCount > 4000 || getY() < level.getMinBuildHeight() - 20) {
                detonate(level, position());
                return;
            }
            {
                if (chunks == null) {
                    chunks = new FlightChunks(getUUID());
                }
                if (!chunks.update(level, position(), v, target)) {
                    return; // впереди чанк ещё грузится — ждём тик
                }
            }
            if (collide(level, v)) {
                return;
            }
            if (tickCount == 1 && kind() != Kind.BOMB && look() != 7 && look() != 5 && look() != 6) { // мина, РПГ и Javelin звучат сами
                level.playSound(null, getX(), getY(), getZ(), switch (kind()) {
                            case GRAD -> com.harddayz.registry.ModSounds.ROCKET_LAUNCH.get();
                            case SAM -> com.harddayz.registry.ModSounds.SAM_LAUNCH.get();
                            default -> com.harddayz.registry.ModSounds.MISSILE_LAUNCH.get();
                        }, SoundSource.HOSTILE, kind() == Kind.BALLISTIC ? 8f : 4f, 0.9f + random.nextFloat() * 0.2f);
            }
            setPos(position().add(v));
            face(v);
        } else {
            lerp.tick(this);
            trail(v);
            whistle(v);
            if (!flySound && tickCount > 1) {
                flySound = true;
                com.harddayz.client.ClientAccess.missileSound(this);
            }
        }
    }

    // ------------------------------------------------------------------ наведение

    private Vec3 guide(ServerLevel level, Vec3 v) {
        return switch (kind()) {
            case GRAD -> grad(level, v);
            case BOMB -> v.add(0, -Kind.BOMB.gravity, 0).scale(0.99);
            case BALLISTIC -> ballistic(level, v);
            case SAM -> sam(level, v);
        };
    }

    /** РСЗО выровнялась под потолком и летит горизонтально. */
    private boolean leveled;

    /**
     * Неуправляемая ракета РСЗО: обычная баллистическая дуга, но не выше потолка — там ракета идёт горизонтально и
     * снова начинает падать, когда дуга с этой высоты как раз приведёт её в точку цели. Мина летит чистой дугой.
     */
    private Vec3 grad(ServerLevel level, Vec3 v) {
        double g = Kind.GRAD.gravity;
        if (look() == 7 || look() == 8 || target == null) {
            return v.add(0, -g, 0);
        }
        double cap = Math.min(level.getMaxBuildHeight() - 16, (origin != null ? origin.y : getY()) + 150);
        if (!leveled && v.y > 0 && getY() + v.y >= cap) {
            leveled = true;
            setPos(getX(), cap, getZ());
            return new Vec3(v.x, 0, v.z);
        }
        if (leveled) {
            double h = Math.max(0, getY() - target.y);
            double u = Math.sqrt(v.x * v.x + v.z * v.z);
            double drop = u * Math.sqrt(2 * h / g);
            double dx = target.x - getX(), dz = target.z - getZ();
            if (dx * dx + dz * dz > drop * drop) {
                return new Vec3(v.x, 0, v.z); // ещё рано падать
            }
            leveled = false;
        }
        return v.add(0, -g, 0);
    }

    private Vec3 ballistic(ServerLevel level, Vec3 v) {
        Vec3 pos = position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double dh = Math.sqrt(dx * dx + dz * dz);
        // высокая дуга: минимум — почти под потолок мира, на дальние цели ещё выше
        double ceiling = level.getMaxBuildHeight() - 12;
        double apex = ceiling; // не выше потолка мира: дальняя цель — длиннее марш на высоте, а не выше дуга
        if (tickCount < 40 || (!diving && tickCount < 260 && pos.y < Math.min(apex, ceiling) - 30)) {
            double speed = Math.min(3.2, 0.4 + tickCount * 0.08);
            // Clear the rail along its actual axis before gradually pitching up.
            if (tickCount <= 4) {
                return v.normalize().scale(speed);
            }
            return steer(v, new Vec3(0, 1, 0), speed, 0.05);
        }
        Vec3 desired;
        double speed;
        double turn;
        if (!diving && dh > (pos.y - target.y) * 0.9 + 20) {
            // марш на высоте: к точке над целью
            Vec3 way = new Vec3(target.x, apex, target.z);
            desired = way.subtract(pos).normalize();
            speed = Math.min(4.2, v.length() + 0.08);
            turn = 0.05;
        } else {
            diving = true;
            Vec3 aim = groundAim(level);
            double hx = aim.x - pos.x;
            double hz = aim.z - pos.z;
            if (hx * hx + hz * hz < 36) {
                // над целью: просто вниз до удара о землю (высота в координатах — лишь ориентир)
                return v.normalize().scale(Math.min(6.0, v.length() + 0.15));
            }
            desired = aim.subtract(pos).normalize();
            speed = Math.min(6.0, v.length() + 0.15);
            turn = 0.14;
        }
        return steer(v, desired, speed, turn);
    }

    private @javax.annotation.Nullable Vec3 ground;

    /** Точка цели на реальной поверхности (по карте высот), а не на введённой высоте. */
    private Vec3 groundAim(ServerLevel level) {
        if (ground == null) {
            int gx = Mth.floor(target.x);
            int gz = Mth.floor(target.z);
            if (level.hasChunk(gx >> 4, gz >> 4)) {
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, gx, gz);
                ground = new Vec3(target.x, gy, target.z);
            } else {
                return target;
            }
        }
        return ground;
    }

    private Vec3 sam(ServerLevel level, Vec3 v) {
        if (tickCount > 160) {
            detonate(level, position());
            return v;
        }
        Entity t = targetEntity >= 0 ? level.getEntity(targetEntity) : null;
        if (tickCount < (look() == 2 ? 14 : 6)) {
            // С-400 сначала уходит вертикально вверх (холодный старт), потом доворачивает на цель
            return v.normalize().scale(Math.min(4.5, v.length() + 0.4));
        }
        if (t == null || !t.isAlive()) {
            return v.normalize().scale(Math.min(4.5, v.length() + 0.2));
        }
        Vec3 aim = t.position();
        double dist = aim.distanceTo(position());
        Vec3 rel = aim.subtract(position());
        Vec3 relV = v.subtract(t.getDeltaMovement());
        double s = relV.lengthSqr() < 1.0E-6 ? 0 : Mth.clamp(rel.dot(relV) / relV.lengthSqr(), 0, 1);
        double miss = rel.subtract(relV.scale(s)).length();
        if (dist < 5.0 || miss < 4.5) {
            Vec3 at = position().add(v.scale(s));
            setPos(at);
            if (random.nextFloat() < Math.min(0.92f, (hitChance(t) + bonus) * 0.95f)) {
                if (t instanceof Missile m) {
                    m.intercepted(level);
                } else if (t instanceof Drone d) {
                    d.shotDown(level);
                } else {
                    t.hurt(damageSources().explosion(this, null), 30);
                }
            } else if (t instanceof Drone d) {
                d.hurt(damageSources().explosion(this, null), 2); // промах: лишь задело осколками
            }
            detonate(level, at);
            return v;
        }
        double speed = Math.min(4.5, v.length() + 0.25);
        Vec3 lead = intercept(position(), speed, t.position(), t.getDeltaMovement());
        return steer(v, lead.subtract(position()).normalize(), speed, 0.3);
    }

    /**
     * Точка встречи: куда лететь со скоростью {@code speed}, чтобы поймать цель, идущую прямо со скоростью {@code tv}.
     * Ракета летит на перехват наперёд траектории, а не догоняет хвост.
     */
    public static Vec3 intercept(Vec3 from, double speed, Vec3 tp, Vec3 tv) {
        Vec3 r = tp.subtract(from);
        double a = tv.lengthSqr() - speed * speed;
        double b = 2 * r.dot(tv);
        double c = r.lengthSqr();
        double tau;
        if (Math.abs(a) < 1.0E-6) {
            tau = b < 0 ? -c / b : r.length() / speed;
        } else {
            double disc = b * b - 4 * a * c;
            if (disc < 0) {
                return tp.add(tv.scale(r.length() / speed)); // не догнать — хотя бы упреждение
            }
            double s = Math.sqrt(disc);
            double t1 = (-b - s) / (2 * a);
            double t2 = (-b + s) / (2 * a);
            tau = t1 > 0 && t2 > 0 ? Math.min(t1, t2) : Math.max(t1, t2);
        }
        if (!(tau > 0) || tau > 200) {
            tau = r.length() / speed;
        }
        return tp.add(tv.scale(tau));
    }

    /** Вероятность поразить цель ракетой ПВО: медленное сбить легко, быстрое — трудно. */
    public static float hitChance(Entity t) {
        if (t instanceof Drone d) {
            return switch (d.kind()) {
                case SHAHED, SHAHED_131 -> 0.95f;
                case GERBERA -> 0.97f;
                case SHAHED_238 -> 0.55f;
                case GERAN5 -> 0.4f;
                case CRUISE -> 0.45f;
                case KUB, MOLNIYA -> 0.8f;
                case BABA_YAGA -> 0.85f;
                case BANDEROL -> 0.5f;
                case RECON, WING_RECON -> 0.9f;
                case INTERCEPTOR -> 0.5f;
                case LANCET -> 0.85f;
                case FPV, FPV_FIBER -> 0.8f;
            };
        }
        if (t instanceof Missile m) {
            return switch (m.kind()) {
                case GRAD -> 0.6f;
                case BALLISTIC -> 0.3f;
                default -> 0.7f;
            };
        }
        return 0.8f;
    }

    /** Повернуть скорость к нужному направлению не больше чем на {@code maxTurn} радиан за тик. */
    private static Vec3 steer(Vec3 v, Vec3 desired, double speed, double maxTurn) {
        Vec3 cur = v.lengthSqr() < 1.0E-6 ? desired : v.normalize();
        double cos = Mth.clamp(cur.dot(desired), -1, 1);
        double angle = Math.acos(cos);
        Vec3 dir;
        if (angle <= maxTurn || angle < 1.0E-4) {
            dir = desired;
        } else {
            double t = maxTurn / angle;
            dir = cur.scale(1 - t).add(desired.scale(t)).normalize();
        }
        return dir.scale(speed);
    }

    // ------------------------------------------------------------------ попадание

    private boolean collide(ServerLevel level, Vec3 v) {
        Vec3 from = position();
        Vec3 to = from.add(v);
        if (fuse > 0) {
            if (tickCount >= fuse) {
                detonate(level, from);
                return true;
            }
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                var n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                Vec3 r = v.subtract(n.scale(2 * v.dot(n))).scale(0.35); // отскок с потерей скорости
                setDeltaMovement(r.lengthSqr() < 0.004 ? Vec3.ZERO : r);
                setPos(hit.getLocation().add(n.scale(0.05)));
                if (v.length() > 0.2) {
                    level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.CHAIN_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
                }
                return true;
            }
            return false;
        }
        if (kind() != Kind.SAM) {
            if (tickCount > 3) {
                BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
                if (hit.getType() != HitResult.Type.MISS) {
                    detonate(level, hit.getLocation());
                    return true;
                }
            }
            if (tickCount > 8) {
                AABB box = new AABB(from, to).inflate(1.0);
                for (Entity e : level.getEntities(this, box, e -> (e instanceof LivingEntity || e instanceof Launcher || e instanceof Drone d && !d.isParked()) && e.getId() != launcherId)) {
                    var clip = e.getBoundingBox().inflate(0.4).clip(from, to);
                    if (clip.isPresent()) {
                        detonate(level, clip.get());
                        return true;
                    }
                }
            }
        } else if (tickCount > 3) {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                detonate(level, hit.getLocation());
                return true;
            }
        }
        return false;
    }

    /** Сбита ПВО: подрыв в воздухе без вреда блокам. */
    public void intercepted(ServerLevel level) {
        Blast.detonate(level, position(), kind().power * 0.35f, false, this);
        discardSafely(level);
    }

    /** Термобарическая (ТОС-1А): взрыв мощнее и поджигает всё вокруг. */
    private boolean thermo;
    private float powerMul = 1f;
    /** Дымовая граната: вместо взрыва — завеса. */
    private boolean smoke;
    /** Запал (тики): граната отскакивает и рвётся по времени. */
    private int fuse;

    public Missile fuse(int ticks) {
        fuse = ticks;
        return this;
    }

    public Missile smoke() {
        smoke = true;
        return this;
    }

    public Missile powerMul(float k) {
        powerMul = k;
        return this;
    }

    public Missile thermobaric() {
        thermo = true;
        return this;
    }

    // ------------------------------------------------------------------ осветительная мина

    private boolean flareLit;
    private int flareTicks;
    private final java.util.List<net.minecraft.core.BlockPos> lights = new java.util.ArrayList<>();

    /** На вершине дуги раскрывается парашют: факел медленно опускается ~35 с и освещает землю под собой. */
    private boolean flare(ServerLevel level, Vec3 v) {
        if (!flareLit) {
            if (tickCount < 8 || v.y > 0.05) {
                return false; // ещё летит вверх обычной миной
            }
            flareLit = true;
            level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST, net.minecraft.sounds.SoundSource.HOSTILE, 4f, 0.7f);
        }
        Vec3 drift = new Vec3(v.x * 0.92, -0.055, v.z * 0.92);
        setDeltaMovement(drift);
        setPos(position().add(drift));
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
        if (++flareTicks > 700 || getY() <= ground + 0.5) {
            clearLights(level);
            discardSafely(level);
            return true;
        }
        if (flareTicks % 10 == 1) {
            clearLights(level);
            if (getY() - ground < 140) {
                int cx = getBlockX(), cz = getBlockZ();
                int lvl = flareTicks > 600 ? 9 : 15; // догорает — тускнеет
                for (int dx = -24; dx <= 24; dx += 12) {
                    for (int dz = -24; dz <= 24; dz += 12) {
                        if (dx * dx + dz * dz > 26 * 26) {
                            continue;
                        }
                        int x = cx + dx, z = cz + dz;
                        var p = new net.minecraft.core.BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z);
                        if (level.isLoaded(p) && level.getBlockState(p).isAir()) {
                            level.setBlock(p, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, lvl), 3);
                            lights.add(p);
                        }
                    }
                }
            }
        }
        return true;
    }

    private void clearLights(ServerLevel level) {
        for (var p : lights) {
            if (level.isLoaded(p) && level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        lights.clear();
    }

    private void detonate(ServerLevel level, Vec3 at) {
        if (look() == 8) {
            clearLights(level);
            discardSafely(level);
            return; // осветительная — без взрыва
        }
        if (smoke) {
            com.harddayz.Compat.particles(level, com.harddayz.registry.ModParticles.SMOKE_HUGE.get(), at.x, at.y + 1.5, at.z, 70, 3.5, 1.8, 3.5, 0.01);
            com.harddayz.Compat.particles(level, net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1, at.z, 60, 3, 1, 3, 0.01);
            level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.6f);
            discardSafely(level);
            return;
        }
        boolean blocks = kind() != Kind.SAM;
        Blast.detonate(level, at, kind().power * powerMul * (thermo ? 1.7f : 1f), blocks, this);
        if (thermo) {
            net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(at);
            for (int i = 0; i < 14; i++) {
                net.minecraft.core.BlockPos p = c.offset(random.nextInt(9) - 4, random.nextInt(4) - 1, random.nextInt(9) - 4);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) {
                    level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
                }
            }
            com.harddayz.Compat.particles(level, net.minecraft.core.particles.ParticleTypes.FLAME, at.x, at.y + 1, at.z, 120, 3, 1.5, 3, 0.15);
        }
        discardSafely(level);
    }

    private void discardSafely(ServerLevel level) {
        releaseChunks(level);
        discard();
    }

    // ------------------------------------------------------------------ чанки

    private void releaseChunks(ServerLevel level) {
        if (chunks != null) {
            chunks.release(level);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            clearLights(level);
            releaseChunks(level);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ след на клиенте

    private boolean whistled;
    /** Клиент: звук полёта уже запущен. */
    private boolean flySound;

    /** Свист снаряда на излёте — слышно, что сейчас прилетит. */
    private void whistle(Vec3 v) {
        if (whistled || kind() == Kind.SAM || kind() == Kind.BOMB || v.y > -0.2) {
            return;
        }
        var me = com.harddayz.client.ClientAccess.localPlayerPos();
        if (me == null) {
            return;
        }
        boolean heavy = kind() == Kind.BALLISTIC;
        var sound = heavy ? com.harddayz.registry.ModSounds.INCOMING_BALLISTIC.get()
                : look() == 7 ? com.harddayz.registry.ModSounds.INCOMING_MORTAR.get()
                : com.harddayz.registry.ModSounds.INCOMING_ROCKET.get();
        double len = heavy ? 2.8 : look() == 7 ? 2.8 : 2.2;
        int top = level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        double ticks = (getY() - top) / -v.y;
        if (ticks > len * 20) {
            return;
        }
        Vec3 hit = new Vec3(getX() + v.x * ticks, top, getZ() + v.z * ticks);
        if (hit.distanceTo(me) > (heavy ? 260 : 140)) {
            return;
        }
        whistled = true;
        level().playLocalSound(hit.x, hit.y + 2, hit.z, sound, SoundSource.HOSTILE, heavy ? 7f : 3.5f, 1f, false);
    }

    private void trail(Vec3 v) {
        if (look() == 8) {
            if (v.y > -0.1 && v.y < 0) { // горит под парашютом
                Level lv = level();
                lv.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.GLOW.get(), true, getX(), getY(), getZ(), 0, 0, 0);
                lv.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.FLASH.get(), true, getX(), getY(), getZ(), 0, 0, 0);
                if (random.nextFloat() < 0.5f) {
                    lv.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.FIREWORK, true, getX(), getY() - 0.3, getZ(),
                            random.nextGaussian() * 0.03, -0.12, random.nextGaussian() * 0.03);
                }
                lv.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.SMOKE_WHITE.get(), true, getX(), getY() + 0.4, getZ(), 0, 0.02, 0);
            }
            return;
        }
        Level level = level();
        Vec3 dir = v.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : v.normalize();
        double len = switch (kind()) {
            case GRAD -> 1.4;
            case BALLISTIC -> 4.2;
            case SAM -> 1.8;
            case BOMB -> 0.3;
        };
        Vec3 tail = position().subtract(dir.scale(len));
        // падает с неба: яркая точка и светящийся след, видно издалека
        if (dir.y < -0.3 && kind() != Kind.BOMB && kind() != Kind.SAM) {
            Vec3 pos = position();
            level.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.GLOW.get(), true, pos.x, pos.y, pos.z, 0, 0, 0);
            Vec3 mid = pos.subtract(v.scale(0.5));
            level.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.GLOW.get(), true, mid.x, mid.y, mid.z, 0, 0, 0);
        }
        // факел у сопла: огонь и вспышка частицами
        if (kind() != Kind.BOMB) {
            int n = kind() == Kind.BALLISTIC ? 6 : 3;
            double spread = kind() == Kind.BALLISTIC ? 0.25 : 0.08;
            for (int i = 0; i < n; i++) {
                double k = random.nextDouble() * 0.8;
                level.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.FIRE.get(), true, tail.x - dir.x * k + random.nextGaussian() * spread,
                        tail.y - dir.y * k + random.nextGaussian() * spread, tail.z - dir.z * k + random.nextGaussian() * spread,
                        -dir.x * 0.25, -dir.y * 0.25, -dir.z * 0.25);
            }
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, tail.x, tail.y, tail.z, -dir.x * 0.3, -dir.y * 0.3, -dir.z * 0.3);
        }
        int steps = Math.max(2, (int) (v.length() * 2));
        var r = random;
        for (int i = 0; i < steps; i++) {
            Vec3 p = tail.subtract(v.scale(i / (double) steps));
            double s = 0.15;
            switch (kind()) {
                case GRAD -> {
                    level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z, r.nextGaussian() * 0.02, 0.02, r.nextGaussian() * 0.02);
                    if (i % 2 == 0) {
                        level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, p.x, p.y, p.z, -dir.x * 0.2, -dir.y * 0.2, -dir.z * 0.2);
                    }
                }
                case BALLISTIC -> {
                    if (i % 2 == 0) {
                        level.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.SMOKE_WHITE.get(), true, p.x + r.nextGaussian() * s, p.y,
                                p.z + r.nextGaussian() * s, r.nextGaussian() * 0.02, 0.01, r.nextGaussian() * 0.02);
                    }
                    level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, p.x, p.y, p.z, -dir.x * 0.4, -dir.y * 0.4, -dir.z * 0.4);
                }
                case BOMB -> {
                    if (i == 0) {
                        level.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, p.x, p.y, p.z, 0, 0, 0);
                    }
                }
                case SAM -> {
                    level.addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, p.x, p.y, p.z, 0, 0, 0);
                    if (i == 0) {
                        level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, p.x, p.y, p.z, -dir.x * 0.3, -dir.y * 0.3, -dir.z * 0.3);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ служебное

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(8);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 512 * 512;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
        entityData.define(LOOK, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag input) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (!(level() instanceof ServerLevel level)) {
            return false;
        }
        return false;
    }
}
