package com.harddayz.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.harddayz.item.DesignatorItem;
import com.harddayz.registry.ModItems;
import com.harddayz.weapon.Blast;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Пусковая установка на колёсном шасси. Порядок пуска: опоры вниз → наведение → пуск → пакет/стрела
 * в походное положение → опоры вверх. Пока опоры выдвинуты, ехать нельзя.
 */
public class Launcher extends Vehicle {
    public enum Kind {
        /** РСЗО: пакет из 20 направляющих, залп по площади. */
        GRAD(40, 160),
        /** Тактический ракетный комплекс: 1 ракета, стрела поднимает её вертикально. */
        ISKANDER(1, 220),
        /** Зенитный комплекс: 8 ракет, сам находит и сбивает ракеты и дроны. */
        SAM(8, 140),
        /** Пусковая ударных дронов: контейнер на 5 «Шахедов». */
        SHAHED(5, 130, true),
        /** Одиночная рампа под один дрон. */
        RAMP(1, 50, false),
        /** «Град-П»: одна труба на треноге. */
        GRAD_P(1, 40, false),
        /** Одиночный пост ЗУР: 2 ракеты, сам сбивает цели. */
        SAM_POST(2, 60, false),
        /** Станция РЭБ: глушит чужие дроны в радиусе 128 блоков. */
        EW(0, 120, true),
        /** Ручной ЗРК: стрелок сам наводит и пускает (захват цели в прицеле). Дешевле автоматического. */
        SAM_MANUAL(4, 50, false),
        /** Пулемётное гнездо: стрелок ведёт огонь по дронам, людям и лёгкой технике. */
        MG(250, 40, false),
        /** РЛС: засекает чужие ракеты и дроны в 600 блоках и передаёт сети фракции. */
        RADAR(0, 150, true),
        /** С-400: дальний ЗРК — бьёт на 480 блоков быстрыми ракетами, 4 ракеты. */
        S400(4, 260),
        /** ТОС-1А «Солнцепёк»: 24 термобарических ракеты — мощно и с пожаром, но ближе «Града». */
        TOS(24, 200),
        /** HIMARS: 6 управляемых ракет, по 2 за приказ, точно и далеко, но слабее «Искандера». */
        HIMARS(6, 170),
        /** Patriot: ПРО — бьёт только ракеты, баллистику сбивает почти наверняка. 4 дорогие ракеты. */
        PATRIOT(4, 240),
        /** NASAMS: средняя ПВО базы — дроны и крылатые цели, 6 ракет. */
        NASAMS(6, 160),
        /** Пикап с пулемётом: быстрый, дешёвый, стрелок едет и стреляет сам. */
        PICKUP_MG(250, 90),
        /** Пикап с ПЗРК: захват воздушной цели в прицеле. */
        PICKUP_SAM(4, 90),
        /** БМП-2: 30-мм автоматическая пушка с осколочно-фугасными. */
        BMP2(120, 260),
        /** Bradley: 25-мм пушка — чаще и точнее, но слабее снаряд; толще броня. */
        BRADLEY(150, 320),
        /** Ретранслятор: продлевает связь с «Шахедами» и «Ланцетами» своей фракции на 900 блоков вокруг. */
        RELAY(0, 80, false),
        /** Т-72Б3: 125-мм пушка, толстая броня, медленная перезарядка. */
        T72(22, 520),
        /** Abrams: 120-мм пушка, ещё толще броня, чуть быстрее перезарядка. */
        ABRAMS(20, 600);

        public final int capacity;
        public final float health;
        public final boolean mobile;

        Kind(int capacity, float health) {
            this(capacity, health, true);
        }

        Kind(int capacity, float health, boolean mobile) {
            this.capacity = capacity;
            this.health = health * 0.6f; // техника была слишком живучей
            this.mobile = mobile;
        }

        public boolean dronePad() {
            return this == SHAHED || this == RAMP;
        }

        public boolean samLike() {
            return this == SAM || this == SAM_POST || this == S400 || this == PATRIOT || this == NASAMS;
        }

        public boolean accepts(ItemStack s) {
            if (dronePad()) {
                return droneKind(s) != null;
            }
            return this != EW && this != RADAR && this != RELAY && s.is(ammo());
        }

        /** Стрелковые установки: на них садятся и стреляют сами. */
        public boolean gunner() {
            return this == SAM_MANUAL || this == MG || this == PICKUP_MG || this == PICKUP_SAM || this == BMP2 || this == BRADLEY || tank();
        }

        public boolean tank() {
            return this == T72 || this == ABRAMS;
        }

        /** Стрельба только после захвата цели (ПЗРК). */
        public boolean lockOn() {
            return this == SAM_MANUAL || this == PICKUP_SAM;
        }

        public boolean cannon() {
            return this == BMP2 || this == BRADLEY || tank();
        }

        public Item ammo() {
            return switch (this) {
                case GRAD -> ModItems.GRAD_ROCKET.get();
                case ISKANDER -> ModItems.BALLISTIC_MISSILE.get();
                case SAM -> ModItems.SAM_MISSILE.get();
                case SHAHED, RAMP -> ModItems.SHAHED_DRONE.get();
                case GRAD_P -> ModItems.GRAD_ROCKET.get();
                case SAM_POST -> ModItems.SAM_MISSILE.get();
                case EW -> ModItems.DESIGNATOR.get();
                case SAM_MANUAL -> ModItems.SAM_MISSILE.get();
                case MG -> ModItems.MG_BELT.get();
                case RADAR -> ModItems.DESIGNATOR.get();
                case S400 -> ModItems.SAM_MISSILE.get();
                case TOS -> ModItems.TOS_ROCKET.get();
                case HIMARS -> ModItems.HIMARS_ROCKET.get();
                case PATRIOT -> ModItems.PATRIOT_MISSILE.get();
                case NASAMS -> ModItems.NASAMS_MISSILE.get();
                case PICKUP_MG -> ModItems.MG_BELT.get();
                case PICKUP_SAM -> ModItems.SAM_MISSILE.get();
                case BMP2, BRADLEY -> ModItems.CANNON_SHELLS.get();
                case T72, ABRAMS -> ModItems.TANK_SHELL.get();
                case RELAY -> ModItems.DESIGNATOR.get();
            };
        }

