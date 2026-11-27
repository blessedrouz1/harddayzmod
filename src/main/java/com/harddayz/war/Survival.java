package com.harddayz.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Missile;
import com.harddayz.network.Net;
import com.harddayz.registry.ModBlocks;
import com.harddayz.registry.ModItems;
import com.harddayz.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Режим «Выжить под обстрелом». Удар не мгновенный, а цепочкой, и на каждом шаге можно увернуться:
 * поиск (дроны кружат) → засечка (дрон должен несколько секунд держать человека, индикатор растёт) → наводка
 * (позицию передали на батарею, ещё несколько секунд) → пуск (сигнал с типом, направлением и временем подлёта) → подлёт.
 * Каждый раунд — задача (добежать, удержать, сбить дроны); выполнили — подвоз, провалили — следующий раунд злее.
 */
public final class Survival {
    public static final int ROUND = 150;
    public static final int BREAK = 25;
    private static final Random R = new Random();

    /** Засечка игрока 0..1 и наводка: тик, когда батарея получит координаты, и сами координаты. */
    private static final Map<UUID, double[]> HEAT = new HashMap<>();
    private static final Map<UUID, double[]> HANDOFF = new HashMap<>();
    /** Последнее место, где видели (x, y, z, clock). */
    private static final Map<UUID, double[]> SEEN = new HashMap<>();
    private static final List<UUID> DRONES = new ArrayList<>();
    private static final Map<UUID, UUID> HUNTERS = new HashMap<>();
    private static final List<Object[]> PENDING = new ArrayList<>();
    private static int clock;
    private static int nextHarass;
    private static int nextDrone;
    /** Задача раунда: вид (1 добежать, 2 удержать, 3 сбить дроны), точка, прогресс и цель. */
    private static int task;
    private static int taskX;
    private static int taskZ;
    private static int taskHave;
    private static int taskNeed;
    private static boolean taskDone;
    private static int fails;
    private static int done;

    private Survival() {
    }

    public static void reset() {
        HEAT.clear();
        HANDOFF.clear();
        SEEN.clear();
        DRONES.clear();
        HUNTERS.clear();
        PENDING.clear();
        clock = 0;
        nextHarass = 0;
        nextDrone = 0;
        task = 0;
        fails = 0;
        done = 0;
    }

    public static int round() {
        return 1 + War.survivalTime() / (ROUND + BREAK);
    }

    /** Сила раунда: номер плюс проваленные задачи, плюс сложность. */
    private static int power() {
        return round() + fails + (War.difficulty() - 2);
    }

    /** Затишье между раундами. */
    public static boolean lull() {
        return War.survivalTime() % (ROUND + BREAK) >= ROUND;
    }

    // ------------------------------------------------------------------ запуск и снаряжение

