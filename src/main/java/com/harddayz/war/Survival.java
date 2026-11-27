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
 * Режим «Выжить под обстрелом». Разведдроны кружат над районом и ищут людей; кого увидели — по тому месту бьют.
 * Каждый раунд дронов больше, удары чаще, точнее и тяжелее. Под крышей, в лесу и под землёй дрон не видит.
 * Погибший смотрит дальше как зритель; конец — когда не осталось живых.
 */
public final class Survival {
    public static final int ROUND = 150;
    public static final int BREAK = 25;
    private static final Random R = new Random();
    /** Где и когда видели игрока. */
    private static final Map<UUID, double[]> SEEN = new HashMap<>();
    private static final List<UUID> DRONES = new ArrayList<>();
    private static int clock;
    private static int nextStrike;
    private static int nextHarass;
    private static int nextDrone;

    private Survival() {
    }

    public static void reset() {
        SEEN.clear();
        DRONES.clear();
        PENDING.clear();
        HUNTERS.clear();
        clock = 0;
        nextStrike = 0;
        nextHarass = 0;
        nextDrone = 0;
    }

    public static int round() {
        return 1 + War.survivalTime() / (ROUND + BREAK);
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
        if (r != before) {
            War.broadcast(Component.translatable("message.harddayz.surv_round", r).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            for (ServerPlayer p : alive) {
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1f, 1f);
            }
        } else if (!wasLull && lull()) {
            War.broadcast(Component.translatable("message.harddayz.surv_lull", r, BREAK).withStyle(ChatFormatting.GREEN));
            for (ServerPlayer p : alive) {
                kit(p, false);
            }
        }
        bar(level.getServer(), alive, r);
        if (lull()) {
            return;
        }
        drones(level, r);
        watch(level, alive, r);
        strikes(level, alive, r);
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

    /** Таймер внизу экрана — всем в матче, и погибшим-зрителям тоже. */
    private static void bar(MinecraftServer server, List<ServerPlayer> alive, int r) {
        int t = War.survivalTime(), in = t % (ROUND + BREAK);
        boolean lull = in >= ROUND;
        int left = lull ? ROUND + BREAK - in : ROUND - in;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (War.of(p.getUUID()) == null) {
                continue;
            }
            double[] s = SEEN.get(p.getUUID());
            boolean hot = s != null && clock - s[3] < 20 && alive.contains(p);
            Net.toPlayer(p, new Net.SurvivalState(r, t, left, lull, hot, DRONES.size(), alive.size()));
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
        // поиск: каждые ~20 с дрон перелетает — чаще туда, где люди, чем в пустоту
        if (clock % 20 == 0) {
            List<ServerPlayer> alive = alive(level.getServer());
            for (UUID id : DRONES) {
                if (level.getEntity(id) instanceof Drone d && !alive.isEmpty()) {
                    double[] seen = null;
                    for (double[] s : SEEN.values()) {
                        if (clock - s[3] < 30 && (seen == null || s[3] > seen[3])) {
                            seen = s;
                        }
                    }
                    Vec3 go;
                    if (seen != null) {
                        go = new Vec3(seen[0], seen[1], seen[2]).add(R.nextGaussian() * 25, 0, R.nextGaussian() * 25);
                    } else if (R.nextDouble() < 0.45 + 0.05 * r) {
                        Vec3 p = alive.get(R.nextInt(alive.size())).position();
                        go = p.add(R.nextGaussian() * 70, 0, R.nextGaussian() * 70); // догадка: где-то тут
                    } else {
                        go = center().add(R.nextGaussian() * radius() * 0.5, 0, R.nextGaussian() * radius() * 0.5);
                    }
                    d.retarget(go);
                }
            }
        }
        int want = War.botAllowed(War.BOT_RECON) ? Math.min(6, 1 + r / 2) : 0;
        if (DRONES.size() >= want || clock < nextDrone) {
            return;
        }
        nextDrone = clock + Math.max(15, 45 - r * 4);
        Vec3 c = center();
        // взлетает недалеко от живого игрока: там мир точно загружен
        List<ServerPlayer> alive = alive(level.getServer());
        Vec3 near = alive.isEmpty() ? c : alive.get(R.nextInt(alive.size())).position();
        double a = R.nextDouble() * Math.PI * 2, d = 90;
        Vec3 from = new Vec3(near.x + Math.cos(a) * d, 0, near.z + Math.sin(a) * d);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(from.x), Mth.floor(from.z));
        from = from.add(0, y + 45, 0);
        Vec3 over = c.add(R.nextGaussian() * radius() * 0.4, 0, R.nextGaussian() * radius() * 0.4);
        War.Faction ai = War.enemyFaction();
        if (ai == null) {
            return;
        }
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

    private static void watch(ServerLevel level, List<ServerPlayer> alive, int r) {
        List<Drone> eyes = new ArrayList<>();
        for (UUID id : DRONES) {
            if (level.getEntity(id) instanceof Drone d) {
                eyes.add(d);
            }
        }
        for (ServerPlayer p : alive) {
            boolean outside = p.position().distanceTo(new Vec3(center().x, p.getY(), center().z)) > radius();
            Drone seer = null;
            double chance = 0;
            for (Drone d : eyes) {
                double dist = Math.hypot(d.getX() - p.getX(), d.getZ() - p.getZ());
                if (dist > 140) {
                    continue;
                }
                double c = 0.4 + 0.05 * r + (p.getDeltaMovement().horizontalDistanceSqr() > 0.004 ? 0.25 : 0) - (p.isCrouching() ? 0.2 : 0) - dist / 400;
                if (c > chance && exposed(level, p)) {
                    chance = c;
                    seer = d;
                }
            }
            if (outside) {
                chance = 1; // вышел из района — виден всем
            }
            if (chance > 0 && R.nextDouble() < chance) {
                double[] was = SEEN.get(p.getUUID());
                SEEN.put(p.getUUID(), new double[] {p.getX(), p.getY(), p.getZ(), clock});
                if (seer != null) {
                    seer.retarget(p.position()); // дрон подходит ближе и кружит над целью
                }
                if (was == null || clock - was[3] > 12) {
                    Net.toPlayer(p, new Net.Alert(Component.translatable(outside ? "message.harddayz.surv_outside" : "message.harddayz.surv_spotted").getString()));
                    p.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1f, 0.6f);
                }
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
        if (clock >= nextStrike) {
            ServerPlayer target = null;
            double[] spot = null;
            for (ServerPlayer p : alive) {
                double[] s = SEEN.get(p.getUUID());
                if (s != null && clock - s[3] < 20 && (spot == null || s[3] > spot[3])) {
                    spot = s;
                    target = p;
                }
            }
            if (spot != null) {
                nextStrike = clock + Math.max(4, 13 - r);
                double err = Math.max(3, 13 - r * 1.5);
                Vec3 at = new Vec3(spot[0], spot[1], spot[2]);
                int attacks = 1 + r / 3;
                for (int i = 0; i < attacks; i++) {
                    attack(level, ai, at, err, r, i * 30, target);
                }
                if (target != null) {
                    target.displayClientMessage(Component.translatable("message.harddayz.surv_incoming").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                }
            }
        }
        if (clock >= nextHarass) { // беспокоящий огонь по площади, даже без засечки
            nextHarass = clock + Math.max(8, 30 - r * 3);
            Vec3 c = center();
            attack(level, ai, c.add(R.nextGaussian() * radius() * 0.45, 0, R.nextGaussian() * radius() * 0.45), 18, r, 0, null);
        }
    }

    /** Вес оружия: лёгкое — всегда, тяжёлое растёт с раундами. */
    private static final int[][] ARSENAL = {
            // {номер, бит разрешения, вес, тяжёлое}
            {0, War.BOT_MORTAR, 5, 0}, {1, War.BOT_GRAD, 5, 0}, {2, War.BOT_SHAHED, 3, 0}, {7, War.BOT_SHAHED, 4, 0},
            {3, War.BOT_CLUSTER, 2, 1}, {4, War.BOT_TOS, 2, 1}, {8, War.BOT_URAGAN, 2, 1}, {5, War.BOT_CRUISE, 1, 1}, {6, War.BOT_ISKANDER, 1, 1},
    };

    /** Один удар: залп, серия или стая. */
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
        for (int i = 0; i < n; i++) {
            Vec3 p = at.add(R.nextGaussian() * spread, 0, R.nextGaussian() * spread);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(p.x), Mth.floor(p.z));
            PENDING.add(new Object[] {new Vec3(p.x, y, p.z), w, ai.botId(), level.getGameTime() + delay + i * (w == 1 || w == 8 ? 3 : w == 0 ? 25 : 10),
                    prey == null ? null : prey.getUUID()});
        }
    }

    /** Дроны-камикадзе: каждую секунду доворачивают на свою жертву (если её видно — точно, иначе туда, где видели). */
    private static final Map<UUID, UUID> HUNTERS = new HashMap<>();

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

    private static final List<Object[]> PENDING = new ArrayList<>();

    /** Каждый тик: удары с задержкой внутри залпа. */
    public static void tickShots(ServerLevel level) {
        long now = level.getGameTime();
        PENDING.removeIf(p -> {
            if ((long) p[3] > now) {
                return false;
            }
            Entity made = shoot(level, (Vec3) p[0], (int) p[1], (UUID) p[2]);
            if (made != null && p.length > 4 && p[4] != null) {
                HUNTERS.put(made.getUUID(), (UUID) p[4]);
            }
            return true;
        });
    }

    static @Nullable Entity shoot(ServerLevel level, Vec3 t, int w, UUID side) {
        double az = R.nextDouble() * Math.PI * 2;
        Vec3 dir = new Vec3(Math.cos(az), 0, Math.sin(az));
        switch (w) {
            case 0, 1, 3, 4, 6, 8 -> {
                // мина или ракета прилетает издалека: заводим её на последних 150 блоках настоящей дуги
                double range = w == 0 ? 230 : w == 8 ? 900 : 650, v = w == 0 ? 3.8 : w == 8 ? 6.3 * 1.2 : 6.3, g = Missile.Kind.GRAD.gravity;
                double s = Math.min(1, range * g / (v * v));
                double th = Math.PI / 2 - Math.asin(s) / 2; // навесное решение
                double vx = v * Math.cos(th), vy = v * Math.sin(th);
                Vec3 src = t.subtract(dir.scale(range));
                // заводим на нисходящей ветви; если там выше потолка мира — ещё ближе к цели, дугу не ломаем
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
                ModSounds.launch(level, src.x, t.y, src.z, (w == 0 ? ModSounds.MORTAR_FIRE : w == 6 ? ModSounds.MISSILE_LAUNCH : ModSounds.ROCKET_LAUNCH).get(), 900, 1f);
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
}