        public Item item() {
            return switch (this) {
                case GRAD -> ModItems.GRAD.get();
                case ISKANDER -> ModItems.ISKANDER.get();
                case SAM -> ModItems.SAM.get();
                case SHAHED -> ModItems.SHAHED_LAUNCHER.get();
                case RAMP -> ModItems.RAMP.get();
                case GRAD_P -> ModItems.GRAD_P.get();
                case SAM_POST -> ModItems.SAM_POST.get();
                case EW -> ModItems.EW_STATION.get();
                case SAM_MANUAL -> ModItems.SAM_MANUAL.get();
                case MG -> ModItems.MG_NEST.get();
                case RADAR -> ModItems.RADAR_TRUCK.get();
                case S400 -> ModItems.S400.get();
                case TOS -> ModItems.TOS.get();
                case HIMARS -> ModItems.HIMARS.get();
                case PATRIOT -> ModItems.PATRIOT.get();
                case NASAMS -> ModItems.NASAMS.get();
                case PICKUP_MG -> ModItems.PICKUP_MG.get();
                case PICKUP_SAM -> ModItems.PICKUP_SAM.get();
                case BMP2 -> ModItems.BMP2.get();
                case BRADLEY -> ModItems.BRADLEY.get();
                case T72 -> ModItems.T72.get();
                case ABRAMS -> ModItems.ABRAMS.get();
                case RELAY -> ModItems.RELAY.get();
            };
        }
    }

    /** Вид дрона по предмету-боеприпасу, или null. */
    public static Drone.@Nullable Kind droneKind(ItemStack s) {
        if (s.is(ModItems.SHAHED_DRONE.get())) {
            return Drone.Kind.SHAHED;
        }
        if (s.is(ModItems.SHAHED_238.get())) {
            return Drone.Kind.SHAHED_238;
        }
        if (s.is(ModItems.SHAHED_131.get())) {
            return Drone.Kind.SHAHED_131;
        }
        if (s.is(ModItems.GERBERA.get())) {
            return Drone.Kind.GERBERA;
        }
        return null;
    }

    private static Item droneItem(Drone.Kind k) {
        return switch (k) {
            case SHAHED_238 -> ModItems.SHAHED_238.get();
            case SHAHED_131 -> ModItems.SHAHED_131.get();
            case GERBERA -> ModItems.GERBERA.get();
            default -> ModItems.SHAHED_DRONE.get();
        };
    }

    /** Очередь дронов в контейнере (кто первым заряжен — тот первым летит). */
    private final java.util.ArrayList<Drone.Kind> drones = new java.util.ArrayList<>();

