package com.harddayz.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.harddayz.registry.ModEntities;
import com.harddayz.registry.ModItems;
import com.harddayz.weapon.Blast;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Беспилотники.
 * <ul>
 * <li>SHAHED — ударный дрон-камикадзе: летит сам над рельефом к цели и пикирует; его слышно издалека и можно сбить;</li>
 * <li>FPV — пилотируемый с камеры дрон-камикадзе: подрыв по ЛКМ или при ударе на скорости;</li>
 * <li>RECON — разведывательный: камера с зумом, сбрасывает гранаты (ЛКМ).</li>
 * </ul>
 * Пилотируемыми дронами управляет клиент пилота (плавно, без задержки), сервер принимает позицию.
 */
public class Drone extends Entity implements Threat {
    public enum Kind {
        /** «Шахед-136» / «Герань-2»: медленный, громкий, мощный. */
        SHAHED(8, 3600, 7.5f, 1.25, 26, true),
        FPV(4, 3600, 4.5f, 0, 0, false),
        RECON(6, 9600, 0f, 0, 0, false),
        /** «Шахед-238»: реактивный — в 2 раза быстрее, летит высоко, ПВО сбивает хуже. */
        SHAHED_238(10, 2400, 8f, 2.6, 70, true),
        /** «Шахед-131»: малый, слабее заряд. */
        SHAHED_131(6, 3000, 4.5f, 1.15, 22, true),
        /** «Гербера»: ложная цель — почти без заряда, отвлекает ПВО. */
        GERBERA(4, 3000, 0.8f, 1.0, 24, true),
        /** Дрон-перехватчик: сам догоняет вражеский дрон и пытается его сбить (не наверняка). */
        INTERCEPTOR(3, 0, 2.2f, 2.6, 0, true),
        /** «Ланцет»: летит к точке сам, в конце оператор выбирает цель вручную. */
        LANCET(5, 3600, 5f, 1.6, 40, true),
        /** Самолётный разведчик: кружит над точкой и передаёт найденных врагов в сеть РЛС фракции. */
        WING_RECON(6, 0, 0f, 1.0, 60, true),
        /** «Герань-5»: реактивная, летит почти как крылатая ракета — быстро и высоко, ПВО её сбивает тяжело. */
        GERAN5(8, 1800, 9f, 3.6, 90, true),
        /** «Бандероль»: малая крылатая ракета, быстрая и низковысотная. */
        BANDEROL(6, 1800, 6f, 3.0, 35, true),
        /** «Куб»: малый барражирующий боеприпас — запуск с руки, сам летит к точке. */
        KUB(4, 1800, 3f, 2.0, 30, true),
        /** «Молния»: дешёвый самолётный камикадзе, только под оператором (как «Ланцет»). */
        MOLNIYA(3, 2400, 3.5f, 1.8, 30, true),
        /** «Баба-Яга»: тяжёлый гексакоптер-бомбардировщик, две мощные бомбы. */
        BABA_YAGA(10, 7200, 0f, 0, 0, false),
        /** Крылатая ракета «Калибр» (с пусковой): низко и далеко, мощная БЧ. */
        CRUISE(7, 0, 12f, 3.2, 25, true);

        public final float health;
        /** Заряд АКБ в тиках (у «Шахедов» — запас ручного управления, дальше летит сам). */
        public final int battery;
        public final float warhead;
        public final double cruise;
        public final int altitude;
        /** Летит сам к цели (семейство «Шахедов»). */
        public final boolean autonomous;

        Kind(float health, int battery, float warhead, double cruise, int altitude, boolean autonomous) {
            this.health = health;
            this.battery = battery;
            this.warhead = warhead;
            this.cruise = cruise;
            this.altitude = altitude;
            this.autonomous = autonomous;
        }
    }

    public static final int SHAHED_RANGE = 3000;
    public static final int SIGNAL_RANGE = 320;
    /** Дальность ручного управления «Шахедом» с пульта оператора. */
    public static final int SHAHED_LINK_RANGE = 700;
    /** Ретранслятор продлевает связь на столько блоков вокруг себя. */
    public static final int RELAY_RANGE = 900;

    /** Реальная дальность связи: не дальше прорисовки сервера (иначе пилот видит пустоту). */
    public static int linkRange(net.minecraft.server.MinecraftServer server) {
        return SHAHED_LINK_RANGE;
    }