    public static void start(MinecraftServer server, ServerPlayer by, int radius) {
        reset();
        War.createSurvival(by, Math.max(40, Math.min(600, radius)));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            War.join(p, War.factions().get(0));
            kit(p, true);
        }
    }

    public static void kit(ServerPlayer p, boolean first) {
        List<ItemStack> k = new ArrayList<>();
        if (first) {
            k.add(new ItemStack(ModItems.FIELD_SHOVEL.get()));
            k.add(new ItemStack(ModItems.TRENCH_KIT.get(), 2));
            k.add(new ItemStack(ModItems.DUGOUT_KIT.get()));
            k.add(new ItemStack(ModBlocks.BUNKER_CONCRETE_ITEM.get(), 32));
            k.add(new ItemStack(ModBlocks.CAMO_NET_ITEM.get(), 16));
            k.add(new ItemStack(ModItems.MEDKIT.get(), 2));
        }
        k.add(new ItemStack(ModBlocks.SANDBAGS_ITEM.get(), first ? 64 : 24));
        k.add(new ItemStack(ModItems.BANDAGE.get(), first ? 6 : 3));
        k.add(new ItemStack(ModItems.SMOKE_GRENADE.get(), first ? 2 : 1));
        give(p, k);
    }

    private static void give(ServerPlayer p, List<ItemStack> k) {
        for (ItemStack s : k) {
            if (!p.getInventory().add(s)) {
                p.spawnAtLocation(s);
            }
        }
    }

    // ------------------------------------------------------------------ раз в секунду

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<ServerPlayer> alive = alive(server);
        if (alive.isEmpty()) {
            finish(server);
            return;
        }
        if (War.prep()) {
            return;
        }
        int before = round();
        boolean wasLull = lull();
        War.tickSurvival();
        clock++;
        int r = round();
        if (r != before || clock == 1) {
            if (r != before) {
                War.broadcast(Component.translatable("message.harddayz.surv_round", r).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                for (ServerPlayer p : alive) {
                    p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1f, 1f);
                }
            }
            newTask(level, alive);
        } else if (!wasLull && lull()) {
            endTask(alive);
            War.broadcast(Component.translatable("message.harddayz.surv_lull", r, BREAK).withStyle(ChatFormatting.GREEN));
            for (ServerPlayer p : alive) {
                kit(p, false);
            }
        }
        if (!lull()) {
            drones(level, power());
            watch(level, alive, power());
            strikes(level, alive, power());
            taskTick(level, alive);
        }
        bar(server, alive, r);
    }

    private static List<ServerPlayer> alive(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        War.Faction f = War.factions().isEmpty() ? null : War.factions().get(0);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (f != null && f.has(p.getUUID()) && p.isAlive() && !p.isSpectator()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Таймер, засечка и задача — всем в матче, и погибшим-зрителям тоже. */
    private static void bar(MinecraftServer server, List<ServerPlayer> alive, int r) {
        int t = War.survivalTime(), in = t % (ROUND + BREAK);
        boolean lull = in >= ROUND;
        int left = lull ? ROUND + BREAK - in : ROUND - in;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (War.of(p.getUUID()) == null) {
                continue;
            }
            double[] h = HEAT.get(p.getUUID());
            double[] ho = HANDOFF.get(p.getUUID());
            int heat = h == null ? 0 : (int) (h[0] * 100);
            int hand = ho == null ? -1 : Math.max(0, (int) Math.ceil((ho[0] - server.overworld().getGameTime()) / 20.0));
            Net.toPlayer(p, new Net.SurvivalState(r, t, left, lull, heat, hand, DRONES.size(), alive.size(), lull ? 0 : task, taskHave, taskNeed, taskX, taskZ,
                    taskDone));
        }
    }

    // ------------------------------------------------------------------ разведка

    private static Vec3 center() {
        int[] c = War.city();
        return c == null ? Vec3.ZERO : new Vec3(c[0], 70, c[1]);
    }

    private static int radius() {
        int[] c = War.city();
        return c == null ? 150 : c[2];
    }

    private static void drones(ServerLevel level, int r) {
        DRONES.removeIf(id -> !(level.getEntity(id) instanceof Drone d) || !d.isAlive());
        List<ServerPlayer> alive = alive(level.getServer());
        // поиск: каждые ~20 с дрон перелетает — чаще туда, где люди, чем в пустоту
        if (clock % 20 == 0) {
            for (UUID id : DRONES) {
                if (level.getEntity(id) instanceof Drone d && !alive.isEmpty()) {
                    Vec3 go;
                    if (R.nextDouble() < 0.35 + 0.05 * r) {
                        Vec3 p = alive.get(R.nextInt(alive.size())).position();
                        go = p.add(R.nextGaussian() * 80, 0, R.nextGaussian() * 80); // догадка: где-то тут
                    } else {
                        go = center().add(R.nextGaussian() * radius() * 0.5, 0, R.nextGaussian() * radius() * 0.5);
                    }
                    d.retarget(go);
                }
            }
        }
        int want = War.botAllowed(War.BOT_RECON) ? Mth.clamp(1 + r / 2, 1, 7) : 0;
        if (DRONES.size() >= want || clock < nextDrone) {
            return;
        }
        nextDrone = clock + Math.max(12, 40 - r * 4);
        War.Faction ai = War.enemyFaction();
        if (ai == null || alive.isEmpty()) {
            return;
        }
        // взлетает недалеко от живого игрока: там мир точно загружен
        Vec3 near = alive.get(R.nextInt(alive.size())).position();
        double a = R.nextDouble() * Math.PI * 2;
        Vec3 from = new Vec3(near.x + Math.cos(a) * 100, 0, near.z + Math.sin(a) * 100);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(from.x), Mth.floor(from.z));
        from = from.add(0, y + 45, 0);
        Vec3 over = center().add(R.nextGaussian() * radius() * 0.4, 0, R.nextGaussian() * radius() * 0.4);
        Drone dr = Drone.launchShahed(level, from, over.subtract(from).multiply(1, 0, 1).normalize(), over, ai.botId(), Drone.Kind.WING_RECON);
        DRONES.add(dr.getUUID());
    }

    /** Видно ли человека сверху: открытое небо над головой, не под маскировочной сетью и не в лесу. */
    private static boolean exposed(ServerLevel level, ServerPlayer p) {
        BlockPos head = BlockPos.containing(p.getEyePosition());
        if (!level.canSeeSky(head)) {
            return false;
        }
        for (int dy = 1; dy <= 3; dy++) {
            if (level.getBlockState(head.above(dy)).is(ModBlocks.CAMO_NET.get())) {
                return false;
            }
        }
        return true;
    }

    /** Засечка копится, пока дрон держит человека в поле зрения, и тает, когда спрятался. */
    private static void watch(ServerLevel level, List<ServerPlayer> alive, int r) {
        List<Drone> eyes = new ArrayList<>();
        for (UUID id : DRONES) {
            if (level.getEntity(id) instanceof Drone d) {
                eyes.add(d);
            }
        }
        double dif = War.difficulty() == 1 ? 0.75 : War.difficulty() == 3 ? 1.3 : 1;
        for (ServerPlayer p : alive) {
            double[] h = HEAT.computeIfAbsent(p.getUUID(), k -> new double[] {0});
            boolean outside = p.position().distanceTo(new Vec3(center().x, p.getY(), center().z)) > radius();
            Drone seer = null;
            double best = 0;
            if (exposed(level, p)) {
                for (Drone d : eyes) {
                    double dist = Math.hypot(d.getX() - p.getX(), d.getZ() - p.getZ());
                    double f = 1.2 - dist / 150;
                    if (f > best) {
                        best = f;
                        seer = d;
                    }
                }
            }
            double gain = best <= 0 ? 0 : best * (0.2 + 0.025 * r) * dif * (p.getDeltaMovement().horizontalDistanceSqr() > 0.004 ? 1.5 : 1)
                    * (p.isCrouching() ? 0.5 : 1);
            if (outside) {
                gain = Math.max(gain, 0.5);
            }
            if (gain > 0) {
                h[0] = Math.min(1, h[0] + gain);
                if (seer != null && h[0] > 0.3) {
                    seer.retarget(p.position()); // дрон подходит ближе и кружит над целью
                }
            } else {
                h[0] = Math.max(0, h[0] - 0.3);
            }
            if (h[0] >= 1 && !HANDOFF.containsKey(p.getUUID())) {
                // засекли: координаты уходят на батарею — несколько секунд, чтобы уйти
                h[0] = 0.4;
                SEEN.put(p.getUUID(), new double[] {p.getX(), p.getY(), p.getZ(), clock});
                double delay = Math.max(3, (8.5 - r * 0.5) / dif);
                HANDOFF.put(p.getUUID(), new double[] {level.getGameTime() + delay * 20, p.getX(), p.getY(), p.getZ()});
                Net.toPlayer(p, new Net.Alert(Component.translatable(outside ? "message.harddayz.surv_outside" : "message.harddayz.surv_spotted").getString()));
                p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1f, 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ удары

    private static void strikes(ServerLevel level, List<ServerPlayer> alive, int r) {
        War.Faction ai = War.enemyFaction();
        if (ai == null) {
            return;
        }
        hunters(level, alive);
        long now = level.getGameTime();
        HANDOFF.entrySet().removeIf(e -> {
            double[] h = e.getValue();
            if (h[0] > now) {
                return false;
            }
            ServerPlayer prey = level.getServer().getPlayerList().getPlayer(e.getKey());
            double err = Math.max(3, (13 - r * 1.3) / (War.difficulty() == 3 ? 1.3 : 1));
            int attacks = 1 + Math.max(0, r - 2) / 3;
            for (int i = 0; i < attacks; i++) {
                attack(level, ai, new Vec3(h[1], h[2], h[3]), err, r, i * 40, prey);
            }
            return true;
        });
        if (clock >= nextHarass) { // беспокоящий огонь по площади, даже без засечки
            nextHarass = clock + Math.max(10, 34 - r * 3);
            Vec3 c = center();
            attack(level, ai, c.add(R.nextGaussian() * radius() * 0.45, 0, R.nextGaussian() * radius() * 0.45), 18, Math.max(1, r - 1), 0, null);
        }
    }

    /** Вес оружия: лёгкое — всегда, тяжёлое растёт с раундами. */
    private static final int[][] ARSENAL = {
            // {номер, бит разрешения, вес, тяжёлое}
            {0, War.BOT_MORTAR, 5, 0}, {1, War.BOT_GRAD, 5, 0}, {2, War.BOT_SHAHED, 3, 0}, {7, War.BOT_SHAHED, 4, 0},
            {3, War.BOT_CLUSTER, 2, 1}, {4, War.BOT_TOS, 2, 1}, {8, War.BOT_URAGAN, 2, 1}, {5, War.BOT_CRUISE, 1, 1}, {6, War.BOT_ISKANDER, 1, 1},
    };

    /** Время полёта, тиков: всего и на последнем участке, где снаряд уже настоящий. */
    private static int[] flight(int w) {
        return switch (w) {
            case 0 -> new int[] {136, 36};
            case 2 -> new int[] {200, 120};
            case 5 -> new int[] {140, 56};
            case 7 -> new int[] {90, 70};
            case 8 -> new int[] {262, 18};
            default -> new int[] {223, 20};
        };
    }

    /** Один удар: залп, серия или стая — с сигналом пуска. */
    private static void attack(ServerLevel level, War.Faction ai, Vec3 at, double err, int r, int delay, @Nullable ServerPlayer prey) {
        double sum = 0;
        double[] wt = new double[ARSENAL.length];
        for (int i = 0; i < ARSENAL.length; i++) {
            int[] a = ARSENAL[i];
            if (War.botAllowed(a[1]) && (a[0] != 7 || prey != null)) {
                wt[i] = a[2] * (a[3] == 1 ? 0.35 + 0.25 * r : 1);
                sum += wt[i];
            }
        }
        if (sum <= 0) {
            return;
        }
        double roll = R.nextDouble() * sum;
        int w = 0;
        for (int i = 0; i < ARSENAL.length; i++) {
            roll -= wt[i];
            if (roll <= 0 && wt[i] > 0) {
                w = ARSENAL[i][0];
                break;
            }
        }
        int n = switch (w) {
            case 0 -> 3 + R.nextInt(2 + r / 2);      // серия мин
            case 1 -> 6 + R.nextInt(5 + r);          // залп «Града»
            case 8 -> 4 + R.nextInt(3 + r / 2);      // «Ураган»
            case 4 -> 4 + R.nextInt(3);              // ТОС
            case 2 -> 2 + R.nextInt(2 + r / 3);      // стая Шахедов
            case 7 -> 2 + R.nextInt(2 + r / 3);      // дроны-камикадзе
            case 3 -> 1 + R.nextInt(2);
            default -> 1;
        };
        double spread = err * (w == 1 || w == 8 || w == 4 ? 1.6 : 1);
        double az = R.nextDouble() * Math.PI * 2; // одна батарея — одно направление
        int[] fl = flight(w);
        long launch = level.getGameTime() + delay;
        int gap = w == 1 || w == 8 ? 3 : w == 0 ? 25 : 10;
        for (int i = 0; i < n; i++) {
            Vec3 p = at.add(R.nextGaussian() * spread, 0, R.nextGaussian() * spread);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
            PENDING.add(new Object[] {new Vec3(p.x, y, p.z), w, ai.botId(), launch + fl[0] - fl[1] + (long) i * gap, prey == null ? null : prey.getUUID(), az});
        }
        // сигнал пуска: что, откуда, когда прилетит — всем в районе, на экран и на радар
        Vec3 src = at.add(Math.cos(az) * -600, 0, Math.sin(az) * -600);
        launchSound(level, w, src);
        var msg = new Net.SurvivalLaunch(w, n, (int) src.x, (int) src.z, (int) at.x, (int) at.z, fl[0] + delay);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (War.of(p.getUUID()) != null) {
                Net.toPlayer(p, msg);
            }
        }
    }

    /** Дроны-камикадзе: каждую секунду доворачивают на свою жертву (если её видно — точно, иначе туда, где видели). */
    private static void hunters(ServerLevel level, List<ServerPlayer> alive) {
        HUNTERS.entrySet().removeIf(e -> {
            if (!(level.getEntity(e.getKey()) instanceof Drone d) || !d.isAlive()) {
                return true;
            }
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getValue());
            if (p == null || !alive.contains(p)) {
                return false;
            }
            double[] s = SEEN.get(p.getUUID());
            boolean sees = exposed(level, p) && d.distanceTo(p) < 90;
            Vec3 go = sees ? p.position().add(p.getDeltaMovement().scale(10)) : s != null ? new Vec3(s[0], s[1], s[2]) : null;
            if (go != null) {
                d.retarget(go);
            }
            return false;
        });
    }

    /** Каждый тик: снаряды появляются на последнем участке полёта. */
    public static void tickShots(ServerLevel level) {
        long now = level.getGameTime();
        PENDING.removeIf(p -> {
            if ((long) p[3] > now) {
                return false;
            }
            Entity made = shoot(level, (Vec3) p[0], (int) p[1], (UUID) p[2], p.length > 5 ? (double) p[5] : R.nextDouble() * Math.PI * 2);
            if (made != null && p[4] != null) {
                HUNTERS.put(made.getUUID(), (UUID) p[4]);
            }
            return true;
        });
    }

    static @Nullable Entity shoot(ServerLevel level, Vec3 t, int w, UUID side) {
        return shoot(level, t, w, side, R.nextDouble() * Math.PI * 2);
    }

    static @Nullable Entity shoot(ServerLevel level, Vec3 t, int w, UUID side, double az) {
        Vec3 dir = new Vec3(Math.cos(az), 0, Math.sin(az));
        switch (w) {
            case 0, 1, 3, 4, 6, 8 -> {
                // мина или ракета прилетает издалека: заводим её на последнем участке настоящей дуги
                double range = w == 0 ? 230 : w == 8 ? 900 : 650, v = w == 0 ? 3.8 : w == 8 ? 6.3 * 1.2 : 6.3, g = Missile.Kind.GRAD.gravity;
                double s = Math.min(1, range * g / (v * v));
                double th = Math.PI / 2 - Math.asin(s) / 2; // навесное решение
                double vx = v * Math.cos(th), vy = v * Math.sin(th);
                Vec3 src = t.subtract(dir.scale(range));
                // на нисходящей ветви; если там выше потолка мира — ещё ближе к цели, дугу не ломаем
                double rest = Math.min(range, 150), t0 = 0;
                Vec3 pos = src;
                for (; rest > 30; rest -= 10) {
                    t0 = (range - rest) / vx;
                    pos = src.add(dir.scale(vx * t0)).add(0, vy * t0 - g * t0 * t0 / 2, 0);
                    if (pos.y < level.getMaxBuildHeight() - 20) {
                        break;
                    }
                }
                Vec3 vel = dir.scale(vx).add(0, vy - g * t0, 0);
                Missile m = Missile.launch(level, Missile.Kind.GRAD, pos, vel, t, null).owner(side);
                switch (w) {
                    case 0 -> m.powerMul(0.7f).look(7);
                    case 3 -> m.payload(1);
                    case 4 -> m.thermobaric();
                    case 6 -> m.powerMul(3.4f).look(1);
                    case 8 -> m.powerMul(1.35f);
                    default -> {
                    }
                }
                return m;
            }
            case 7 -> { // камикадзе заходит низко с ближней дистанции
                Vec3 from = t.subtract(dir.scale(70)).add(0, 22, 0);
                return Drone.launchShahed(level, from, dir, t, side, Drone.Kind.GERBERA);
            }
            default -> {
                Drone.Kind k = w == 5 ? Drone.Kind.CRUISE : R.nextInt(3) == 0 ? Drone.Kind.SHAHED_131 : Drone.Kind.SHAHED;
                Vec3 from = t.subtract(dir.scale(150)).add(0, 45, 0);
                return Drone.launchShahed(level, from, dir, t, side, k);
            }
        }
    }

    // ------------------------------------------------------------------ задачи раунда

    private static void newTask(ServerLevel level, List<ServerPlayer> alive) {
        taskDone = false;
        taskHave = 0;
        Vec3 c = center();
        double mx = 0, mz = 0;
        for (ServerPlayer p : alive) {
            mx += p.getX() / alive.size();
            mz += p.getZ() / alive.size();
        }
        task = 1 + R.nextInt(3);
        if (task == 3 && !War.botAllowed(War.BOT_RECON)) {
            task = 1;
        }
        for (int i = 0; i < 30; i++) { // точка в районе, не слишком близко к людям
            double a = R.nextDouble() * Math.PI * 2, d = 60 + R.nextDouble() * 80;
            taskX = (int) (mx + Math.cos(a) * d);
            taskZ = (int) (mz + Math.sin(a) * d);
            if (Math.hypot(taskX - c.x, taskZ - c.z) < radius() - 10) {
                break;
            }
        }
        taskNeed = task == 2 ? 40 : task == 3 ? 1 + round() / 3 : 1;
        War.broadcast(Component.translatable("message.harddayz.surv_task_" + task, taskNeed, taskX, taskZ).withStyle(ChatFormatting.AQUA));
    }

    private static void taskTick(ServerLevel level, List<ServerPlayer> alive) {
        if (task == 0 || taskDone) {
            return;
        }
        if (task == 1 || task == 2) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, taskX, taskZ);
            var dust = new DustParticleOptions(new org.joml.Vector3f(0.3f, 1f, 0.5f), 2.5f);
            for (int i = 0; i < 10; i++) {
                com.harddayz.Compat.particles(level, dust, taskX + 0.5, y + 1 + i * 2, taskZ + 0.5, 2, 0.2, 0.5, 0.2, 0);
            }
            boolean on = false;
            for (ServerPlayer p : alive) {
                on |= Math.hypot(p.getX() - taskX - 0.5, p.getZ() - taskZ - 0.5) < (task == 1 ? 4 : 7);
            }
            if (on) {
                taskHave++;
            }
        }
        if (taskHave >= taskNeed) {
            taskDone = true;
            done++;
            War.broadcast(Component.translatable("message.harddayz.surv_task_done").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            for (ServerPlayer p : alive) {
                p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
                give(p, List.of(new ItemStack(ModItems.MEDKIT.get()), new ItemStack(ModBlocks.BUNKER_CONCRETE_ITEM.get(), 16),
                        new ItemStack(ModBlocks.CAMO_NET_ITEM.get(), 8), new ItemStack(ModItems.SMOKE_GRENADE.get(), 2)));
            }
        }
    }

    private static void endTask(List<ServerPlayer> alive) {
        if (task != 0 && !taskDone) {
            fails++;
            War.broadcast(Component.translatable("message.harddayz.surv_task_fail").withStyle(ChatFormatting.RED));
        }
        task = 0;
    }

    /** Игрок сбил вражеский дрон. */
    public static void droneDown(Drone d) {
        if (War.survival() && task == 3 && !taskDone && !lull()) {
            taskHave++;
        }
    }

    // ------------------------------------------------------------------ смерть и конец

    /** Погибший в этом режиме дальше только смотрит. */
    public static void died(ServerPlayer p) {
        War.broadcast(Component.translatable("message.harddayz.surv_dead", p.getDisplayName(), round(), War.survivalTime() / 60,
                String.format("%02d", War.survivalTime() % 60)).withStyle(ChatFormatting.DARK_RED));
        War.survivalDied(p.getUUID());
    }

    public static void respawned(ServerPlayer p) {
        if (War.survival() && War.survivalDead(p.getUUID())) {
            p.setGameMode(GameType.SPECTATOR);
        }
    }

    private static void finish(MinecraftServer server) {
        int t = War.survivalTime();
        int best = War.survivalBest(t);
        War.broadcast(Component.translatable("message.harddayz.surv_over", round(), t / 60, String.format("%02d", t % 60), best / 60,
                String.format("%02d", best % 60)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        War.broadcast(Component.translatable("message.harddayz.surv_tasks", done).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() && War.survivalDead(p.getUUID())) {
                p.setGameMode(GameType.SURVIVAL);
                Vec3 c = center();
                ServerLevel lv = server.overworld();
                int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(c.x), Mth.floor(c.z));
                p.teleportTo(lv, c.x, y + 1, c.z, p.getYRot(), 0);
            }
        }
        War.end();
        War.sendLives();
        reset();
    }

    public static @Nullable String status() {
        return War.survival() ? Component.translatable("screen.harddayz.surv_status", round(), War.survivalTime() / 60,
                String.format("%02d", War.survivalTime() % 60), DRONES.size()).getString() : null;
    }

    /** Звук пуска сразу в момент сигнала: издалека, с задержкой по расстоянию. */
    static void launchSound(ServerLevel level, int w, Vec3 src) {
        ModSounds.launch(level, src.x, src.y + 2, src.z,
                (w == 0 ? ModSounds.MORTAR_FIRE : w == 6 ? ModSounds.MISSILE_LAUNCH : w == 2 || w == 7 ? ModSounds.LAUNCH_SHAHED : ModSounds.ROCKET_LAUNCH).get(), 1200, 1f);
    }
}