    private enum State {
        IDLE, DEPLOY, AIMING, FIRING, STOW, RETRACT
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WANT_TURRET = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WANT_ELEV = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WANT_ERECT = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FLASH = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.INT);
    /** Сохранённые цели пульта: 5 слотов «x,y,z» через «;» (пустой — нет цели). */
    private static final EntityDataAccessor<String> SLOTS = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.STRING);
    /** Типы дронов на направляющих (для моделей), порядковые номера через запятую. */
    private static final EntityDataAccessor<String> DRONES = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.STRING);

    public int[] droneKinds() {
        String s = entityData.get(DRONES);
        if (s.isEmpty()) {
            return new int[0];
        }
        return java.util.Arrays.stream(s.split(",")).mapToInt(Integer::parseInt).toArray();
    }
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.INT);
    public static final int SLOT_COUNT = 5;

    public @Nullable BlockPos slot(int i) {
        String[] parts = entityData.get(SLOTS).split(";", -1);
        if (i < 0 || i >= parts.length || parts[i].isEmpty()) {
            return null;
        }
        String[] c = parts[i].split(",");
        try {
            return new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public int selectedSlot() {
        return Mth.clamp(entityData.get(SLOT), 0, SLOT_COUNT - 1);
    }

    public void setSlot(int i, BlockPos p) {
        if (i < 0 || i >= SLOT_COUNT) {
            return;
        }
        String[] parts = entityData.get(SLOTS).split(";", -1);
        String[] out = new String[SLOT_COUNT];
        for (int k = 0; k < SLOT_COUNT; k++) {
            out[k] = k < parts.length ? parts[k] : "";
        }
        out[i] = p.getX() + "," + p.getY() + "," + p.getZ();
        entityData.set(SLOTS, String.join(";", out));
        entityData.set(SLOT, i);
    }

    private final Smooth turret = new Smooth(2.4f, 0.12f);
    private final Smooth samTurret = new Smooth(9f, 1.0f);
    private final Smooth elevation = new Smooth(1.3f, 0.07f);
    private final Smooth gunElevation = new Smooth(9f, 1.2f);
    private final Smooth erect = new Smooth(0.011f, 0.0006f);

    private @Nullable BlockPos target;
    private State state = State.IDLE;
    private int timer;
    private int shot;
    /** Оператор, который заберёт управление следующим запущенным дроном. */
    private @Nullable UUID controlOp;
    private int samCooldown;
    private int samTarget = -1;
    private final Set<Integer> engaged = new HashSet<>();

    public Launcher(EntityType<? extends Launcher> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ доступ

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    public void setKind(Kind k) {
        entityData.set(KIND, k.ordinal());
        entityData.set(HEALTH, k.health);
    }

    @Override
    public float maxHealth() {
        return kind().health;
    }

    @Override
    protected Vec3 seat() {
        return switch (kind()) {
            // слева (как в русской технике), на уровне пола кабины
            case GRAD, SHAHED -> new Vec3(-0.45, 1.45, 2.05);
            case ISKANDER -> new Vec3(-0.92, 1.3, 3.8);
            case SAM, S400 -> new Vec3(-0.45, 1.4, 2.4);
            case TOS -> new Vec3(-0.45, 1.45, 2.05);
            case EW -> new Vec3(-0.45, 1.45, 2.05);
            case MG -> new Vec3(0, 0.15, -0.95);
            case RADAR -> new Vec3(-0.45, 1.45, 2.05);
            case SAM_MANUAL -> new Vec3(0, 0.35, -1.0);
            case PICKUP_MG, PICKUP_SAM -> new Vec3(0, 1.0, -1.7);
            case BMP2, BRADLEY -> new Vec3(0, 1.75, -1.0);
            case T72, ABRAMS -> new Vec3(0, 1.9, 0.6);
            default -> new Vec3(0, 1, 0);
        };
    }

    @Override
    protected boolean canDrive() {
        return kind().mobile && super.canDrive();
    }

    /** Боевая машина с отдельными местами водителя и стрелка. */
    public boolean crewed() {
        return kind().gunner() && kind().mobile;
    }

    @Override
    protected int seats() {
        return crewed() ? 2 : 1;
    }

    @Override
    protected Vec3 seat(int i) {
        if (!crewed() || i == 0) {
            return crewed() ? switch (kind()) {
                case BMP2, BRADLEY -> new Vec3(-0.5, 1.2, 1.9);
                case T72, ABRAMS -> new Vec3(0, 1.0, 2.6);
                default -> new Vec3(-0.45, 0.55, 0.55);
            } : seat();
        }
        return seat();
    }

    /** Кто стреляет: у стационарных — единственный, у машин с экипажем — второе место. */
    public @Nullable Player gunnerPassenger() {
        var ps = getPassengers();
        int i = crewed() ? 1 : 0;
        return ps.size() > i && ps.get(i) instanceof Player p ? p : null;
    }

    @Override
    protected double maxSpeed() {
        return switch (kind()) {
            case ISKANDER -> 0.3;
            case PICKUP_MG, PICKUP_SAM -> 0.6;
            case BMP2 -> 0.36;
            case BRADLEY -> 0.33;
            case T72 -> 0.32;
            case ABRAMS -> 0.34;
            default -> 0.38;
        };
    }

    @Override
    protected double accel() {
        return switch (kind()) {
            case PICKUP_MG, PICKUP_SAM -> 0.012;
            case BMP2, BRADLEY -> 0.006;
            case T72, ABRAMS -> 0.0045;
            case ISKANDER, PATRIOT, S400 -> 0.004;
            default -> 0.005;
        };
    }

    /** Луч по сущностям: у техники хитбокс чуть шире, чтобы снаряд не проходил «сквозь» модель. */
    public static net.minecraft.world.phys.@Nullable EntityHitResult rayEntity(ServerLevel level, Entity shooter, Vec3 from, Vec3 to,
            java.util.function.Predicate<Entity> ok) {
        Entity best = null;
        Vec3 bestAt = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntities(shooter, new AABB(from, to).inflate(2), e -> e.isPickable() && !e.isSpectator() && ok.test(e))) {
            double g = e instanceof Vehicle ? 0.8 : e instanceof Drone ? 0.6 : 0.15;
            var c = e.getBoundingBox().inflate(g).clip(from, to);
            if (c.isPresent() && c.get().distanceToSqr(from) < bestD) {
                bestD = c.get().distanceToSqr(from);
                best = e;
                bestAt = c.get();
            }
        }
        return best == null ? null : new net.minecraft.world.phys.EntityHitResult(best, bestAt);
    }

    @Override
    public float wheelRadius() {
        return kind() == Kind.ISKANDER ? 0.66f : 0.58f;
    }

    public int ammo() {
        return entityData.get(AMMO);
    }

    private Smooth turretSmooth() {
        return kind().samLike() || kind() == Kind.EW || kind().gunner() ? samTurret : turret;
    }

    public float turret(float pt) {
        return turretSmooth().get(pt, true);
    }

    public float elevation(float pt) {
        return kind().gunner() ? gunElevation.get(pt, false) : elevation.get(pt, false);
    }

    public float erect(float pt) {
        return erect.get(pt, false);
    }

    public int flash() {
        return entityData.get(FLASH);
    }

    // ------------------------------------------------------------------ взаимодействие

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        ItemStack stack = player.getItemInHand(hand);
        if (kind().accepts(stack)) {
            if (!level().isClientSide()) {
                int n = Math.min(kind().capacity - ammo(), stack.getCount());
                if (kind() == Kind.MG || kind() == Kind.PICKUP_MG) {
                    int belts = Math.min(stack.getCount(), (kind().capacity - ammo()) / 50);
                    if (belts <= 0) {
                        player.sendOverlayMessage(Component.translatable("message.harddayz.full").withStyle(ChatFormatting.YELLOW));
                        return InteractionResult.SUCCESS;
                    }
                    if (!player.isCreative()) {
                        stack.shrink(belts);
                    }
                    entityData.set(AMMO, ammo() + belts * 50);
                    level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
                    player.sendOverlayMessage(Component.translatable("message.harddayz.loaded", ammo(), kind().capacity).withStyle(ChatFormatting.GREEN));
                    return InteractionResult.SUCCESS;
                }
                if (kind().dronePad()) {
                    n = Math.min(n, 1);
                    if (n > 0) {
                        drones.add(droneKind(stack));
                    }
                }
                if (n <= 0) {
                    player.sendOverlayMessage(Component.translatable("message.harddayz.full").withStyle(ChatFormatting.YELLOW));
                    return InteractionResult.SUCCESS;
                }
                if (!player.isCreative()) {
                    stack.shrink(n);
                }
                entityData.set(AMMO, ammo() + n);
                level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
                player.sendOverlayMessage(Component.translatable("message.harddayz.loaded", ammo(), kind().capacity).withStyle(ChatFormatting.GREEN));
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.OPERATOR_TERMINAL.get()) && kind().dronePad()) {
            if (!level().isClientSide() && player instanceof ServerPlayer sp) {
                launchControlled(sp);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.DESIGNATOR.get()) || stack.is(ModItems.TARGET_LOG.get())) {
            if (level().isClientSide()) {
                com.harddayz.client.ClientAccess.openLauncher(this);
            }
            return InteractionResult.SUCCESS;
        }
        if (kind().gunner() && !player.isSecondaryUseActive() && getPassengers().size() < seats()) {
            if (!level().isClientSide()) {
                player.startRiding(this);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive() || !getPassengers().isEmpty() || !kind().mobile) {
            if (level().isClientSide()) {
                com.harddayz.client.ClientAccess.openLauncher(this);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level().isClientSide()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    /** Приказ на пуск по точке. */
    public void order(ServerPlayer player, BlockPos t) {
        if (kind() == Kind.EW) {
            player.sendOverlayMessage(Component.translatable("message.harddayz.ew_auto").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (kind().samLike() && kind() != Kind.S400) {
            player.sendOverlayMessage(Component.translatable("message.harddayz.sam_auto").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (ammo() <= 0) {
            player.sendOverlayMessage(Component.translatable("message.harddayz.empty").withStyle(ChatFormatting.RED));
            return;
        }
        if (state != State.IDLE) {
            player.sendOverlayMessage(Component.translatable("message.harddayz.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!stationary()) {
            player.sendOverlayMessage(Component.translatable("message.harddayz.stop_first").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Vec3 tv = Vec3.atCenterOf(t);
        double dist = Math.sqrt(tv.distanceToSqr(getX(), tv.y, getZ()));
        switch (kind()) {
            case GRAD, GRAD_P, TOS -> {
                Double elev = gradElevation(tv);
                if (elev == null) {
                    player.sendOverlayMessage(Component.translatable("message.harddayz.out_of_range", (int) dist, (int) maxGradRange()).withStyle(ChatFormatting.RED));
                    return;
                }
                entityData.set(WANT_ELEV, elev.floatValue());
                entityData.set(WANT_TURRET, Mth.wrapDegrees(yawTo(tv) - getYRot()));
            }
            case SHAHED, RAMP -> {
                if (dist < 80 || dist > Drone.SHAHED_RANGE) {
                    player.sendOverlayMessage(Component.translatable("message.harddayz.out_of_range", (int) dist, Drone.SHAHED_RANGE).withStyle(ChatFormatting.RED));
                    return;
                }
                entityData.set(WANT_ELEV, 18f);
                entityData.set(WANT_TURRET, Mth.wrapDegrees(yawTo(tv) - getYRot()));
            }
            default -> {
                int max = kind() == Kind.S400 ? 1500 : kind() == Kind.HIMARS ? 1800 : com.harddayz.Config.ballisticRange();
                if (dist > max || dist < 60) {
                    player.sendOverlayMessage(Component.translatable("message.harddayz.out_of_range", (int) dist, max).withStyle(ChatFormatting.RED));
                    return;
                }
            }
        }
        target = t;
        state = State.DEPLOY;
        entityData.set(OUTRIG, kind().mobile ? 1f : 0f);
        timer = 0;
        shot = 0;
        player.sendOverlayMessage(Component.translatable("message.harddayz.order", t.getX(), t.getY(), t.getZ(), (int) dist).withStyle(ChatFormatting.GOLD));
        level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.5f);
    }

    private float yawTo(Vec3 p) {
        return (float) Math.toDegrees(Math.atan2(-(p.x - getX()), p.z - getZ()));
    }

    /** Скорость неуправляемых ракет: у «Солнцепёка» меньше — и дальность меньше. */
    private double rocketSpeed() {
        return kind() == Kind.TOS ? Missile.GRAD_SPEED * 0.72 : Missile.GRAD_SPEED;
    }

    private double maxGradRange() {
        double v = rocketSpeed();
        return v * v / Missile.Kind.GRAD.gravity;
    }

    /** Угол возвышения неуправляемой ракеты (настильная траектория), или null — не долетит. */
    private @Nullable Double gradElevation(Vec3 t) {
        double v = rocketSpeed();
        double g = Missile.Kind.GRAD.gravity;
        Vec3 from = muzzleBase();
        double x = Math.sqrt((t.x - from.x) * (t.x - from.x) + (t.z - from.z) * (t.z - from.z));
        double y = t.y - from.y;
        double disc = v * v * v * v - g * (g * x * x + 2 * y * v * v);
        if (disc < 0 || x < 20) {
            return null;
        }
        return Math.max(8, Math.toDegrees(Math.atan((v * v - Math.sqrt(disc)) / (g * x))));
    }

    // ------------------------------------------------------------------ геометрия

    private static Vec3 forward(float yawDeg, float pitchDeg) {
        double y = Math.toRadians(yawDeg);
        double p = Math.toRadians(pitchDeg);
        return new Vec3(-Math.sin(y) * Math.cos(p), Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    /** Точка вращения пакета направляющих / стрелы (совпадает с моделью). */
    private Vec3 muzzleBase() {
        return switch (kind()) {
            case GRAD -> local(0, 2.0, -1.5);
            case ISKANDER -> local(0, 1.75, -2.75);
            case SAM -> local(0, 2.35, -0.55);
            case SHAHED -> local(0, 1.95, -1.6);
            case RAMP -> local(0, 0.6, -1.2);
            case GRAD_P -> local(0, 1.05, 0);
            case SAM_POST -> local(0, 1.5, 0);
            case EW -> local(0, 2.0, -1.5);
            case MG -> local(0, 1.05, 0);
            case RADAR -> local(0, 2.0, -1.5);
            case SAM_MANUAL -> local(0, 1.25, 0);
            case S400 -> local(0, 2.35, -0.55);
            case TOS -> local(0, 2.0, -1.5);
            case HIMARS -> local(0, 1.95, -1.6);
            case PATRIOT, NASAMS -> local(0, 2.35, -0.55);
            case PICKUP_MG, PICKUP_SAM -> local(0, 2.4, -1.1);
            case BMP2, BRADLEY -> local(0, 2.2, -0.25);
            case T72 -> local(0, 1.5, 0.9);
            case ABRAMS -> local(0, 1.6, 0.9);
            case RELAY -> local(0, 2.0, 0);
        };
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tick() {
        super.tick();
        turretSmooth().tick(entityData.get(WANT_TURRET), true);
        elevation.tick(entityData.get(WANT_ELEV), false);
        gunElevation.tick(entityData.get(WANT_ELEV), false);
        erect.tick(entityData.get(WANT_ERECT), false);
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        // заряженная пусковая держит свой участок мира загруженным
        if ((ammo() > 0 || kind() == Kind.RELAY) && tickCount % 20 == 0) {
            level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.ENDER_PEARL, chunkPosition(), 2);
        }
        if (flash() > 0) {
            entityData.set(FLASH, flash() - 1);
        }
        if (kind().dronePad()) {
            String ds = String.join(",", drones.stream().map(k -> String.valueOf(k.ordinal())).toList());
            if (!ds.equals(entityData.get(DRONES))) {
                entityData.set(DRONES, ds);
            }
        }
        if (kind().samLike() && !(kind() == Kind.S400 && state != State.IDLE)) {
            tickSam(level);
            return;
        }
        if (kind().gunner()) {
            tickGunner(level);
            return;
        }
        if (kind() == Kind.RADAR) {
            tickRadar(level);
            return;
        }
        if (kind() == Kind.RELAY) {
            return; // работает самим фактом существования (см. Drone.linked)
        }
        if (kind() == Kind.EW) {
            tickEw(level);
            return;
        }
        switch (state) {
            case DEPLOY -> {
                if (outrig.near(kind().mobile ? 1f : 0f, 0.01f, false)) {
                    state = State.AIMING;
                    if (kind() == Kind.ISKANDER) {
                        entityData.set(WANT_ERECT, 1f);
                    }
                    level.playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5f, 0.4f);
                }
            }
            case AIMING -> {
                if (kind() == Kind.ISKANDER && tickCount % 20 == 0) {
                    level.playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.35f);
                }
                if (aimed()) {
                    state = State.FIRING;
                    timer = 0;
                }
            }
            case FIRING -> {
                switch (kind()) {
                    case GRAD, GRAD_P, TOS -> fireGrad(level);
                    case ISKANDER -> fireIskander(level);
                    case SHAHED, RAMP -> fireShahed(level);
                    case S400 -> fireS400Ground(level);
                    case HIMARS -> fireHimars(level);
                    default -> {
                    }
                }
            }
            case STOW -> {
                if (timer++ == 30) {
                    entityData.set(WANT_ELEV, 0f);
                    entityData.set(WANT_TURRET, 0f);
                    entityData.set(WANT_ERECT, 0f);
                }
                if (timer > 30 && turret.near(0, 0.3f, true) && elevation.near(0, 0.3f, false) && erect.near(0, 0.005f, false)) {
                    entityData.set(OUTRIG, 0f);
                    state = State.RETRACT;
                }
            }
            case RETRACT -> {
                if (outrig.near(0, 0.01f, false)) {
                    state = State.IDLE;
                }
            }
            default -> {
            }
        }
    }

    private boolean aimed() {
        return turret.near(entityData.get(WANT_TURRET), 0.4f, true) && elevation.near(entityData.get(WANT_ELEV), 0.4f, false)
                && erect.near(entityData.get(WANT_ERECT), 0.005f, false);
    }

    private void stow() {
        state = State.STOW;
        timer = 0;
    }

    private void fireGrad(ServerLevel level) {
        if (timer++ % 2 != 0) {
            return;
        }
        if (ammo() <= 0 || target == null) {
            stow();
            return;
        }
        int tube = shot++ % 40;
        int row = kind() == Kind.GRAD_P ? 0 : kind() == Kind.TOS ? (tube % 24) / 8 : tube / 10;
        int col = kind() == Kind.GRAD_P ? 0 : kind() == Kind.TOS ? tube % 8 : tube % 10;
        // настоящий разброс РСЗО: каждая ракета чуть по-своему по направлению, углу и скорости
        float yaw = getYRot() + turret.value + (float) random.nextGaussian() * 2.6f;
        Vec3 dir = forward(yaw, elevation.value + (float) random.nextGaussian() * 1.1f);
        double y = Math.toRadians(yaw);
        Vec3 right = new Vec3(-Math.cos(y), 0, -Math.sin(y));
        Vec3 up = forward(yaw, elevation.value + 90);
        Vec3 muzzle = kind() == Kind.GRAD_P ? muzzleBase().add(dir.scale(1.9))
                : muzzleBase().add(dir.scale(3.3)).add(right.scale((col - 4.5) * 0.225)).add(up.scale(0.25 + row * 0.25));
        Vec3 vel = dir.scale(rocketSpeed() * (1 + random.nextGaussian() * 0.022));
        Missile m = Missile.launch(level, Missile.Kind.GRAD, muzzle, vel, Vec3.atCenterOf(target), this).owner(owner);
        if (kind() == Kind.TOS) {
            m.thermobaric();
        }
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 3);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, muzzle.x, muzzle.y, muzzle.z, 10, 0.4, 0.4, 0.4, 0.05);
        Vec3 back = muzzleBase().subtract(dir.scale(1.0));
        level.sendParticles(ParticleTypes.CLOUD, true, true, back.x, back.y, back.z, 14, 0.6, 0.3, 0.6, 0.12);
    }

    /** С-400 по наземной цели: одна ракета, большой разброс, слабая БЧ. */
    private void fireS400Ground(ServerLevel level) {
        if (timer++ % 40 != 0) {
            return;
        }
        if (ammo() <= 0 || target == null || shot >= 1) {
            stow();
            return;
        }
        shot++;
        Vec3 aim = Vec3.atCenterOf(target).add(random.nextGaussian() * 22, 0, random.nextGaussian() * 22);
        Vec3 g = muzzleBase();
        Missile.launch(level, Missile.Kind.BALLISTIC, g.add(0, 2.5, 0), new Vec3(0, 0.6, 0), aim, this).owner(owner).powerMul(0.35f).look(2);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 8);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, g.x, g.y, g.z, 30, 1.5, 0.4, 1.5, 0.05);
    }

    /** HIMARS: две точные ракеты с интервалом. */
    private void fireHimars(ServerLevel level) {
        if (timer++ % 25 != 0) {
            return;
        }
        if (ammo() <= 0 || target == null || shot >= 2) {
            stow();
            return;
        }
        shot++;
        Vec3 aim = Vec3.atCenterOf(target).add(random.nextGaussian() * 2.5, 0, random.nextGaussian() * 2.5);
        Vec3 g = muzzleBase();
        Missile.launch(level, Missile.Kind.BALLISTIC, g.add(0, 1.5, 0), new Vec3(0, 0.8, 0), aim, this).owner(owner).powerMul(0.5f).look(1);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 6);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, g.x, g.y, g.z, 30, 1.2, 0.4, 1.2, 0.05);
        level.playSound(null, g.x, g.y, g.z, com.harddayz.registry.ModSounds.MISSILE_LAUNCH.get(), SoundSource.HOSTILE, 5f, 1.1f);
    }

    private void fireIskander(ServerLevel level) {
        timer++;
        Vec3 g = muzzleBase();
        if (timer == 1) {
            level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 3f, 0.5f);
        }
        if (timer < 50) {
            if (timer > 20) {
                level.sendParticles(ParticleTypes.CLOUD, true, true, g.x, g.y + 0.5, g.z, 8, 1.2, 0.3, 1.2, 0.06);
            }
            return;
        }
        if (ammo() > 0 && target != null) {
            Missile.launch(level, Missile.Kind.BALLISTIC, g.add(0, 3.8, 0), new Vec3(0, 0.4, 0), Vec3.atCenterOf(target), this).owner(owner);
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 10);
            level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, g.x, g.y, g.z, 50, 3.0, 0.5, 3.0, 0.06);
            level.sendParticles(ParticleTypes.EXPLOSION, true, true, g.x, g.y, g.z, 8, 1.8, 0.3, 1.8, 0);
            level.sendParticles(ParticleTypes.FLAME, true, true, g.x, g.y, g.z, 80, 2.4, 0.3, 2.4, 0.18);
            level.playSound(null, g.x, g.y, g.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4f, 0.5f);
        }
        stow();
    }

    private void fireShahed(ServerLevel level) {
        if (timer++ % 30 != 0) {
            return;
        }
        if (ammo() <= 0 || target == null) {
            stow();
            return;
        }
        float yaw = getYRot() + turret.value;
        Vec3 dir = forward(yaw, elevation.value);
        Vec3 muzzle = muzzleBase().add(dir.scale(kind() == Kind.RAMP ? 2.6 : 3.6)).add(0, 0.4, 0);
        // у каждого дрона своя точка рядом с целью
        double ang = random.nextDouble() * Math.PI * 2;
        double off = 4 + random.nextDouble() * 9;
        Vec3 aim = Vec3.atCenterOf(target).add(Math.cos(ang) * off, 0, Math.sin(ang) * off);
        Drone.Kind dk = drones.isEmpty() ? Drone.Kind.SHAHED : drones.remove(0);
        if (controlOp != null) {
            // пуск под ручное управление: один дрон, курс — прямо на выбранную точку
            Drone d = Drone.launchShahed(level, muzzle, dir, Vec3.atCenterOf(target), owner, dk);
            if (level.getPlayerByUUID(controlOp) instanceof ServerPlayer op && op.distanceTo(this) < Drone.SHAHED_LINK_RANGE) {
                d.takeControl(op);
            }
            controlOp = null;
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 6);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, muzzle.x, muzzle.y, muzzle.z, 20, 0.6, 0.3, 0.6, 0.04);
            level.sendParticles(ParticleTypes.FLAME, true, true, muzzle.x, muzzle.y, muzzle.z, 25, 0.4, 0.2, 0.4, 0.1);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 3f, 0.6f);
            stow();
            return;
        }
        Drone.launchShahed(level, muzzle, dir, aim, owner, dk);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 6);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, muzzle.x, muzzle.y, muzzle.z, 20, 0.6, 0.3, 0.6, 0.04);
        level.sendParticles(ParticleTypes.FLAME, true, true, muzzle.x, muzzle.y, muzzle.z, 25, 0.4, 0.2, 0.4, 0.1);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 3f, 0.6f);
    }

    /** Пульт оператора по пусковой: дрон стартует и сразу переходит под ручное управление. */
    private void launchControlled(ServerPlayer op) {
        if (ammo() <= 0) {
            op.sendOverlayMessage(Component.translatable("message.harddayz.empty").withStyle(ChatFormatting.RED));
            return;
        }
        if (state != State.IDLE) {
            op.sendOverlayMessage(Component.translatable("message.harddayz.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!stationary()) {
            op.sendOverlayMessage(Component.translatable("message.harddayz.stop_first").withStyle(ChatFormatting.YELLOW));
            return;
        }
        // как обычный пуск: опоры, разворот, подъём направляющей — и только потом старт под управлением
        BlockPos slotTarget = slot(selectedSlot());
        Vec3 fwd = forward(getYRot() + turret.value, 0);
        target = slotTarget != null ? slotTarget : BlockPos.containing(position().add(fwd.multiply(1, 0, 1).normalize().scale(1500)));
        controlOp = op.getUUID();
        entityData.set(WANT_ELEV, 18f);
        entityData.set(WANT_TURRET, Mth.wrapDegrees(yawTo(Vec3.atCenterOf(target)) - getYRot()));
        state = State.DEPLOY;
        entityData.set(OUTRIG, kind().mobile ? 1f : 0f);
        timer = 0;
        shot = 0;
        op.sendOverlayMessage(Component.translatable("message.harddayz.control_prep").withStyle(ChatFormatting.GOLD));
        level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.5f);
    }

    // ------------------------------------------------------------------ ЗРК

    private int samRange() {
        return switch (kind()) {
            case S400 -> 480;
            case PATRIOT -> 520;
            case NASAMS -> 340;
            default -> 260;
        };
    }

    private void tickSam(ServerLevel level) {
        if (samCooldown > 0) {
            samCooldown--;
        }
        Entity t = samTarget >= 0 ? level.getEntity(samTarget) : null;
        if (t == null || !t.isAlive() || t.distanceTo(this) > samRange()) {
            samTarget = -1;
            t = null;
            if (tickCount % 5 == 0) {
                t = findThreat(level);
                if (t != null) {
                    samTarget = t.getId();
                    level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 2f, 2f);
                }
            }
        }
        if (t == null) {
            entityData.set(WANT_ELEV, 12f);
            return;
        }
        Vec3 p = t.position();
        entityData.set(WANT_TURRET, Mth.wrapDegrees(yawTo(p) - getYRot()));
        Vec3 base = muzzleBase();
        double h = Math.sqrt((p.x - base.x) * (p.x - base.x) + (p.z - base.z) * (p.z - base.z));
        entityData.set(WANT_ELEV, (float) Mth.clamp(Math.toDegrees(Math.atan2(p.y - base.y, h)), 15, 80));
        if (samCooldown == 0 && ammo() > 0 && !engaged.contains(t.getId())
                && Math.abs(Mth.wrapDegrees(entityData.get(WANT_TURRET) - samTurret.value)) < 12) {
            Vec3 dir = forward(getYRot() + samTurret.value, elevation.value);
            boolean left = shot++ % 2 == 0;
            double y = Math.toRadians(getYRot() + samTurret.value);
            Vec3 right = new Vec3(-Math.cos(y), 0, -Math.sin(y));
            Vec3 muzzle = kind() == Kind.SAM_POST ? base.add(dir.scale(1.6)).add(right.scale(left ? -0.35 : 0.35))
                    : base.add(dir.scale(2.4)).add(right.scale(left ? -1.1 : 1.1));
            Missile.launch(level, Missile.Kind.SAM, muzzle, dir.scale(kind() == Kind.S400 || kind() == Kind.PATRIOT ? 2.0 : 1.4), p, this).hunt(t)
                    .owner(owner).bonus(kind() == Kind.PATRIOT && t instanceof Missile ? 0.6f : kind() == Kind.NASAMS ? 0.05f : 0f)
                    .look(kind() == Kind.S400 ? 2 : kind() == Kind.PATRIOT ? 3 : kind() == Kind.NASAMS ? 4 : 0);
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 4);
            engaged.add(t.getId());
            samCooldown = 20;
            level.sendParticles(ParticleTypes.CLOUD, true, true, muzzle.x, muzzle.y, muzzle.z, 20, 0.5, 0.5, 0.5, 0.1);
        }
        if (engaged.size() > 64) {
            engaged.clear();
        }
    }

    private @Nullable Entity findThreat(ServerLevel level) {
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, new AABB(position(), position()).inflate(250, 300, 250), e -> e instanceof Threat)) {
            Threat th = (Threat) e;
            UUID o = th.owner();
            if (!th.isThreat() || engaged.contains(e.getId()) || com.harddayz.war.War.allied(owner, o) || e.getY() < getY() + 6) {
                continue;
            }
            if (kind() == Kind.PATRIOT && !(e instanceof Missile)) {
                continue; // ПРО не тратит ракеты на дроны
            }
            double d = e.distanceToSqr(this);
            if (kind() == Kind.PATRIOT && e instanceof Missile mm && mm.kind() == Missile.Kind.BALLISTIC) {
                d *= 0.1; // баллистика — в первую очередь
            }
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) {
            // цель видит РЛС сети — ЗРК бьёт дальше своей станции
            String key = com.harddayz.war.RadarNet.key(owner);
            long now = level.getGameTime();
            for (var c : com.harddayz.war.RadarNet.contacts(key, now)) {
                Entity e = level.getEntity(c.id);
                if (e == null || !e.isAlive() || engaged.contains(e.getId()) || !com.harddayz.war.RadarNet.tracked(key, e, now)) {
                    continue;
                }
                double d = e.distanceToSqr(this);
                if (d < 450 * 450 && d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ урон и подбор

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() instanceof Player p && source.getDirectEntity() == p && p.isShiftKeyDown() && getPassengers().isEmpty() && health() > 0) {
            spawnAtLocation(level, new ItemStack(kind().item()));
            if (kind().dronePad()) {
                for (Drone.Kind k : drones) {
                    spawnAtLocation(level, new ItemStack(droneItem(k)));
                }
            } else if (kind() != Kind.EW) {
                int left = ammo();
                while (left > 0) {
                    int n = Math.min(left, kind().ammo().getDefaultMaxStackSize());
                    spawnAtLocation(level, new ItemStack(kind().ammo(), n));
                    left -= n;
                }
            }
            discard();
            return true;
        }
        float hp = health() - damage;
        entityData.set(HEALTH, hp);
        if (hp <= 0) {
            Vec3 c = position().add(0, 1.2, 0);
            com.harddayz.war.War.Faction lostBy = com.harddayz.war.War.of(owner);
            if (lostBy != null) {
                lostBy.lost++;
                UUID by = source.getEntity() instanceof com.harddayz.entity.Threat t ? t.owner()
                        : source.getEntity() instanceof Player pl ? pl.getUUID() : source.getEntity() instanceof Vehicle v ? v.owner() : null;
                com.harddayz.war.War.Faction killer = com.harddayz.war.War.of(by);
                if (killer != null && killer != lostBy) {
                    killer.destroyed++;
                }
                com.harddayz.war.War.save();
            }
            ejectPassengers();
            discard();
            float bk = switch (kind()) {
                case ISKANDER -> ammo() * 7f;
                case GRAD, GRAD_P, TOS -> ammo() * 0.18f;
                case HIMARS, S400, PATRIOT, SAM, NASAMS -> ammo() * 0.9f;
                case MG, PICKUP_MG -> 0;
                default -> ammo() * 0.12f;
            };
            Blast.detonate(level, c, 1.2f + Math.min(12f, bk), bk > 0.5f, this);
        }
        return true;
    }

    // ------------------------------------------------------------------ служебное

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(KIND, 0);
        builder.define(AMMO, 0);
        builder.define(WANT_TURRET, 0f);
        builder.define(WANT_ELEV, 0f);
        builder.define(WANT_ERECT, 0f);
        builder.define(FLASH, 0);
        builder.define(DRONES, "");
        builder.define(SLOTS, ";;;;");
        builder.define(SLOT, 0);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(KIND, input.getIntOr("kind", 0));
        entityData.set(AMMO, input.getIntOr("ammo", 0));
        entityData.set(HEALTH, input.getFloatOr("health", kind().health));
        input.getString("owner").ifPresent(s -> owner = UUID.fromString(s));
        entityData.set(SLOTS, input.getStringOr("slots", ";;;;"));
        entityData.set(SLOT, input.getIntOr("slot", 0));
        drones.clear();
        for (String k : input.getStringOr("drones", "").split(",")) {
            if (!k.isEmpty()) {
                drones.add(Drone.Kind.valueOf(k));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("kind", entityData.get(KIND));
        output.putInt("ammo", ammo());
        output.putFloat("health", health());
        if (owner != null) {
            output.putString("owner", owner.toString());
        }
        output.putString("drones", String.join(",", drones.stream().map(Enum::name).toList()));
        output.putString("slots", entityData.get(SLOTS));
        output.putInt("slot", selectedSlot());
    }

    // ------------------------------------------------------------------ стрелковые установки

    private int gunCooldown;

    private void tickGunner(ServerLevel level) {
        if (gunCooldown > 0) {
            gunCooldown--;
        }
        if (gunnerPassenger() instanceof Player p) {
            entityData.set(WANT_TURRET, Mth.wrapDegrees(p.getYRot() - getYRot()));
            entityData.set(WANT_ELEV, Mth.clamp(-p.getXRot(), -12, 80));
        }
    }

    /** Цель в прицеле ручного ЗРК: угроза в конусе 7° и до 220 блоков. */
    public static @Nullable Entity lockTarget(Level level, Vec3 eye, Vec3 look, @Nullable UUID shooter) {
        Entity best = null;
        double bestAng = Math.toRadians(7);
        for (Entity e : level.getEntities((Entity) null, new AABB(eye, eye).inflate(220), e -> e instanceof Threat)) {
            Threat th = (Threat) e;
            if (!th.isThreat() || (shooter != null && com.harddayz.war.War.allied(shooter, th.owner()))) {
                continue;
            }
            Vec3 to = e.position().subtract(eye);
            double d = to.length();
            if (d < 4 || d > 220) {
                continue;
            }
            double ang = Math.acos(Mth.clamp(to.scale(1 / d).dot(look), -1, 1));
            if (ang < bestAng) {
                bestAng = ang;
                best = e;
            }
        }
        return best;
    }

    /** ЛКМ стрелка. */
    public void gunnerFire(ServerPlayer p) {
        if (!(level() instanceof ServerLevel level) || gunnerPassenger() != p || gunCooldown > 0) {
            return;
        }
        if (ammo() <= 0) {
            gunCooldown = 10;
            level.playSound(null, blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.4f);
            return;
        }
        Vec3 look = p.getLookAngle();
        Vec3 muzzle = muzzleBase().add(look.scale(1.4));
        if (kind().lockOn()) {
            Entity t = lockTarget(level, p.getEyePosition(), look, p.getUUID());
            if (t == null) {
                p.sendOverlayMessage(Component.translatable("message.harddayz.no_lock").withStyle(ChatFormatting.RED));
                gunCooldown = 6;
                return;
            }
            Missile.launch(level, Missile.Kind.SAM, muzzle, look.scale(1.4), t.position(), this).hunt(t).owner(p.getUUID());
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 4);
            gunCooldown = 30;
            level.sendParticles(ParticleTypes.CLOUD, true, true, muzzle.x, muzzle.y, muzzle.z, 20, 0.4, 0.4, 0.4, 0.1);
            return;
        }
        // пулемёт / автопушка: мгновенный выстрел с небольшим разбросом
        boolean cannon = kind().cannon();
        gunCooldown = kind() == Kind.T72 ? 90 : kind() == Kind.ABRAMS ? 75 : kind() == Kind.BMP2 ? 6 : kind() == Kind.BRADLEY ? 4 : 2;
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 2);
        float spread = kind().tank() ? 0.12f : cannon ? 0.35f : 0.4f;
        Vec3 dir = net.minecraft.world.phys.Vec3.directionFromRotation(p.getXRot() + (float) random.nextGaussian() * spread,
                p.getYRot() + (float) random.nextGaussian() * spread);
        Vec3 end = muzzle.add(dir.scale(cannon ? 300 : 260));
        var block = level.clip(new net.minecraft.world.level.ClipContext(muzzle, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : block.getLocation();
        var hit = rayEntity(level, this, muzzle, stop, e -> e != p && e != this);
        boolean hitBlock = hit == null && block.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
        if (hit != null) {
            stop = hit.getLocation();
            Entity e = hit.getEntity();
            float dmg = e instanceof Drone ? 3.5f : e instanceof Vehicle ? 2.5f : 5f;
            if (cannon) {
                dmg = kind().tank() ? 70f : kind() == Kind.BMP2 ? 9f : 7f;
            }
            e.hurtServer(level, damageSources().playerAttack(p), dmg);
        }
        if (cannon && (hit != null || hitBlock)) {
            Blast.detonate(level, stop, kind().tank() ? 3.2f : kind() == Kind.BMP2 ? 1.4f : 1.0f, true, this); // фугасный — рушит укрытия
        }
        if (kind().tank()) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6f, 0.6f);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3f, 1.6f);
            level.sendParticles(ParticleTypes.EXPLOSION, true, true, muzzle.x, muzzle.y, muzzle.z, 3, 0.3, 0.3, 0.3, 0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, muzzle.x, muzzle.y, muzzle.z, 20, 0.8, 0.4, 0.8, 0.03);
        } else {
            level.playSound(null, getX(), getY(), getZ(), com.harddayz.registry.ModSounds.MG_FIRE.get(), SoundSource.PLAYERS, 4f,
                    (kind().cannon() ? 0.6f : 0.95f) + random.nextFloat() * 0.1f);
        }
        var fx = new com.harddayz.network.Net.Tracer(muzzle.x, muzzle.y, muzzle.z, stop.x, stop.y, stop.z, hitBlock);
        for (ServerPlayer pl : level.players()) {
            if (pl.distanceToSqr(this) < 250 * 250) {
                com.harddayz.network.Net.toPlayer(pl, fx);
            }
        }
    }

    // ------------------------------------------------------------------ РЛС

    private void tickRadar(ServerLevel level) {
        if (tickCount % 10 != 0) {
            return;
        }
        String key = com.harddayz.war.RadarNet.key(owner);
        long now = level.getGameTime();
        if (tickCount % 20 == 0) {
            com.harddayz.war.RadarNet.radarAlive(key, now);
        }
        int r = com.harddayz.war.RadarNet.RADAR_RANGE;
        for (Entity e : level.getEntities((Entity) null, new AABB(position(), position()).inflate(r, 600, r), e -> e instanceof Threat)) {
            Threat th = (Threat) e;
            if (!th.isThreat() || com.harddayz.war.War.allied(owner, th.owner()) || e.getY() < getY() - 4) {
                continue; // низко у земли РЛС не видит
            }
            com.harddayz.war.RadarNet.report(key, e, now);
        }
    }

    // ------------------------------------------------------------------ РЭБ

    public static final int EW_RADIUS = 128;

    private void tickEw(ServerLevel level) {
        entityData.set(WANT_TURRET, (float) ((tickCount * 3) % 360)); // антенны вращаются
        entityData.set(WANT_ELEV, 0f);
        if (tickCount % 10 != 0) {
            return;
        }
        for (Drone d : level.getEntitiesOfClass(Drone.class, new AABB(position(), position()).inflate(EW_RADIUS))) {
            if (com.harddayz.war.War.allied(owner, d.owner())) {
                continue;
            }
            double dist = d.distanceTo(this);
            if (dist <= EW_RADIUS * 1.3) {
                d.interfere(level, (float) Math.min(1, 1.3 - dist / EW_RADIUS)); // ближе — сильнее
            }
        }
        if (tickCount % 40 == 0) {
            level.playSound(null, blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.2f, 0.6f);
        }
    }
}