    /** Есть ли связь оператора с дроном: напрямую или через свой ретранслятор рядом с дроном. */
    public boolean linked(ServerPlayer op) {
        if (op.distanceTo(this) <= SHAHED_LINK_RANGE) {
            return true;
        }
        return !level().getEntitiesOfClass(Launcher.class, getBoundingBox().inflate(RELAY_RANGE, 400, RELAY_RANGE),
                l -> l.kind() == Launcher.Kind.RELAY && l.health() > 0 && com.harddayz.war.War.allied(op.getUUID(), l.owner())).isEmpty();
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PILOT = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BATTERY = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> GRENADES = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    /** Помехи от РЭБ 0..1 (для экрана пилота и срыва управления). */
    private static final EntityDataAccessor<Float> INTERFERENCE = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);
    /** Двигатель включён (квадрокоптер пилот включает/глушит сам; у «Шахедов» мотор работает всегда). */
    private static final EntityDataAccessor<Boolean> ENGINE = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FACTION = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PARKED = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.BOOLEAN);

    public boolean engineOn() {
        if (entityData.get(PARKED)) {
            return entityData.get(ENGINE);
        }
        return kind().autonomous || entityData.get(ENGINE);
    }

    public int faction() {
        return entityData.get(FACTION);
    }

    public float interference() {
        return entityData.get(INTERFERENCE);
    }

    /** РЭБ рядом: помехи растут, сильные — срыв связи / курса. */
    /** РЭБ: помехи нарастают постепенно — сначала сильный «снег» и рывки, потом связь рвётся. */
    public void interfere(ServerLevel level, float k) {
        float v = Math.min(1f, Math.max(interference(), k * 0.5f) + k * 0.03f);
        entityData.set(INTERFERENCE, v);
        if (v >= 0.95f) {
            jam(level);
        }
    }

    /** Качество связи 0..1 (синхронизируется пилоту для экрана). */
    private static final EntityDataAccessor<Float> LINK = SynchedEntityData.defineId(Drone.class, EntityDataSerializers.FLOAT);

    public float link() {
        return entityData.get(LINK);
    }

    /** Связь с оператором: квадрокоптер — напрямую, «Шахед»/«Ланцет» — напрямую или через ближайший свой ретранслятор. */
    private float linkQuality(ServerPlayer op) {
        if (!kind().autonomous) {
            return (float) Mth.clamp(1 - op.distanceTo(this) / SIGNAL_RANGE, 0, 1);
        }
        double q = 1 - op.distanceTo(this) / SHAHED_LINK_RANGE;
        for (Launcher l : level().getEntitiesOfClass(Launcher.class, getBoundingBox().inflate(RELAY_RANGE, 400, RELAY_RANGE),
                l -> l.kind() == Launcher.Kind.RELAY && l.health() > 0 && com.harddayz.war.War.allied(op.getUUID(), l.owner()))) {
            q = Math.max(q, 1 - l.distanceTo(this) / RELAY_RANGE);
        }
        return (float) Mth.clamp(q, 0, 1);
    }

    /** Обновить связь; false — связь потеряна (пилот отключён). */
    private boolean keepLink(ServerPlayer op) {
        float q = linkQuality(op);
        entityData.set(LINK, q);
        if (q <= 0) {
            op.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.link_lost").withStyle(net.minecraft.ChatFormatting.RED), true);
            release();
            return false;
        }
        return true;
    }

    private float health = 8;
    private @Nullable UUID owner;
    private Vec3 target = Vec3.ZERO;
    private boolean diving;
    private @Nullable FlightChunks chunks;
    /** Клиент: запущен ли звук мотора. */
    public boolean soundStarted;
    /** Клиент: вращение винтов. */
    public float rotor;

    public Drone(EntityType<? extends Drone> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ создание

    public static Drone launchShahed(ServerLevel level, Vec3 pos, Vec3 dir, Vec3 target, @Nullable UUID owner, Kind kind) {
        Drone d = new Drone(ModEntities.DRONE.get(), level);
        d.setKind(kind);
        d.setPos(pos);
        d.setDeltaMovement(dir.scale(0.9));
        d.target = target;
        d.owner = owner;
        d.face(dir);
        level.addFreshEntity(d);
        return d;
    }

    public static Drone deploy(ServerLevel level, ServerPlayer pilot, Kind kind) {
        Drone d = new Drone(ModEntities.DRONE.get(), level);
        d.setKind(kind);
        Vec3 at = pilot.getEyePosition().add(pilot.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));
        d.setPos(at);
        d.setYRot(pilot.getYRot());
        d.owner = pilot.getUUID();
        // ставится на землю без пилота: подключиться — ПКМ по дрону или пультом оператора
        level.addFreshEntity(d);
        return d;
    }

    public void setKind(Kind k) {
        entityData.set(KIND, k.ordinal());
        entityData.set(BATTERY, k.battery);
        entityData.set(GRENADES, k == Kind.RECON ? 3 : k == Kind.BABA_YAGA ? 2 : 0);
        health = k.health;
    }

    // ------------------------------------------------------------------ доступ

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    public int pilotId() {
        return entityData.get(PILOT);
    }

    public int battery() {
        return entityData.get(BATTERY);
    }

    public int grenades() {
        return entityData.get(GRENADES);
    }

    @Override
    public boolean isThreat() {
        return true;
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    private void face(Vec3 v) {
        if (v.lengthSqr() < 1.0E-6) {
            return;
        }
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        setYRot((float) Math.toDegrees(Math.atan2(-v.x, v.z)));
        setXRot((float) Math.toDegrees(-Math.atan2(v.y, h)));
    }

    private final com.harddayz.Compat.Lerp lerp = new com.harddayz.Compat.Lerp(2);

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        lerp.set(x, y, z, yRot, xRot);
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            rotor += kind().autonomous ? 2.2f : 1.6f;
            boolean mine = com.harddayz.client.ClientAccess.isPilotingLocally(this);
            if (!mine) {
                lerp.tick(this);
            }
            com.harddayz.client.ClientAccess.droneClientTick(this);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (tickCount % 10 == 0 && interference() > 0) {
            entityData.set(INTERFERENCE, Math.max(0, interference() - 0.08f)); // вне зоны РЭБ помехи спадают
        }
        if (tickCount % 40 == 1) {
            entityData.set(FACTION, com.harddayz.war.War.factionIndex(owner));
        }
        if (kind().autonomous && pilotId() >= 0) {
            Entity op = level.getEntity(pilotId());
            if (!(op instanceof ServerPlayer sop) || !op.isAlive() || !keepLink(sop)) {
                release(); // связь потеряна — дальше летит сам
            } else if (battery() <= 0) {
                ((ServerPlayer) op).displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.battery_out").withStyle(net.minecraft.ChatFormatting.RED), true);
                release(); // заряд кончился — дальше по старому курсу
            } else {
                entityData.set(BATTERY, battery() - 1);
                PilotView.follow((ServerPlayer) op, this);
                if (tickCount > 6000) {
                    detonate(level);
                }
                if (chunks == null) {
                    chunks = new FlightChunks(getUUID());
                }
                chunks.update(level, position(), getDeltaMovement(), null);
                return; // ведёт оператор
            }
        }
        if (kind().autonomous) {
            tickShahed(level);
            return;
        }
        // пилотируемые: заряд тратится только при включённом моторе
        if (engineOn() && (pilotId() >= 0 || tickCount % 4 == 0)) {
            entityData.set(BATTERY, Math.max(0, battery() - 1));
            if (battery() <= 0) {
                entityData.set(ENGINE, false); // сел аккумулятор — мотор встал, дрон падает
            }
        }
        Entity pilot = pilotId() >= 0 ? level.getEntity(pilotId()) : null;
        if (pilotId() >= 0 && (!(pilot instanceof ServerPlayer sp0) || !pilot.isAlive() || !keepLink(sp0))) {
            release(); // потеря связи
        } else if (pilot instanceof ServerPlayer sp) {
            PilotView.follow(sp, this);
        }
        if (pilotId() < 0) {
            // без пилота: с мотором висит на месте, без мотора — падает
            Vec3 v = getDeltaMovement();
            v = engineOn() ? v.scale(0.8) : v.add(0, -0.06, 0).multiply(0.9, 1, 0.9);
            double fall = v.y;
            setDeltaMovement(v);
            move(MoverType.SELF, v);
            if (onGround()) {
                setDeltaMovement(Vec3.ZERO);
                if (fall < -0.6) {
                    crash(level); // рухнул с высоты
                }
            }
        }
    }

    /** Удар о землю на скорости. */
    public void crash(ServerLevel level) {
        if (kind().warhead > 0) {
            detonate(level);
        } else {
            breakApart(level);
        }
    }

    /** Пилот включает/глушит мотор. */
    public void toggleEngine(ServerPlayer from) {
        if (from.getId() != pilotId() || kind().autonomous) {
            return;
        }
        boolean on = !entityData.get(ENGINE) && battery() > 0;
        entityData.set(ENGINE, on);
        from.displayClientMessage(net.minecraft.network.chat.Component.translatable(on ? "message.harddayz.engine_on" : "message.harddayz.engine_off")
                .withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW), true);
        level().playSound(null, getX(), getY(), getZ(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 0.5f, 2f);
    }

    private int hunt = -1;
    /** Стоит на земле и ждёт пуска (ПКМ по нему). */
    private boolean parked;
    private int launchedAt;

    /** Самолётные камикадзе под оператором: сами не летают. */
    private boolean wingFpv() {
        return kind() == Kind.LANCET || kind() == Kind.MOLNIYA;
    }

    public boolean isParked() {
        return parked;
    }

    public boolean parkable() {
        return kind() == Kind.INTERCEPTOR || wingFpv() || kind() == Kind.WING_RECON;
    }

    /** Поставить на землю перед игроком, мотор не запущен. */
    public static Drone park(ServerLevel level, ServerPlayer p, Kind kind) {
        Drone d = new Drone(ModEntities.DRONE.get(), level);
        d.setKind(kind);
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.6)).add(0, 0.3, 0);
        d.setPos(at);
        d.setYRot(p.getYRot());
        d.owner = p.getUUID();
        d.parked = true;
        d.entityData.set(PARKED, true);
        level.addFreshEntity(d);
        return d;
    }

    private int flight() {
        return tickCount - launchedAt;
    }

    /** Пуск со стоянки: цель — точка целеуказателя у игрока или куда он смотрит. */
    private void launchFromGround(ServerPlayer p) {
        ServerLevel level = (ServerLevel) level();
        Vec3 aim = null;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ModItems.DESIGNATOR.get()) && com.harddayz.item.DesignatorItem.target(s) != null) {
                aim = Vec3.atCenterOf(com.harddayz.item.DesignatorItem.target(s));
                break;
            }
        }
        manualClimb = wingFpv(); // «Ланцет» сам не летает: взлёт и сразу к оператору
        if (aim == null || kind() == Kind.INTERCEPTOR) {
            var hit = level.clip(new ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(600)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) {
                // смотрел в небо — берём точку на земле в 150 блоках по направлению взгляда
                Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
                Vec3 at = position().add((flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize()).scale(150));
                aim = new Vec3(at.x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z)), at.z);
            } else {
                aim = hit.getLocation();
            }
        }
        parked = false;
        entityData.set(PARKED, false);
        launchedAt = tickCount;
        owner = p.getUUID();
        target = aim;
        Vec3 flat = aim.subtract(position()).multiply(1, 0, 1);
        Vec3 dir = (flat.lengthSqr() < 1 ? p.getLookAngle().multiply(1, 0, 1) : flat).normalize().add(0, 1.2, 0).normalize();
        setDeltaMovement(dir.scale(0.6));
        face(dir);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 2f, 1.1f);
        com.harddayz.Compat.particles(level, ParticleTypes.CLOUD, getX(), getY(), getZ(), 20, 0.4, 0.2, 0.4, 0.05);
    }

    private void tickParked(ServerLevel level) {
        Vec3 v = getDeltaMovement().add(0, -0.06, 0).multiply(0.8, 1, 0.8);
        move(MoverType.SELF, v);
        setDeltaMovement(onGround() ? Vec3.ZERO : v);
    }
    private int idle;
    private boolean offered;

    /** Перехватчик: ищет ближайший чужой дрон и идёт на сближение с упреждением. */
    /** Враг ли это для перехватчика: в матче — чужие фракции; вне матча — всё, чем не управляет сам хозяин (для тренировки). */
    private boolean hostile(Entity e) {
        UUID o = e instanceof Threat th ? th.owner() : null;
        if (e instanceof Drone d) {
            if (d.kind() == Kind.INTERCEPTOR) {
                return false;
            }
            Player op = owner == null ? null : level().getPlayerByUUID(owner);
            if (op != null && d.pilotId() == op.getId()) {
                return false; // не трогать дрон, которым сейчас рулит сам хозяин
            }
        }
        if (e instanceof Missile m && (m.kind() == Missile.Kind.SAM || !m.isThreat())) {
            return false;
        }
        if (owner != null && owner.equals(o)) {
            return false; // свои дроны и ракеты — никогда
        }
        return !com.harddayz.war.War.active() || !com.harddayz.war.War.allied(owner, o);
    }

    private @Nullable Vec3 home;
    private @Nullable Vec3 loiterAt;

    /** «Ланцет» без оператора: кружит над точкой, тратит заряд; кончился — падает. К нему можно вернуться пультом. */
    private void loiter(ServerLevel level) {
        if (loiterAt == null) {
            loiterAt = position();
        }
        entityData.set(BATTERY, Math.max(0, battery() - 1));
        if (battery() <= 0) {
            Vec3 v = getDeltaMovement().add(0, -0.08, 0);
            setDeltaMovement(v);
            Vec3 to = position().add(v);
            var hit = level.clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                detonate(level);
                return;
            }
            setPos(to);
            return;
        }
        Vec3 off = new Vec3(getX() - loiterAt.x, 0, getZ() - loiterAt.z);
        Vec3 tangent = off.lengthSqr() < 1 ? new Vec3(1, 0, 0) : new Vec3(-off.z, 0, off.x).normalize();
        Vec3 pull = off.lengthSqr() < 1 ? Vec3.ZERO : off.normalize().scale((25 - off.length()) * 0.04);
        Vec3 desired = tangent.add(pull).add(0, Mth.clamp((loiterAt.y - getY()) * 0.05, -0.3, 0.3), 0).normalize();
        Vec3 v = steer(getDeltaMovement(), desired, kind().cruise * 0.6, 0.1);
        setDeltaMovement(v);
        face(v);
        setPos(position().add(v));
    }

    /**
     * Перехватчик: набирает высоту патрулирования и кружит над точкой пуска, ищет ближайший чужой дрон или ракету
     * в 250 блоках и идёт на сближение с упреждением. Без цели 40 с — самоликвидация.
     */
    private void tickInterceptor(ServerLevel level) {
        if (flight() > 2400) {
            detonate(level);
            return;
        }
        if (home == null) {
            home = position();
        }
        Entity t = hunt >= 0 ? level.getEntity(hunt) : null;
        if (t == null || !t.isAlive() || t.distanceTo(this) > 300) {
            t = null;
            hunt = -1;
            double best = 250 * 250;
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(250), e -> (e instanceof Drone || e instanceof Missile) && hostile(e))) {
                double ds = e.distanceToSqr(this);
                if (ds < best) {
                    best = ds;
                    t = e;
                }
            }
            if (t != null) {
                hunt = t.getId();
            }
        }
        Vec3 v = getDeltaMovement();
        Vec3 desired;
        double speed;
        if (t == null) {
            if (++idle > 800) {
                detonate(level); // цели нет — самоликвидация
                return;
            }
            // патруль: круг радиусом 30 над точкой пуска на высоте ~45 над землёй
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
            Vec3 off = new Vec3(getX() - home.x, 0, getZ() - home.z);
            Vec3 tangent = off.lengthSqr() < 1 ? new Vec3(1, 0, 0) : new Vec3(-off.z, 0, off.x).normalize();
            Vec3 pull = off.lengthSqr() < 1 ? Vec3.ZERO : off.normalize().scale((30 - off.length()) * 0.04);
            desired = tangent.add(pull).add(0, Mth.clamp((ground + 45 - getY()) * 0.05, -0.6, 0.6), 0).normalize();
            speed = kind().cruise * 0.6;
        } else {
            idle = 0;
            double dist = t.distanceTo(this);
            Vec3 lead = t.position().add(t.getDeltaMovement().scale(dist / kind().cruise));
            desired = lead.subtract(position()).normalize();
            speed = kind().cruise;
            if (dist < 4) {
                if (random.nextFloat() < 0.65f) {
                    if (t instanceof Drone td) {
                        td.shotDown(level);
                    } else if (t instanceof Missile m) {
                        m.intercepted(level);
                    }
                }
                detonate(level);
                return;
            }
        }
        v = steer(v, desired, Math.min(speed, v.length() + 0.12), 0.18);
        setDeltaMovement(v);
        face(v);
        Vec3 to = position().add(v);
        var hit = level.clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        if (flight() > 6 && hit.getType() != HitResult.Type.MISS) {
            setPos(hit.getLocation());
            detonate(level);
            return;
        }
        setPos(to);
    }

    /** Самолётный разведчик: круги над точкой, раз в секунду — доклад в сеть РЛС. */
    private void tickWingRecon(ServerLevel level) {
        if (flight() > 7200) {
            breakApart(level); // кончилось топливо
            return;
        }
        Vec3 pos = position();
        Vec3 v = getDeltaMovement();
        Vec3 off = new Vec3(pos.x - target.x, 0, pos.z - target.z);
        double r = off.length();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
        double alt = Math.max(ground, target.y) + kind().altitude;
        Vec3 desired;
        if (r > 70) {
            desired = new Vec3(-off.x, 0, -off.z).normalize();
        } else {
            Vec3 tangent = new Vec3(-off.z, 0, off.x).normalize();
            desired = tangent.add(off.normalize().scale((45 - r) * 0.03));
        }
        desired = desired.add(0, Mth.clamp((alt - pos.y) * 0.04, -0.4, 0.4), 0).normalize();
        v = steer(v, desired, kind().cruise, 0.06);
        setDeltaMovement(v);
        face(v);
        if (chunks == null) {
            chunks = new FlightChunks(getUUID());
        }
        chunks.update(level, pos, v, null);
        setPos(pos.add(v));
        if (tickCount % 20 == 0 && owner != null) {
            String key = com.harddayz.war.RadarNet.key(owner);
            long now = level.getGameTime();
            for (Entity e : level.getEntities(this, getBoundingBox().inflate(150, 120, 150), e -> (e instanceof Player || e instanceof Vehicle
                    || e instanceof Drone) && !com.harddayz.war.War.allied(owner, e instanceof Player p ? p.getUUID()
                            : e instanceof Vehicle vh ? vh.owner() : ((Drone) e).owner()))) {
                com.harddayz.war.RadarNet.report(key, e, now);
            }
            com.harddayz.war.RadarNet.radarAlive(key, now);
        }
    }

    /** «Ланцет» запущен без целеуказателя: сначала вертикальный взлёт, потом управление сразу у оператора. */
    private boolean manualClimb;

    private void tickShahed(ServerLevel level) {
        if (parked) {
            tickParked(level);
            return;
        }
        if (manualClimb) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ()));
            if (getY() < ground + 25 && flight() < 120) {
                setDeltaMovement(new Vec3(0, 0.45, 0));
                setPos(position().add(0, 0.45, 0));
                return;
            }
            manualClimb = false;
            offered = true;
            Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
            setDeltaMovement(fwd.scale(0.6));
            target = position().add(fwd.scale(400)).subtract(0, 25, 0);
            if (owner != null && level.getPlayerByUUID(owner) instanceof ServerPlayer op && linked(op) && takeControl(op)) {
                op.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.lancet_manual").withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
        }
        if (kind() == Kind.INTERCEPTOR) {
            tickInterceptor(level);
            return;
        }
        if (kind() == Kind.WING_RECON) {
            tickWingRecon(level);
            return;
        }
        if (wingFpv() && !manualClimb && pilotId() < 0) {
            loiter(level); // оператор отключился — кружит на месте и ждёт
            return;
        }
        if (wingFpv() && !offered && owner != null && flight() > 80
                && getY() - level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX()), Mth.floor(getZ())) > 20
                && Math.sqrt(position().distanceToSqr(target.x, position().y, target.z)) < 120
                && level.getPlayerByUUID(owner) instanceof ServerPlayer op && linked(op)) {
            offered = true; // подлетел — оператор выбирает цель сам
            if (takeControl(op)) {
                op.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.lancet_control").withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
        }
        Vec3 v = getDeltaMovement();
        Vec3 pos = position();
        if (flight() > 6000) {
            detonate(level);
            return;
        }
        if (flight() < 25) {
            v = v.normalize().scale(Math.min(kind().cruise, v.length() + 0.04 * kind().cruise)).add(0, 0.02, 0);
        } else {
            double dx = target.x - pos.x;
            double dz = target.z - pos.z;
            double dh = Math.sqrt(dx * dx + dz * dz);
            Vec3 desired;
            double speed;
            double turn;
            if (!diving && dh > Math.max(45, (pos.y - target.y) * 1.6 + 20)) {
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x + v.x * 20), Mth.floor(pos.z + v.z * 20));
                double alt = Math.max(ground, target.y) + kind().altitude;
                Vec3 flat = new Vec3(dx, 0, dz).normalize();
                // лёгкое «рыскание», как у настоящего
                flat = flat.yRot((float) Math.sin(tickCount * 0.05 + getId()) * 0.08f);
                desired = flat.add(0, Mth.clamp((alt - pos.y) * 0.04, -0.4, 0.4), 0).normalize();
                speed = kind().cruise;
                turn = 0.05 / Math.max(1, kind().cruise / 1.3);
            } else {
                diving = true;
                int gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(target.x), Mth.floor(target.z));
                Vec3 aim = new Vec3(target.x, Math.min(target.y, gy), target.z); // на реальную землю
                desired = aim.subtract(pos).normalize();
                speed = Math.min(kind().cruise * 1.6, v.length() + 0.03);
                turn = 0.06;
            }
            v = steer(v, desired, speed, turn);
        }
        setDeltaMovement(v);
        face(v);
        if (chunks == null) {
            chunks = new FlightChunks(getUUID());
        }
        if (!chunks.update(level, pos, v, target)) {
            return; // впереди чанк ещё грузится — ждём тик
        }
        Vec3 to = pos.add(v);
        if (flight() > 4) {
            BlockHitResult hit = level.clip(new ClipContext(pos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                detonate(level);
                return;
            }
            if (rammed(level, pos, to, null)) {
                return;
            }
            for (Entity e : level.getEntities(this, new AABB(pos, to).inflate(1), e -> (e instanceof LivingEntity || e instanceof Vehicle)
                    && !(e instanceof Vehicle vh && (flight() < 60 || com.harddayz.war.War.allied(owner, vh.owner()))))) {
                if (e.getBoundingBox().inflate(0.5).clip(pos, to).isPresent()) {
                    setPos(e.position());
                    detonate(level);
                    return;
                }
            }
        }
        setPos(to);
    }

    private static Vec3 steer(Vec3 v, Vec3 desired, double speed, double maxTurn) {
        Vec3 cur = v.lengthSqr() < 1.0E-6 ? desired : v.normalize();
        double angle = Math.acos(Mth.clamp(cur.dot(desired), -1, 1));
        Vec3 dir = angle <= maxTurn ? desired : cur.scale(1 - maxTurn / angle).add(desired.scale(maxTurn / angle)).normalize();
        return dir.scale(speed);
    }

    // ------------------------------------------------------------------ команды пилота (с сервера)

    /** Позиция от клиента пилота. */
    public void pilotMove(ServerPlayer from, Vec3 pos, float yaw, float pitch) {
        if (from.getId() != pilotId()) {
            return;
        }
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return;
        }
        if (pos.distanceTo(position()) > 12) {
            return; // слишком большой скачок — не принимаем
        }
        if (kind().autonomous && level() instanceof ServerLevel sl) {
            // столкновение по пути — подрыв
            var hit = sl.clip(new ClipContext(position(), pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                detonate(sl);
                return;
            }
        }
        if (level() instanceof ServerLevel sl && rammed(sl, position(), pos, from)) {
            return;
        }
        setDeltaMovement(pos.subtract(position()));
        setPos(pos);
        setYRot(yaw);
        setXRot(pitch);
    }

    /**
     * Столкновение в воздухе с другим дроном или ракетой: обоих сбивает (у кого есть БЧ — взрыв).
     * Возвращает true, если этот дрон погиб.
     */
    private boolean rammed(ServerLevel level, Vec3 from, Vec3 to, @Nullable Entity ignore) {
        if (flight() < 10 && kind().autonomous) {
            return false; // только что стартовал с пусковой
        }
        for (Entity e : level.getEntities(this, new AABB(from, to).inflate(1.2), e -> (e instanceof Drone || e instanceof Missile) && e != ignore)) {
            if (e instanceof Drone d && d.parked) {
                continue;
            }
            if (!e.getBoundingBox().inflate(0.5).clip(from, to).isPresent() && !e.getBoundingBox().inflate(0.5).contains(to)) {
                continue;
            }
            setPos(e.position());
            if (e instanceof Drone d) {
                d.shotDown(level);
            } else if (e instanceof Missile m) {
                m.intercepted(level);
            }
            if (kind().warhead > 0) {
                detonate(level);
            } else {
                shotDown(level);
            }
            return true;
        }
        return false;
    }

    /** ЛКМ пилота: FPV — подрыв, разведчик — сброс гранаты. */
    public void pilotFire(ServerPlayer from) {
        if (from.getId() != pilotId() || !(level() instanceof ServerLevel level)) {
            return;
        }
        if (kind() == Kind.FPV || kind().autonomous) {
            detonate(level);
        } else if ((kind() == Kind.RECON || kind() == Kind.BABA_YAGA) && grenades() > 0) {
            entityData.set(GRENADES, grenades() - 1);
            Missile.launch(level, Missile.Kind.BOMB, position().add(0, -0.4, 0), getDeltaMovement().scale(0.5), position(), this).owner(owner)
                    .powerMul(kind() == Kind.BABA_YAGA ? 1.6f : 1f);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 1f, 1.4f);
        }
    }

    /** ПКМ пилота разведдрона: записать точку в журнал целей из инвентаря пилота. */
    public void pilotMark(ServerPlayer from, net.minecraft.core.BlockPos pos) {
        if (from.getId() != pilotId() || kind() != Kind.RECON || pos.distToCenterSqr(position()) > 700 * 700) {
            return;
        }
        var inv = from.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.TARGET_LOG.get())) {
                int n = com.harddayz.item.TargetLogItem.add(s, pos);
                from.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.point_saved", n, pos.getX(), pos.getY(),
                        pos.getZ()).withStyle(net.minecraft.ChatFormatting.GREEN), true);
                from.level().playSound(null, from.blockPosition(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.8f, 1.8f);
                return;
            }
        }
        from.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.no_log").withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    /** Подключиться к своему квадрокоптеру (ПКМ по нему или пульт). */
    public boolean connect(ServerPlayer p) {
        if (kind().autonomous || pilotId() >= 0 || battery() <= 0 || (owner != null && !com.harddayz.war.War.allied(p.getUUID(), owner))) {
            String why = battery() <= 0 ? "message.harddayz.drone_dead" : pilotId() >= 0 ? "message.harddayz.drone_busy" : "message.harddayz.drone_foreign";
            if (!kind().autonomous) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable(why).withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return false;
        }
        entityData.set(PILOT, p.getId());
        owner = p.getUUID();
        return true;
    }

    /** Пульт оператора: взять под управление летящий «Шахед». */
    public boolean takeControl(ServerPlayer op) {
        if (!kind().autonomous || pilotId() >= 0) {
            return false;
        }
        if (battery() <= 0) {
            op.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.battery_out").withStyle(net.minecraft.ChatFormatting.RED), true);
            return false;
        }
        entityData.set(PILOT, op.getId());
        return true;
    }

    public void release() {
        loiterAt = null;
        if (pilotId() >= 0 && level().getEntity(pilotId()) instanceof ServerPlayer sp) {
            PilotView.restore(sp);
        }
        entityData.set(PILOT, -1);
    }

    private boolean jammed;

    /** Попал под РЭБ: «Шахед» сбивается с курса, пилотируемый теряет связь и падает. */
    public void jam(ServerLevel level) {
        if (jammed) {
            return;
        }
        jammed = true;
        if (kind().autonomous) {
            if (!diving) {
                double ang = random.nextDouble() * Math.PI * 2;
                double off = 40 + random.nextDouble() * 60;
                target = target.add(Math.cos(ang) * off, 0, Math.sin(ang) * off);
            }
        } else {
            release();
            entityData.set(BATTERY, 0);
        }
        com.harddayz.Compat.particles(level, ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 15, 0.4, 0.4, 0.4, 0.2);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (parked) {
            if (!level().isClientSide() && player instanceof ServerPlayer sp) {
                if (!entityData.get(ENGINE)) {
                    entityData.set(ENGINE, true); // первый ПКМ — завести мотор
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.parked_engine").withStyle(net.minecraft.ChatFormatting.GOLD), true);
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 0.6f, 2f);
                } else {
                    launchFromGround(sp); // второй — пуск
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (kind().autonomous) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide() && battery() <= 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.harddayz.drone_dead")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            connect(sp);
        }
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ урон

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (!(level() instanceof ServerLevel level)) {
            return true;
        }
        // забрать дрон — только ударом рукой вплотную (а не очередью из пулемёта)
        if ((parked || !kind().autonomous) && pilotId() < 0 && source.getEntity() instanceof Player p && source.getDirectEntity() == p
                && p.getVehicle() == null && p.distanceTo(this) < 5 && (p.isShiftKeyDown() || p.isCreative())) {
            spawnAtLocation(new ItemStack(switch (kind()) {
                case FPV -> ModItems.FPV_DRONE.get();
                case INTERCEPTOR -> ModItems.INTERCEPTOR.get();
                case LANCET -> ModItems.LANCET.get();
                case WING_RECON -> ModItems.WING_RECON.get();
                case MOLNIYA -> ModItems.MOLNIYA.get();
                case BABA_YAGA -> ModItems.BABA_YAGA.get();
                default -> ModItems.RECON_DRONE.get();
            }));
            discard();
            return true;
        }
        health -= damage;
        if (health <= 0) {
            shotDown(level);
        }
        return true;
    }

    /** Сбит: боевая часть рвётся в воздухе слабее. */
    public void shotDown(ServerLevel level) {
        if (isRemoved()) {
            return;
        }
        Vec3 at = position();
        remove(RemovalReason.KILLED);
        com.harddayz.Compat.particles(level, net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.4, 0.4, 0.4, 0);
        com.harddayz.Compat.particles(level, net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 20, 0.5, 0.5, 0.5, 0.05);
        // сбитый дрон взрывается: боевая часть — сильнее, разведчик — батарея и обломки
        Blast.detonate(level, at, Math.max(2.2f, kind().warhead * 0.6f), false, this);
    }

    private void detonate(ServerLevel level) {
        Blast.detonate(level, position(), kind().warhead, true, this);
        remove(RemovalReason.KILLED);
    }

    private void breakApart(ServerLevel level) {
        com.harddayz.Compat.particles(level, ParticleTypes.SMOKE, getX(), getY(), getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        com.harddayz.Compat.particles(level, ParticleTypes.CRIT, getX(), getY(), getZ(), 15, 0.3, 0.3, 0.3, 0.2);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 1f, 0.7f);
        remove(RemovalReason.KILLED);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            if (chunks != null) {
                chunks.release(level);
            }
            if (pilotId() >= 0 && level.getEntity(pilotId()) instanceof ServerPlayer sp) {
                PilotView.restore(sp);
            }
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ служебное

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(3);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 400 * 400;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
        entityData.define(PILOT, -1);
        entityData.define(BATTERY, 0);
        entityData.define(GRENADES, 0);
        entityData.define(INTERFERENCE, 0f);
        entityData.define(ENGINE, false);
        entityData.define(FACTION, -1);
        entityData.define(LINK, 1f);
        entityData.define(PARKED, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag input) {
        entityData.set(KIND, com.harddayz.Compat.getInt(input, "kind", 1));
        entityData.set(BATTERY, com.harddayz.Compat.getInt(input, "battery", 0));
        entityData.set(GRENADES, com.harddayz.Compat.getInt(input, "grenades", 0));
        entityData.set(ENGINE, com.harddayz.Compat.getBoolean(input, "engine", false));
        parked = com.harddayz.Compat.getBoolean(input, "parked", false);
        entityData.set(PARKED, parked);
        health = com.harddayz.Compat.getFloat(input, "health", kind().health);
        com.harddayz.Compat.optString(input, "owner").ifPresent(s -> owner = UUID.fromString(s));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
        output.putInt("kind", entityData.get(KIND));
        output.putInt("battery", battery());
        output.putInt("grenades", grenades());
        output.putBoolean("engine", entityData.get(ENGINE));
        output.putBoolean("parked", parked);
        output.putFloat("health", health);
        if (owner != null) {
            output.putString("owner", owner.toString());
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return !kind().autonomous || parked;
    }
}
