package com.harddayz.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

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
import net.minecraft.nbt.CompoundTag;
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
        /** Удалён из мода: значение оставлено только ради порядка в старых сохранениях. */
        T72(22, 520),
        /** Удалён из мода: значение оставлено только ради порядка в старых сохранениях. */
        ABRAMS(20, 600),
        /** Ка-52: ударный — пушка 2А42 (ЛКМ) и блоки НУРС С-8 (ПКМ). */
        KA52(40, 260),
        /** Ми-8АМТШ: транспортно-боевой — пилот и 3 десантника, НУРС. */
        MI8(32, 300),
        /** Миномёт 82 мм: навесной огонь по точке, 3 мины за приказ. */
        MORTAR(12, 40, false),
        /** «Точка-У»: тактическая баллистика — ближе и слабее «Искандера», дешевле. */
        TOCHKA(1, 200),
        /** «Смерч»: тяжёлая РСЗО 300 мм, 12 ракет — далеко и мощно. */
        SMERCH(12, 240),
        /** «Ураган»: РСЗО 220 мм, 16 ракет — между «Градом» и «Смерчем». */
        URAGAN(16, 220),
        /** «Калибр» (береговой): 4 крылатые ракеты, летят низко над землёй далеко. */
        KALIBR(4, 220),
        /** Надувной макет «Града»: ложная цель для разведки, дронов и ракет. Ничего не умеет, лопается от попадания. */
        DECOY(0, 30, false);

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
            return this != EW && this != RADAR && this != RELAY && (s.is(ammo()) || this == MORTAR && s.is(ModItems.MORTAR_ILLUM.get()));
        }

        /** Стрелковые установки: на них садятся и стреляют сами. */
        public boolean gunner() {
            return this == SAM_MANUAL || this == MG || this == PICKUP_MG || this == PICKUP_SAM || this == BMP2 || this == BRADLEY;
        }

        /** Убраны из мода (БМП-2, танки и вертолёты): значения оставлены только ради старых сохранений, такие машины исчезают. */
        public boolean removed() {
            return this == BMP2 || this == T72 || this == ABRAMS || this == KA52 || this == MI8;
        }

        public boolean heli() {
            return this == KA52 || this == MI8;
        }

        /** Стрельба только после захвата цели (ПЗРК). */
        public boolean lockOn() {
            return this == SAM_MANUAL || this == PICKUP_SAM;
        }

        public boolean cannon() {
            return this == BMP2 || this == BRADLEY;
        }

        public Item ammo() {
            return switch (this) {
                case GRAD -> ModItems.GRAD_ROCKET.get();
                case ISKANDER -> ModItems.BALLISTIC_MISSILE.get();
                case TOCHKA -> ModItems.TOCHKA_MISSILE.get();
                case SMERCH -> ModItems.SMERCH_ROCKET.get();
                case URAGAN -> ModItems.URAGAN_ROCKET.get();
                case KALIBR -> ModItems.KALIBR_MISSILE.get();
                case SAM -> ModItems.SAM_MISSILE.get();
                case SHAHED, RAMP -> ModItems.SHAHED_DRONE.get();
                case GRAD_P -> ModItems.GRAD_ROCKET.get();
                case SAM_POST -> ModItems.SAM_MISSILE.get();
                case EW -> ModItems.DESIGNATOR.get();
                case SAM_MANUAL -> ModItems.SAM_MISSILE.get();
                case MG -> ModItems.MG_BELT.get();
                case RADAR -> ModItems.DESIGNATOR.get();
                case S400 -> ModItems.S400_MISSILE.get();
                case TOS -> ModItems.TOS_ROCKET.get();
                case HIMARS -> ModItems.HIMARS_ROCKET.get();
                case PATRIOT -> ModItems.PATRIOT_MISSILE.get();
                case NASAMS -> ModItems.NASAMS_MISSILE.get();
                case PICKUP_MG -> ModItems.MG_BELT.get();
                case PICKUP_SAM -> ModItems.SAM_MISSILE.get();
                case BMP2, BRADLEY -> ModItems.CANNON_SHELLS.get();
                case T72, ABRAMS, KA52, MI8 -> net.minecraft.world.item.Items.AIR; // удалены
                case MORTAR -> ModItems.MORTAR_SHELL.get();
                case RELAY -> ModItems.DESIGNATOR.get();
                case DECOY -> net.minecraft.world.item.Items.AIR;
            };
        }

        public Item item() {
            return switch (this) {
                case GRAD -> ModItems.GRAD.get();
                case ISKANDER -> ModItems.ISKANDER.get();
                case TOCHKA -> ModItems.TOCHKA.get();
                case SMERCH -> ModItems.SMERCH.get();
                case URAGAN -> ModItems.URAGAN.get();
                case KALIBR -> ModItems.KALIBR.get();
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
                case T72, ABRAMS, KA52, MI8 -> net.minecraft.world.item.Items.AIR; // удалены
                case MORTAR -> ModItems.MORTAR.get();
                case RELAY -> ModItems.RELAY.get();
                case DECOY -> ModItems.DECOY.get();
            };
        }
    }

    /** Вид дрона по предмету-боеприпасу, или null. */
    public static @javax.annotation.Nullable Drone.Kind droneKind(ItemStack s) {
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
        if (s.is(ModItems.GERAN5.get())) {
            return Drone.Kind.GERAN5;
        }
        if (s.is(ModItems.BANDEROL.get())) {
            return Drone.Kind.BANDEROL;
        }
        return null;
    }

    private static Item droneItem(Drone.Kind k) {
        return switch (k) {
            case SHAHED_238 -> ModItems.SHAHED_238.get();
            case SHAHED_131 -> ModItems.SHAHED_131.get();
            case GERBERA -> ModItems.GERBERA.get();
            case GERAN5 -> ModItems.GERAN5.get();
            case BANDEROL -> ModItems.BANDEROL.get();
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
    /** Клиент: когда отлетела крышка каждого контейнера (тик), -1 — на месте. */
    public final float[] capLaunched = {-1, -1, -1, -1, -1, -1, -1, -1};
    /** Клиент: боезапас на прошлом кадре — по убыли видно, что был пуск. */
    public int capLastAmmo = -1;

    /** Сгоревший остов: секунд с момента гибели, 0 — живая машина. */
    private static final EntityDataAccessor<Integer> WRECK = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.INT);
    /** Через сколько тиков рванёт боекомплект (-1 — нечему). */
    private int cookoff = -1;
    private float cookPower;
    private int wreckTicks;

    public int wreck() {
        return entityData.get(WRECK);
    }

    public boolean wrecked() {
        return wreck() > 0;
    }

    /** Маскировочная сеть: 0 — нет, 1 — накрыта и не видна разведке, 2 — накрыта, но выдала себя пуском. */
    private static final EntityDataAccessor<Integer> CAMO = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.INT);
    private int revealUntil;

    public int camo() {
        return entityData.get(CAMO);
    }

    /** Под сетью и давно не стреляла: разведка, тепловизор и самонаведение её не видят. */
    public boolean hidden() {
        return camo() == 1 || camo() == 3;
    }

    /** Своя сеть на технике (не навес из блоков). */
    public boolean netOn() {
        return camo() == 1 || camo() == 2;
    }

    /** Стоит под навесом из маскировочных сетей (блоки). */
    private boolean underNetBlocks() {
        var c = blockPosition();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 6; dy++) {
                    var st = level().getBlockState(c.offset(dx, dy, dz));
                    if (st.is(com.harddayz.registry.ModBlocks.CAMO_NET.get()) || st.is(com.harddayz.registry.ModBlocks.CAMO_NET_WALL.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final EntityDataAccessor<Integer> FLASH = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.INT);
    /** Сохранённые цели пульта: 5 слотов «x,y,z» через «;» (пустой — нет цели). */
    private static final EntityDataAccessor<String> SLOTS = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.STRING);
    /** Типы дронов на направляющих (для моделей), порядковые номера через запятую. */
    private static final EntityDataAccessor<String> DRONES = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.STRING);
    /** Топливо в баке (литры). Одно для всей техники, бак разный. */
    private static final EntityDataAccessor<Float> FUEL = SynchedEntityData.defineId(Launcher.class, EntityDataSerializers.FLOAT);

    public boolean usesFuel() {
        return kind().mobile && !kind().samLike() || kind() == Kind.S400 || kind() == Kind.PATRIOT || kind() == Kind.NASAMS;
    }

    /** Объём бака. */
    public float tank() {
        return switch (kind()) {
            case PICKUP_MG, PICKUP_SAM -> 80;
            case BMP2, BRADLEY -> 120;
            case TOS -> 160;
            case KA52 -> 180;
            case MI8 -> 220;
            case ISKANDER, S400, PATRIOT -> 140;
            default -> 110;
        };
    }

    /** Расход за тик работы мотора на ходу. */
    private float burn() {
        return switch (kind()) {
            case PICKUP_MG, PICKUP_SAM -> 0.008f;
            case TOS, BMP2, BRADLEY -> 0.02f;
            case KA52, MI8 -> 0.035f;
            default -> 0.014f;
        };
    }

    public float fuel() {
        return entityData.get(FUEL);
    }

    public void refuel(float liters) {
        entityData.set(FUEL, Math.min(tank(), fuel() + liters));
    }

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
        entityData.set(FUEL, tank());
        refreshDimensions();
    }

    /** Модель больше хитбокса и видна издалека — не прятать её раньше времени. */
    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 320 * 320;
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(6, 6, 6);
    }

    /** Хитбокс по размеру техники: пулемёт и ПЗРК маленькие, ТОС крупнее. */
    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return switch (kind()) {
            case MG, SAM_MANUAL, GRAD_P, MORTAR -> net.minecraft.world.entity.EntityDimensions.scalable(1.1f, 1.3f);
            case RELAY -> net.minecraft.world.entity.EntityDimensions.scalable(1.4f, 3.5f);
            case SAM_POST -> net.minecraft.world.entity.EntityDimensions.scalable(1.6f, 1.6f);
            case PICKUP_MG, PICKUP_SAM -> net.minecraft.world.entity.EntityDimensions.scalable(2.0f, 2.0f);
            case RAMP -> net.minecraft.world.entity.EntityDimensions.scalable(2.4f, 1.6f);
            case KA52, MI8 -> net.minecraft.world.entity.EntityDimensions.scalable(3.0f, 2.6f);
            case BMP2, BRADLEY, TOS -> net.minecraft.world.entity.EntityDimensions.scalable(3.2f, 2.3f);
            default -> net.minecraft.world.entity.EntityDimensions.scalable(3.0f, 2.6f);
        };
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (KIND.equals(key)) {
            refreshDimensions();
        }
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
            default -> new Vec3(0, 1, 0);
        };
    }

    @Override
    public VehicleBody body() {
        return kind().removed() ? VehicleBody.DEFAULT : LauncherGeometry.body(kind());
    }

    @Override
    protected boolean burning() {
        return !wrecked() || wreck() < 180; // остов горит три минуты, потом только тлеет
    }

    @Override
    protected boolean canDrive() {
        if (wrecked()) {
            return false;
        }
        if (usesFuel() && fuel() <= 0) {
            return false; // бак пуст
        }
        return kind().mobile && super.canDrive();
    }

    /** Боевая машина с отдельными местами водителя и стрелка. */
    public boolean crewed() {
        return kind().gunner() && kind().mobile;
    }

    @Override
    protected int seats() {
        return kind() == Kind.MI8 ? 4 : kind() == Kind.KA52 ? 2 : crewed() ? 2 : 1;
    }

    @Override
    protected Vec3 seat(int i) {
        if (kind().heli()) {
            return switch (i) {
                case 0 -> kind() == Kind.KA52 ? new Vec3(-0.3, 0.75, 1.6) : new Vec3(-0.35, 0.6, 2.2);
                case 1 -> kind() == Kind.KA52 ? new Vec3(0.3, 0.75, 1.6) : new Vec3(0, 0.55, 0.6);
                case 2 -> new Vec3(-0.4, 0.55, -0.6);
                default -> new Vec3(0.4, 0.55, -0.6);
            };
        }
        if (!crewed() || i == 0) {
            return crewed() ? switch (kind()) {
                case BMP2, BRADLEY -> new Vec3(-0.5, 1.2, 1.9);
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
            case KA52, MI8 -> 0.25;
            default -> 0.38;
        };
    }

    @Override
    protected double accel() {
        return switch (kind()) {
            case PICKUP_MG, PICKUP_SAM -> 0.012;
            case BMP2, BRADLEY -> 0.006;
            case ISKANDER, PATRIOT, S400 -> 0.004;
            default -> 0.005;
        };
    }

    /** Луч по сущностям: у техники хитбокс чуть шире, чтобы снаряд не проходил «сквозь» модель. */
    public static @javax.annotation.Nullable net.minecraft.world.phys.EntityHitResult rayEntity(ServerLevel level, Entity shooter, Vec3 from, Vec3 to,
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
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (wrecked() || kind() == Kind.DECOY) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModItems.CAMO_NET.get()) && !netOn()) {
            if (!level().isClientSide()) {
                entityData.set(CAMO, 1);
                revealUntil = 0;
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                level().playSound(null, blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.5f, 0.7f);
                player.displayClientMessage(Component.translatable("message.harddayz.camo_on").withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (netOn() && stack.isEmpty() && player.isSecondaryUseActive()) {
            if (!level().isClientSide()) {
                entityData.set(CAMO, 0);
                if (!player.getInventory().add(new ItemStack(ModItems.CAMO_NET.get()))) {
                    spawnAtLocation(new ItemStack(ModItems.CAMO_NET.get()));
                }
                level().playSound(null, blockPosition(), SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1.5f, 0.7f);
            }
            return InteractionResult.SUCCESS;
        }
        if (kind() == Kind.MORTAR && !player.isSecondaryUseActive() && (stack.isEmpty() || kind().accepts(stack))) {
            if (hand == InteractionHand.MAIN_HAND && !level().isClientSide() && player instanceof ServerPlayer sp) {
                if (stack.isEmpty()) {
                    mortarAim(sp);
                } else {
                    mortarDrop(sp, stack);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (kind().accepts(stack)) {
            if (!level().isClientSide()) {
                int n = Math.min(kind().capacity - ammo(), stack.getCount());
                if (kind() == Kind.MG || kind() == Kind.PICKUP_MG) {
                    int belts = Math.min(stack.getCount(), (kind().capacity - ammo()) / 50);
                    if (belts <= 0) {
                        player.displayClientMessage(Component.translatable("message.harddayz.full").withStyle(ChatFormatting.YELLOW), true);
                        return InteractionResult.SUCCESS;
                    }
                    if (!player.isCreative()) {
                        stack.shrink(belts);
                    }
                    entityData.set(AMMO, ammo() + belts * 50);
                    level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
                    player.displayClientMessage(Component.translatable("message.harddayz.loaded", ammo(), kind().capacity).withStyle(ChatFormatting.GREEN), true);
                    return InteractionResult.SUCCESS;
                }
                if (kind().dronePad()) {
                    n = Math.min(n, 1);
                    if (n > 0) {
                        drones.add(droneKind(stack));
                    }
                }
                if (n <= 0) {
                    player.displayClientMessage(Component.translatable("message.harddayz.full").withStyle(ChatFormatting.YELLOW), true);
                    return InteractionResult.SUCCESS;
                }
                if (!player.isCreative()) {
                    stack.shrink(n);
                }
                entityData.set(AMMO, ammo() + n);
                level().playSound(null, blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
                player.displayClientMessage(Component.translatable("message.harddayz.loaded", ammo(), kind().capacity).withStyle(ChatFormatting.GREEN), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModItems.JERRYCAN.get()) && usesFuel()) {
            if (!level().isClientSide()) {
                if (fuel() >= tank() - 0.5f) {
                    player.displayClientMessage(Component.translatable("message.harddayz.tank_full").withStyle(ChatFormatting.YELLOW), true);
                } else {
                    refuel(40);
                    if (!player.isCreative()) {
                        stack.shrink(1);
                        if (!player.getInventory().add(new ItemStack(ModItems.EMPTY_JERRYCAN.get()))) {
                            spawnAtLocation(new ItemStack(ModItems.EMPTY_JERRYCAN.get()));
                        }
                    }
                    level().playSound(null, blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1f, 0.8f);
                    player.displayClientMessage(Component.translatable("message.harddayz.refueled", (int) fuel(), (int) tank()).withStyle(ChatFormatting.GREEN), true);
                }
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
        if (kind().heli() && !player.isSecondaryUseActive() && getPassengers().size() < seats()) {
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
            player.displayClientMessage(Component.translatable("message.harddayz.ew_auto").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (kind().samLike() && kind() != Kind.S400) {
            player.displayClientMessage(Component.translatable("message.harddayz.sam_auto").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (ammo() <= 0) {
            player.displayClientMessage(Component.translatable("message.harddayz.empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (state != State.IDLE) {
            player.displayClientMessage(Component.translatable("message.harddayz.busy").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (tickCount < reloadUntil) {
            player.displayClientMessage(Component.translatable("message.harddayz.reloading_launcher", (reloadUntil - tickCount) / 20 + 1)
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!stationary()) {
            player.displayClientMessage(Component.translatable("message.harddayz.stop_first").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        Vec3 tv = Vec3.atCenterOf(t);
        double dist = Math.sqrt(tv.distanceToSqr(getX(), tv.y, getZ()));
        switch (kind()) {
            case MORTAR -> {
                player.displayClientMessage(Component.translatable("message.harddayz.mortar_manual").withStyle(ChatFormatting.YELLOW), false);
                return;
            }
            case KALIBR -> {
                if (dist < 150 || dist > Drone.SHAHED_RANGE) {
                    player.displayClientMessage(Component.translatable("message.harddayz.out_of_range", (int) dist, Drone.SHAHED_RANGE).withStyle(ChatFormatting.RED), true);
                    return;
                }
                // контейнер поднимается гидроцилиндром в вертикаль, ракеты уходят из ячеек вверх
                entityData.set(WANT_ELEV, 90f);
                entityData.set(WANT_TURRET, 0f);
            }
            case GRAD, GRAD_P, TOS, SMERCH, URAGAN -> {
                Double elev = gradElevation(tv);
                if (elev == null) {
                    int dy = (int) (tv.y - muzzleBase().y);
                    player.displayClientMessage((Math.abs(dy) > 8 && dist <= maxGradRange()
                            ? Component.translatable("message.harddayz.out_of_range_h", (int) dist, dy, (int) maxGradRange())
                            : Component.translatable("message.harddayz.out_of_range", (int) dist, (int) maxGradRange())).withStyle(ChatFormatting.RED), true);
                    return;
                }
                entityData.set(WANT_ELEV, elev.floatValue());
                entityData.set(WANT_TURRET, Mth.wrapDegrees(yawTo(tv) - getYRot()));
            }
            case SHAHED, RAMP -> {
                if (dist < 80 || dist > Drone.SHAHED_RANGE) {
                    player.displayClientMessage(Component.translatable("message.harddayz.out_of_range", (int) dist, Drone.SHAHED_RANGE).withStyle(ChatFormatting.RED), true);
                    return;
                }
                entityData.set(WANT_ELEV, 18f);
                entityData.set(WANT_TURRET, Mth.wrapDegrees(yawTo(tv) - getYRot()));
            }
            default -> {
                int max = kind() == Kind.S400 ? 1500 : kind() == Kind.HIMARS ? 1800 : kind() == Kind.TOCHKA ? com.harddayz.Config.ballisticRange() / 2
                        : com.harddayz.Config.ballisticRange();
                if (dist > max || dist < 60) {
                    player.displayClientMessage(Component.translatable("message.harddayz.out_of_range", (int) dist, max).withStyle(ChatFormatting.RED), true);
                    return;
                }
                if (kind() == Kind.S400) {
                    entityData.set(WANT_ELEV, 88f); // и по земле — вертикальный пуск
                    entityData.set(WANT_TURRET, 0f);
                }
            }
        }
        target = t;
        state = State.DEPLOY;
        entityData.set(OUTRIG, kind().mobile ? 1f : 0f);
        timer = 0;
        shot = 0;
        player.displayClientMessage(Component.translatable("message.harddayz.order", t.getX(), t.getY(), t.getZ(), (int) dist).withStyle(ChatFormatting.GOLD), true);
        level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.5f);
    }

    private float yawTo(Vec3 p) {
        return (float) Math.toDegrees(Math.atan2(-(p.x - getX()), p.z - getZ()));
    }

    /** Скорость неуправляемых ракет: у «Солнцепёка» меньше — и дальность меньше. */
    private double rocketSpeed() {
        return switch (kind()) {
            case TOS -> Missile.GRAD_SPEED * 0.92;
            case SMERCH -> Missile.GRAD_SPEED * 1.4;
            case URAGAN -> Missile.GRAD_SPEED * 1.22;
            default -> Missile.GRAD_SPEED;
        };
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
    private Vec3 launchMuzzle() {
        return position().add(LauncherGeometry.muzzle(kind(), ammo(), getYRot(), turretSmooth().value, elevation(1), erect.value));
    }

    private Vec3 launchDirection() {
        return LauncherGeometry.direction(kind(), getYRot(), turretSmooth().value, elevation(1), erect.value);
    }

    private Vec3 muzzleBase() {
        if (!kind().removed() && (kind().samLike() || kind().dronePad()
                || kind() == Kind.GRAD || kind() == Kind.GRAD_P || kind() == Kind.ISKANDER || kind() == Kind.TOCHKA
                || kind() == Kind.TOS || kind() == Kind.HIMARS || kind() == Kind.SMERCH || kind() == Kind.URAGAN
                || kind() == Kind.KALIBR || kind() == Kind.MORTAR)) {
            return position().add(LauncherGeometry.pivot(kind(), getYRot(), turretSmooth().value, elevation.value, erect.value));
        }
        return switch (kind()) {
            case GRAD, DECOY -> local(0, 2.0, -1.5);
            case ISKANDER -> local(0, 1.75, -2.75);
            case TOCHKA -> local(0, 1.75, -1.9);
            case SMERCH, URAGAN -> local(0, 2.0, -1.9);
            case KALIBR -> local(0, 2.2, -1.6);
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
            case T72, ABRAMS, KA52, MI8 -> local(0, 0.8, 2.5);
            case MORTAR -> local(0, 0.5, 0);
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
        if (kind().removed()) {
            discard(); // танки и вертолёты убраны из мода
            return;
        }
        if (wrecked()) {
            if (camo() > 0) {
                entityData.set(CAMO, 0); // сеть сгорела
            }
            tickWreck(level);
            return;
        }
        if (flash() > 0) {
            revealUntil = tickCount + 600; // пуск видно: 30 с сеть не спасает
        }
        if (!netOn() && tickCount % 20 == 0) {
            entityData.set(CAMO, tickCount >= revealUntil && underNetBlocks() ? 3 : 0);
        }
        if (netOn()) {
            if (!stationary() && getControllingPassenger() != null) {
                entityData.set(CAMO, 0); // поехала — сеть слетела
                spawnAtLocation(new ItemStack(ModItems.CAMO_NET.get()));
            } else if (tickCount % 10 == 0) {
                entityData.set(CAMO, tickCount < revealUntil ? 2 : 1);
            }
        }
        // заряженная пусковая держит свой участок мира загруженным
        if ((ammo() > 0 || kind() == Kind.RELAY) && tickCount % 20 == 0) {
            com.harddayz.Compat.ticket(level, chunkPosition(), 2);
        }
        if (flash() > 0) {
            entityData.set(FLASH, flash() - 1);
        }
        // мотор работает — топливо уходит (на месте втрое меньше)
        if (usesFuel() && getControllingPassenger() != null && fuel() > 0) {
            boolean moving = !stationary();
            entityData.set(FUEL, Math.max(0, fuel() - burn() * (moving ? 1 : 0.33f)));
            if (fuel() <= 0 && getControllingPassenger() instanceof ServerPlayer d) {
                d.displayClientMessage(Component.translatable("message.harddayz.no_fuel").withStyle(ChatFormatting.RED), true);
            }
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
        if (kind() == Kind.DECOY) {
            return; // макет просто стоит
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
                    prep = 0;
                    if (kind() == Kind.ISKANDER || kind() == Kind.TOCHKA) {
                        entityData.set(WANT_ERECT, 1f);
                    }
                    level.playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5f, 0.4f);
                }
            }
            case AIMING -> {
                if ((kind() == Kind.ISKANDER || kind() == Kind.TOCHKA) && tickCount % 20 == 0) {
                    level.playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.0f, 0.35f);
                }
                prep++;
                if (prep >= (kind() == Kind.MORTAR ? 20 : 60) && aimed()) { // ~3 с подготовки к пуску
                    state = State.FIRING;
                    timer = 0;
                }
            }
            case FIRING -> {
                switch (kind()) {
                    case GRAD, GRAD_P, TOS, SMERCH, URAGAN -> fireGrad(level);
                    case ISKANDER, TOCHKA -> fireIskander(level);
                    case KALIBR -> fireKalibr(level);
                    case SHAHED, RAMP -> fireShahed(level);
                    case S400 -> fireS400Ground(level);
                    case HIMARS -> fireHimars(level);
                    case MORTAR -> fireMortar(level);
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

    /** Тики подготовки к пуску в AIMING. */
    private int prep;

    private boolean aimed() {
        return turret.near(entityData.get(WANT_TURRET), 0.4f, true) && elevation.near(entityData.get(WANT_ELEV), 0.4f, false)
                && erect.near(entityData.get(WANT_ERECT), 0.005f, false);
    }

    private void stow() {
        state = State.STOW;
        timer = 0;
        reloadUntil = tickCount + switch (kind()) {
            case ISKANDER -> 2400;
            case TOCHKA -> 1800;
            case SMERCH -> 1600;
            case URAGAN -> 1400;
            case KALIBR -> 1200;
            case GRAD, TOS -> 1200;
            case HIMARS, S400 -> 800;
            case GRAD_P -> 400;
            case SHAHED, RAMP -> 400;
            case MORTAR -> 200;
            default -> 400;
        };
    }

    /** До какого тика пусковая перезаряжается после залпа. */
    private int reloadUntil;

    private void fireGrad(ServerLevel level) {
        if (timer++ % 2 != 0) {
            return;
        }
        if (ammo() <= 0 || target == null) {
            stow();
            return;
        }
        shot++;
        // Spread changes velocity, never the launcher's physical tube position.
        Vec3 axis = launchDirection();
        float yaw = (float) Math.toDegrees(Math.atan2(-axis.x, axis.z)) + (float) random.nextGaussian() * 1.7f;
        float pitch = (float) Math.toDegrees(Math.asin(axis.y)) + (float) random.nextGaussian() * 0.8f;
        Vec3 dir = forward(yaw, pitch);
        Vec3 muzzle = launchMuzzle();
        Vec3 vel = dir.scale(rocketSpeed() * (1 + random.nextGaussian() * 0.022));
        Missile m = Missile.launch(level, Missile.Kind.GRAD, muzzle, vel, Vec3.atCenterOf(target), this).owner(owner);
        if (kind() == Kind.TOS) {
            m.thermobaric();
        } else if (kind() == Kind.SMERCH) {
            m.powerMul(1.8f);
        } else if (kind() == Kind.URAGAN) {
            m.powerMul(1.35f);
        }
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 3);
        com.harddayz.Compat.particles(level, ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.4, 0.4, 0.4, 0.05);
        Vec3 back = muzzleBase().subtract(dir.scale(1.0));
        com.harddayz.Compat.particles(level, ParticleTypes.CLOUD, back.x, back.y, back.z, 14, 0.6, 0.3, 0.6, 0.12);
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
        Missile.launch(level, Missile.Kind.BALLISTIC, launchMuzzle(), launchDirection().scale(0.6), aim, this).owner(owner).powerMul(0.35f).look(2);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 8);
        com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, g.x, g.y, g.z, 30, 1.5, 0.4, 1.5, 0.05);
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
        Missile.launch(level, Missile.Kind.BALLISTIC, launchMuzzle(), launchDirection().scale(0.8), aim, this).owner(owner).powerMul(0.5f).look(1);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 6);
        com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, g.x, g.y, g.z, 30, 1.2, 0.4, 1.2, 0.05);
        // звук пуска даёт сама ракета (Missile), второй не нужен
    }

    /** Миномёт: следующий выстрел не раньше этого тика. */
    private int mortarNext;

    private static String deg(float v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Наводка по взгляду: куда смотришь — туда азимут, насколько поднял голову — такой угол ствола (45..85°). */
    private void mortarAim(ServerPlayer sp) {
        float elev = Mth.clamp(-sp.getXRot(), 45f, 85f);
        entityData.set(WANT_ELEV, elev);
        entityData.set(WANT_TURRET, Mth.wrapDegrees(sp.getYRot() - getYRot()));
        level().playSound(null, blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
        sp.displayClientMessage(Component.translatable("message.harddayz.mortar_aim", deg(Mth.positiveModulo(sp.getYRot() + 180f, 360f)), deg(elev))
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** Опустить мину в ствол: выстрел сразу, по текущей наводке. */
    private void mortarDrop(ServerPlayer sp, ItemStack stack) {
        if (tickCount < mortarNext) {
            return;
        }
        mortarNext = tickCount + 40;
        ServerLevel level = (ServerLevel) level();
        float yawSet = getYRot() + entityData.get(WANT_TURRET);
        float elevSet = entityData.get(WANT_ELEV);
        float yaw = yawSet + (float) random.nextGaussian() * 0.5f;
        Vec3 dir = forward(yaw, elevSet + (float) random.nextGaussian() * 0.35f);
        Vec3 muzzle = muzzleBase().add(dir.scale(1.6));
        boolean illum = stack.is(ModItems.MORTAR_ILLUM.get());
        Missile.launch(level, Missile.Kind.GRAD, muzzle, dir.scale(MORTAR_SPEED * (1 + random.nextGaussian() * 0.008)), muzzle.add(dir.scale(80)), this)
                .owner(owner != null ? owner : sp.getUUID()).powerMul(0.7f).look(illum ? 8 : 7);
        if (!sp.isCreative()) {
            stack.shrink(1);
        }
        entityData.set(FLASH, 4);
        com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.3, 0.3, 0.3, 0.03);
        com.harddayz.Compat.particles(level, ParticleTypes.POOF, muzzle.x, muzzle.y, muzzle.z, 8, 0.2, 0.2, 0.2, 0.05);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, com.harddayz.registry.ModSounds.MORTAR_FIRE.get(), SoundSource.HOSTILE, 5f, 0.95f + random.nextFloat() * 0.1f);
        sp.displayClientMessage(Component.translatable("message.harddayz.mortar_shot", deg(Mth.positiveModulo(yawSet + 180f, 360f)), deg(elevSet))
                .withStyle(ChatFormatting.GOLD), true);
    }

    public static final double MORTAR_SPEED = 2.4;
    public static final int MORTAR_RANGE = 115;

    /** Миномёт: крутая (навесная) траектория — верхнее решение. */
    private @Nullable Double mortarElevation(Vec3 t) {
        double v = MORTAR_SPEED;
        double g = Missile.Kind.GRAD.gravity;
        Vec3 from = muzzleBase();
        double x = Math.sqrt((t.x - from.x) * (t.x - from.x) + (t.z - from.z) * (t.z - from.z));
        double y = t.y - from.y;
        double disc = v * v * v * v - g * (g * x * x + 2 * y * v * v);
        if (disc < 0 || x < 8) {
            return null;
        }
        return Math.min(85, Math.toDegrees(Math.atan((v * v + Math.sqrt(disc)) / (g * x))));
    }

    private void fireMortar(ServerLevel level) {
        if (timer++ % 30 != 0) {
            return;
        }
        if (ammo() <= 0 || target == null || shot >= 3) {
            stow();
            return;
        }
        shot++;
        Vec3 axis = launchDirection();
        float yaw = (float) Math.toDegrees(Math.atan2(-axis.x, axis.z)) + (float) random.nextGaussian() * 0.8f;
        float pitch = (float) Math.toDegrees(Math.asin(axis.y)) + (float) random.nextGaussian() * 0.6f;
        Vec3 dir = forward(yaw, pitch);
        Vec3 muzzle = launchMuzzle();
        Missile.launch(level, Missile.Kind.GRAD, muzzle, dir.scale(MORTAR_SPEED), Vec3.atCenterOf(target), this).owner(owner).powerMul(0.7f).look(7);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 4);
        com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.3, 0.3, 0.3, 0.03);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, com.harddayz.registry.ModSounds.MORTAR_FIRE.get(), SoundSource.HOSTILE, 4f, 1f);
    }

    /** «Калибр»: вертикальный пуск из шахт контейнера. Крышки отлетают при выстреле. */
    private void fireKalibr(ServerLevel level) {
        if (timer++ % 40 != 0) {
            return;
        }
        if (ammo() <= 0 || target == null || shot >= 2) {
            stow();
            return;
        }
        shot++;
        // Позиция текущей ракеты в контейнере: 2 ряда по 2
        // В LauncherGeometry.muzzle() для KALIBR: i%2==0 -> x=-9, иначе x=9; y=43+i/2*18
        Vec3 muzzle = launchMuzzle(); // уже вычисляет правильную позицию шахты
        // Вертикальный старт: ракета летит строго вверх, потом наводится на цель (Drone.Kind.CRUISE)
        Vec3 vertUp = new Vec3(0, 1, 0).scale(0.5);
        double ang = random.nextDouble() * Math.PI * 2;
        Vec3 aim = Vec3.atCenterOf(target).add(Math.cos(ang) * 3, 0, Math.sin(ang) * 3);
        Drone.launchShahed(level, muzzle, vertUp, aim, owner, Drone.Kind.CRUISE);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 8); // крышка отлетает при flash>0 (рендерер)
        com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, muzzle.x, muzzle.y, muzzle.z, 30, 0.8, 0.4, 0.8, 0.05);
        com.harddayz.Compat.particles(level, ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 20, 0.3, 0.2, 0.3, 0.12);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, com.harddayz.registry.ModSounds.LAUNCH_CRUISE.get(), SoundSource.HOSTILE, 6f, 1f);
    }

    private void fireIskander(ServerLevel level) {
        timer++;
        Vec3 g = muzzleBase();
        if (timer == 1) {
            level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 3f, 0.5f);
        }
        if (timer < 50) {
            if (timer > 20) {
                com.harddayz.Compat.particles(level, ParticleTypes.CLOUD, g.x, g.y + 0.5, g.z, 8, 1.2, 0.3, 1.2, 0.06);
            }
            return;
        }
        if (ammo() > 0 && target != null) {
            Missile.launch(level, Missile.Kind.BALLISTIC, launchMuzzle(), launchDirection().scale(0.4), Vec3.atCenterOf(target), this).owner(owner)
                    .powerMul(kind() == Kind.TOCHKA ? 0.6f : 1f);
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 10);
            com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, g.x, g.y, g.z, 50, 3.0, 0.5, 3.0, 0.06);
            com.harddayz.Compat.particles(level, ParticleTypes.EXPLOSION, g.x, g.y, g.z, 8, 1.8, 0.3, 1.8, 0);
            com.harddayz.Compat.particles(level, ParticleTypes.FLAME, g.x, g.y, g.z, 80, 2.4, 0.3, 2.4, 0.18);
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
        Vec3 dir = launchDirection();
        Vec3 muzzle = launchMuzzle();
        // у каждого дрона своя точка рядом с целью
        double ang = random.nextDouble() * Math.PI * 2;
        double off = 4 + random.nextDouble() * 9;
        Vec3 aim = Vec3.atCenterOf(target).add(Math.cos(ang) * off, 0, Math.sin(ang) * off);
        Drone.Kind dk = drones.isEmpty() ? Drone.Kind.SHAHED : drones.remove(drones.size() - 1);
        if (controlOp != null) {
            // пуск под ручное управление: один дрон, курс — прямо на выбранную точку
            Drone d = Drone.launchShahed(level, muzzle, dir, Vec3.atCenterOf(target), owner, dk);
            if (level.getPlayerByUUID(controlOp) instanceof ServerPlayer op && op.distanceTo(this) < Drone.SHAHED_LINK_RANGE) {
                d.takeControl(op);
            }
            controlOp = null;
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 6);
            com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 20, 0.6, 0.3, 0.6, 0.04);
            com.harddayz.Compat.particles(level, ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 25, 0.4, 0.2, 0.4, 0.1);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, (dk.jetDrone() ? com.harddayz.registry.ModSounds.LAUNCH_JETDRONE
                : com.harddayz.registry.ModSounds.LAUNCH_SHAHED).get(), SoundSource.HOSTILE, 5f, 1f);
            stow();
            return;
        }
        Drone.launchShahed(level, muzzle, dir, aim, owner, dk);
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 6);
        com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x, muzzle.y, muzzle.z, 20, 0.6, 0.3, 0.6, 0.04);
        com.harddayz.Compat.particles(level, ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 25, 0.4, 0.2, 0.4, 0.1);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, (dk.jetDrone() ? com.harddayz.registry.ModSounds.LAUNCH_JETDRONE
                : com.harddayz.registry.ModSounds.LAUNCH_SHAHED).get(), SoundSource.HOSTILE, 5f, 1f);
    }

    /** Пульт оператора по пусковой: дрон стартует и сразу переходит под ручное управление. */
    private void launchControlled(ServerPlayer op) {
        if (ammo() <= 0) {
            op.displayClientMessage(Component.translatable("message.harddayz.empty").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (state != State.IDLE) {
            op.displayClientMessage(Component.translatable("message.harddayz.busy").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!stationary()) {
            op.displayClientMessage(Component.translatable("message.harddayz.stop_first").withStyle(ChatFormatting.YELLOW), true);
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
        op.displayClientMessage(Component.translatable("message.harddayz.control_prep").withStyle(ChatFormatting.GOLD), true);
        level().playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.2f, 0.5f);
    }

    // ------------------------------------------------------------------ ЗРК

    private int samRange() {
        return switch (kind()) {
            case S400 -> 600;
            case PATRIOT -> 450;
            case NASAMS -> 340;
            default -> 260;
        };
    }

    /** Цель → до какого тика по ней уже летит ракета (повторный пуск только если промах). */
    private final java.util.Map<Integer, Long> inFlight = new java.util.HashMap<>();
    /** Цель → сколько ракет уже выпущено (по баллистике можно две). */
    private final java.util.Map<Integer, Integer> fired = new java.util.HashMap<>();

    private int samRate() {
        return switch (kind()) {
            case S400 -> 80;
            case PATRIOT -> 100;
            case NASAMS -> 55;
            default -> 45;
        };
    }

    /** Общий «журнал целей» ПВО каждой фракции: цель → до какого тика по ней уже работает какая-то установка. */
    private static final java.util.Map<String, java.util.Map<Integer, Long>> CLAIMS = new java.util.HashMap<>();

    private java.util.Map<Integer, Long> claims() {
        return CLAIMS.computeIfAbsent(com.harddayz.war.RadarNet.key(owner), k -> new java.util.HashMap<>());
    }

    private boolean busyWith(Entity e, long now) {
        Long until = inFlight.get(e.getId());
        if (until != null && until > now) {
            return true;
        }
        Long other = claims().get(e.getId()); // по цели уже стреляет соседняя установка
        return other != null && other > now;
    }

    /** Чем меньше, тем важнее цель: баллистика и ракеты — первыми, дальше «Шахеды», потом мелкие дроны. */
    private double priority(Entity e) {
        double d = Math.sqrt(e.distanceToSqr(this));
        if (e instanceof Missile m) {
            return d * (m.kind() == Missile.Kind.BALLISTIC ? 0.15 : 0.7);
        }
        if (e instanceof Drone dr) {
            return d * switch (dr.kind()) {
                case CRUISE, GERAN5, BANDEROL, SHAHED_238 -> 0.4;
                case SHAHED, SHAHED_131, GERBERA, LANCET, KUB, MOLNIYA -> 0.6;
                case WING_RECON -> 0.8;
                default -> 1.2;
            };
        }
        return d;
    }

    private boolean canEngage(Entity e) {
        if (!(e instanceof Threat th) || !th.isThreat() || !e.isAlive() || com.harddayz.war.War.allied(owner, th.owner())) {
            return false;
        }
        if (e instanceof Missile m && m.kind() != Missile.Kind.BALLISTIC) {
            return false; // неуправляемые ракеты РСЗО, мины и гранаты ПВО не сбивает (не тратит на них ракеты)
        }
        if (kind() == Kind.PATRIOT && !(e instanceof Missile) && !(e instanceof Drone dr && dr.kind().jet())) {
            return false; // ПРО: баллистика, крылатые и реактивные дроны; мелочь не трогает
        }
        double d = e.distanceTo(this);
        if (d < 18 || e.getY() < getY() + 6) {
            return false; // слишком близко ракета не успеет взвестись
        }
        if (d <= samRange()) {
            return true;
        }
        // цель ещё дальше, но идёт к нам: стреляем наперёд, если точка встречи будет в зоне поражения
        double speed = kind() == Kind.S400 || kind() == Kind.PATRIOT ? 4.5 : 4.0;
        Vec3 meet = Missile.intercept(muzzleBase(), speed, e.position(), e.getDeltaMovement());
        return d <= samRange() * 1.4 && meet.distanceTo(position()) <= samRange() * 0.95;
    }

    /** Прибавка к шансу поражения: ПРО и С-400 лучше по баллистике и крылатым, NASAMS — по дронам. */
    private float samBonus(Entity t) {
        boolean hard = t instanceof Missile || t instanceof Drone d && d.kind().jet();
        return switch (kind()) {
            case PATRIOT -> hard ? 0.55f : 0f;
            case S400 -> hard ? 0.3f : 0.05f;
            case NASAMS -> hard ? 0.1f : 0.08f;
            default -> 0f;
        };
    }

    private void tickSam(ServerLevel level) {
        if (samCooldown > 0) {
            samCooldown--;
        }
        long now = level.getGameTime();
        Entity t = samTarget >= 0 ? level.getEntity(samTarget) : null;
        if (t == null || !canEngage(t) || busyWith(t, now) && tickCount % 10 == 0) {
            samTarget = -1;
            t = null;
            if (tickCount % 5 == 0) {
                t = findThreat(level, now);
                if (t != null) {
                    samTarget = t.getId();
                    level.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 2f, 2f);
                }
            }
        }
        boolean vertical = kind() == Kind.S400; // С-400: вертикальный пуск, башня не крутится
        if (t == null) {
            entityData.set(WANT_ELEV, vertical ? 0f : 12f);
            if (vertical) {
                entityData.set(WANT_TURRET, 0f);
            }
            return;
        }
        // упреждение: куда цель придёт к моменту встречи
        double speed = kind() == Kind.S400 || kind() == Kind.PATRIOT ? 2.0 : 1.4;
        Vec3 p = Missile.intercept(muzzleBase(), 4.0, t.position(), t.getDeltaMovement()); // пуск в точку встречи
        Vec3 base = muzzleBase();
        if (vertical) {
            entityData.set(WANT_TURRET, 0f);
            entityData.set(WANT_ELEV, 88f); // контейнеры поднимаются вертикально
        } else {
            entityData.set(WANT_TURRET, Mth.wrapDegrees(yawTo(p) - getYRot()));
            double h = Math.sqrt((p.x - base.x) * (p.x - base.x) + (p.z - base.z) * (p.z - base.z));
            entityData.set(WANT_ELEV, (float) Mth.clamp(Math.toDegrees(Math.atan2(p.y - base.y, h)), 15, 80));
        }
        int salvo = t instanceof Missile m && m.kind() == Missile.Kind.BALLISTIC ? 2 : 1;
        boolean ready = vertical ? elevation.value > 80 : Math.abs(Mth.wrapDegrees(entityData.get(WANT_TURRET) - samTurret.value)) < 12;
        if (samCooldown == 0 && ammo() > 0 && !busyWith(t, now) && ready) {
            shot++;
            Vec3 dir = launchDirection();
            Vec3 muzzle = launchMuzzle();
            Missile.launch(level, Missile.Kind.SAM, muzzle, dir.scale(speed), p, this).hunt(t)
                    .owner(owner).bonus(samBonus(t))
                    .look(kind() == Kind.S400 ? 2 : kind() == Kind.PATRIOT ? 3 : kind() == Kind.NASAMS ? 4 : 0);
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 4);
            int n = fired.merge(t.getId(), 1, Integer::sum);
            // ракета долетит примерно за столько тиков; до этого по цели не стреляем (кроме залпа по баллистике)
            long eta = (long) (t.distanceTo(this) / 4.0) + 20; // ракета разгоняется до ~4.5 блока/тик
            if (n % salvo == 0) {
                inFlight.put(t.getId(), now + eta);
                claims().put(t.getId(), now + eta);
                if (claims().size() > 128) {
                    claims().values().removeIf(u -> u < now);
                }
            }
            samCooldown = n % salvo == 0 ? samRate() : 8;
            com.harddayz.Compat.particles(level, ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 20, 0.5, 0.5, 0.5, 0.1);
        }
        if (inFlight.size() > 64) {
            inFlight.entrySet().removeIf(e -> e.getValue() < now);
            fired.keySet().retainAll(inFlight.keySet());
        }
    }

    private @Nullable Entity findThreat(ServerLevel level, long now) {
        Entity best = null;
        double bestP = Double.MAX_VALUE;
        int r = samRange();
        for (Entity e : level.getEntities((Entity) null, new AABB(position(), position()).inflate(r * 1.4, 300, r * 1.4), e -> e instanceof Threat)) {
            if (!canEngage(e) || busyWith(e, now)) {
                continue;
            }
            double pr = priority(e);
            if (pr < bestP) {
                bestP = pr;
                best = e;
            }
        }
        if (best == null) {
            // цель видит РЛС сети — ЗРК бьёт дальше своей станции
            String key = com.harddayz.war.RadarNet.key(owner);
            for (var c : com.harddayz.war.RadarNet.contacts(key, now)) {
                Entity e = level.getEntity(c.id);
                if (e == null || busyWith(e, now) || !com.harddayz.war.RadarNet.tracked(key, e, now) || !canEngage(e)) {
                    continue;
                }
                double pr = priority(e);
                if (pr < bestP) {
                    bestP = pr;
                    best = e;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ урон и подбор

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (!(level() instanceof ServerLevel level)) {
            return true;
        }
        if (wrecked()) {
            if (source.getEntity() instanceof Player p && source.getDirectEntity() == p && p.isShiftKeyDown() && wreck() > 5) {
                discard();
            }
            return true;
        }
        if (source.getEntity() instanceof Player p && source.getDirectEntity() == p && p.isShiftKeyDown() && getPassengers().isEmpty() && health() > 0) {
            spawnAtLocation(new ItemStack(kind().item()));
            if (kind().dronePad()) {
                for (Drone.Kind k : drones) {
                    spawnAtLocation(new ItemStack(droneItem(k)));
                }
            } else if (kind() != Kind.EW) {
                int left = ammo();
                while (left > 0) {
                    int n = Math.min(left, kind().ammo().getMaxStackSize());
                    spawnAtLocation(new ItemStack(kind().ammo(), n));
                    left -= n;
                }
            }
            discard();
            return true;
        }
        float hp = health() - damage;
        entityData.set(HEALTH, hp);
        if (hp <= 0 && kind() == Kind.DECOY) {
            com.harddayz.Compat.particles(level, ParticleTypes.POOF, getX(), getY() + 1.5, getZ(), 40, 1.8, 0.8, 1.8, 0.05);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 2f, 0.5f);
            discard();
            return true;
        }
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
            float bk = switch (kind()) {
                case ISKANDER -> ammo() * 7f;
                case GRAD, GRAD_P, TOS -> ammo() * 0.18f;
                case HIMARS, S400, PATRIOT, SAM, NASAMS -> ammo() * 0.9f;
                case MG, PICKUP_MG -> 0;
                default -> ammo() * 0.12f;
            };
            entityData.set(HEALTH, 0f);
            entityData.set(WRECK, 1);
            entityData.set(AMMO, 0);
            drones.clear();
            state = State.IDLE;
            // сам подрыв машины — небольшой, а боекомплект детонирует через несколько секунд
            Blast.detonate(level, c, 1.6f, false, this);
            cookPower = bk;
            cookoff = bk > 0.3f ? 50 + random.nextInt(110) : -1;
        }
        return true;
    }

    /** Остов: горит, рвёт боекомплект, через 10 минут исчезает. */
    private void tickWreck(ServerLevel level) {
        if (++wreckTicks % 20 == 0) {
            entityData.set(WRECK, wreck() + 1);
        }
        if (cookoff > 0 && --cookoff == 0) {
            Vec3 c = position().add(0, 1.2, 0);
            Blast.detonate(level, c, 1.2f + Math.min(12f, cookPower), cookPower > 0.5f, this);
            // ракеты в пакете рвутся ещё какое-то время хлопками
            if (cookPower > 1) {
                cookoff = -2 - (int) Math.min(8, cookPower * 0.6);
            }
        } else if (cookoff < -1 && wreckTicks % (8 + random.nextInt(14)) == 0) {
            cookoff++;
            Vec3 c = position().add((random.nextDouble() - 0.5) * 4, 1.5, (random.nextDouble() - 0.5) * 4);
            Blast.detonate(level, c, 1.2f + random.nextFloat(), false, this);
        }
        if (wreck() > 600) {
            discard();
        }
    }

    // ------------------------------------------------------------------ служебное

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(KIND, 0);
        entityData.define(AMMO, 0);
        entityData.define(WANT_TURRET, 0f);
        entityData.define(WANT_ELEV, 0f);
        entityData.define(WANT_ERECT, 0f);
        entityData.define(FLASH, 0);
        entityData.define(WRECK, 0);
        entityData.define(CAMO, 0);
        entityData.define(DRONES, "");
        entityData.define(FUEL, 0f);
        entityData.define(SLOTS, ";;;;");
        entityData.define(SLOT, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag input) {
        entityData.set(KIND, com.harddayz.Compat.getInt(input, "kind", 0));
        entityData.set(AMMO, com.harddayz.Compat.getInt(input, "ammo", 0));
        entityData.set(HEALTH, com.harddayz.Compat.getFloat(input, "health", kind().health));
        com.harddayz.Compat.optString(input, "owner").ifPresent(s -> owner = UUID.fromString(s));
        entityData.set(SLOTS, com.harddayz.Compat.getString(input, "slots", ";;;;"));
        entityData.set(SLOT, com.harddayz.Compat.getInt(input, "slot", 0));
        entityData.set(FUEL, com.harddayz.Compat.getFloat(input, "fuel", tank()));
        entityData.set(WRECK, com.harddayz.Compat.getInt(input, "wreck", 0));
        entityData.set(CAMO, com.harddayz.Compat.getInt(input, "camo", 0));
        cookoff = com.harddayz.Compat.getInt(input, "cookoff", -1);
        cookPower = com.harddayz.Compat.getFloat(input, "cookPower", 0f);
        drones.clear();
        for (String k : com.harddayz.Compat.getString(input, "drones", "").split(",")) {
            if (!k.isEmpty()) {
                drones.add(Drone.Kind.valueOf(k));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
        output.putInt("kind", entityData.get(KIND));
        output.putInt("ammo", ammo());
        output.putFloat("health", health());
        if (owner != null) {
            output.putString("owner", owner.toString());
        }
        output.putString("drones", String.join(",", drones.stream().map(Enum::name).toList()));
        output.putFloat("fuel", fuel());
        output.putInt("wreck", wreck());
        output.putInt("camo", netOn() ? 1 : 0);
        output.putInt("cookoff", cookoff);
        output.putFloat("cookPower", cookPower);
        output.putString("slots", entityData.get(SLOTS));
        output.putInt("slot", selectedSlot());
    }

    // ------------------------------------------------------------------ стрелковые установки

    private int gunCooldown;

    private void tickGunner(ServerLevel level) {
        if (gunCooldown > 0) {
            gunCooldown--;
        }
        Player p = gunnerPassenger();
        if (p != null) {
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
        Vec3 muzzle = kind().lockOn() ? launchMuzzle() : muzzleBase().add(look.scale(1.4));
        if (kind().lockOn()) {
            Entity t = lockTarget(level, p.getEyePosition(), look, p.getUUID());
            if (t == null) {
                p.displayClientMessage(Component.translatable("message.harddayz.no_lock").withStyle(ChatFormatting.RED), true);
                gunCooldown = 6;
                return;
            }
            Missile.launch(level, Missile.Kind.SAM, muzzle, launchDirection().scale(1.4), t.position(), this).hunt(t).owner(p.getUUID());
            entityData.set(AMMO, ammo() - 1);
            entityData.set(FLASH, 4);
            gunCooldown = 30;
            com.harddayz.Compat.particles(level, ParticleTypes.CLOUD, muzzle.x, muzzle.y, muzzle.z, 20, 0.4, 0.4, 0.4, 0.1);
            return;
        }
        // пулемёт / автопушка: мгновенный выстрел с небольшим разбросом
        boolean cannon = kind().cannon();
        gunCooldown = kind() == Kind.BMP2 ? 6 : kind() == Kind.BRADLEY ? 4 : 2;
        entityData.set(AMMO, ammo() - 1);
        entityData.set(FLASH, 2);
        float spread = cannon ? 0.35f : 0.4f;
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
                dmg = kind() == Kind.BMP2 ? 9f : 7f;
            }
            e.hurt(damageSources().playerAttack(p), dmg);
        }
        if (cannon && (hit != null || hitBlock)) {
            Blast.detonate(level, stop, kind() == Kind.BMP2 ? 1.4f : 1.0f, true, this); // фугасный — рушит укрытия
        }
        level.playSound(null, getX(), getY(), getZ(), com.harddayz.registry.ModSounds.MG_FIRE.get(), SoundSource.PLAYERS, 4f,
                (kind().cannon() ? 0.6f : 0.95f) + random.nextFloat() * 0.1f);
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
            com.harddayz.war.RadarNet.radarAt(key, blockPosition());
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
