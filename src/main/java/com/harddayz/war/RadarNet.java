package com.harddayz.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.harddayz.entity.Drone;
import com.harddayz.entity.Missile;
import com.harddayz.network.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Сеть обнаружения: РЛС передают сюда засечённые чужие ракеты и дроны. Сеть общая для фракции
 * (без войны — для владельца). Экран радара показывает только то, что реально видят работающие РЛС.
 * Сирены сети воют, когда цель подлетает к ним.
 */
public final class RadarNet {
    public static final int RADAR_RANGE = 1600;
    public static final int SIREN_RANGE = 400;

    public static final class Contact {
        public final int id;
        public String type;
        public Vec3 pos;
        public Vec3 vel;
        public long seen;

        Contact(int id) {
            this.id = id;
        }
    }

    private static final Map<String, Map<Integer, Contact>> NETS = new HashMap<>();

    /** Засечка точки пуска: оценка места, погрешность, сколько пусков слилось. */
    public static final class Launch {
        public final String type;
        public Vec3 pos;
        public double err;
        public int count = 1;
        public long last;

        Launch(String type, Vec3 pos, double err, long now) {
            this.type = type;
            this.pos = pos;
            this.err = err;
            this.last = now;
        }
    }

    /** Отметка на радаре: разрыв на земле или сбитая цель. */
    public record Mark(String type, Vec3 pos, long time) {
    }

    private static final Map<String, List<Launch>> LAUNCHES = new HashMap<>();
    private static final Map<String, java.util.Set<java.util.UUID>> DETECTED = new HashMap<>();
    private static final Map<String, List<Mark>> MARKS = new HashMap<>();
    /** Позиции РЛС за последние секунды: кто «видит» разрывы. */
    private static final Map<String, List<long[]>> RADAR_SEEN = new HashMap<>();
    private static final java.util.Random RND = new java.util.Random();

    /**
     * РЛС впервые увидела снаряд: по траектории считаем, откуда он вылетел. Обычная РЛС ошибается тем больше, чем дальше пуск,
     * контрбатарейная ({@code precise}) — на несколько блоков.
     */
    public static void detectLaunch(String key, Entity e, Vec3 radar, boolean precise, long now) {
        if (!(e instanceof com.harddayz.entity.Threat th) || th.origin() == null) {
            return;
        }
        java.util.Set<java.util.UUID> seen = DETECTED.computeIfAbsent(key, k -> new java.util.HashSet<>());
        if (!seen.add(e.getUUID())) {
            return;
        }
        if (seen.size() > 4000) {
            seen.clear();
        }
        Vec3 o = th.origin();
        double d = o.distanceTo(radar);
        double err = precise ? Math.min(60, 10 + d * 0.03) : Math.min(260, 35 + d * 0.2);
        double ang = RND.nextDouble() * Math.PI * 2, off = err * Math.sqrt(RND.nextDouble()) * 0.8;
        Vec3 est = o.add(Math.cos(ang) * off, 0, Math.sin(ang) * off);
        String type = th.launchType();
        List<Launch> list = LAUNCHES.computeIfAbsent(key, k -> new ArrayList<>());
        for (Launch l : list) {
            if (l.type.equals(type) && l.pos.distanceTo(est) < l.err + err) {
                // ещё один пуск оттуда же — точка уточняется
                double w = l.err * l.err / (l.err * l.err + err * err);
                l.pos = l.pos.add(est.subtract(l.pos).scale(w));
                l.err = Math.max(precise ? 8 : 25, Math.min(l.err, err) * 0.8);
                l.count++;
                l.last = now;
                return;
            }
        }
        list.add(new Launch(type, est, err, now));
        if (list.size() > 40) {
            list.remove(0);
        }
    }

    public static List<Launch> launches(String key, long now) {
        List<Launch> list = LAUNCHES.getOrDefault(key, List.of());
        if (!list.isEmpty()) {
            list.removeIf(l -> now - l.last > 20 * 60 * 15); // 15 минут
        }
        return list;
    }

    /** Взрыв: отметка у всех сетей, чьи РЛС видят это место. */
    public static void blast(Vec3 at, boolean air, long now) {
        for (var e : RADAR_SEEN.entrySet()) {
            e.getValue().removeIf(r -> now - r[3] > 60);
            for (long[] r : e.getValue()) {
                double dx = at.x - r[0], dz = at.z - r[2];
                if (dx * dx + dz * dz < (double) RADAR_RANGE * RADAR_RANGE) {
                    List<Mark> m = MARKS.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
                    m.add(new Mark(air ? "down" : "hit", at, now));
                    if (m.size() > 80) {
                        m.remove(0);
                    }
                    break;
                }
            }
        }
    }

    public static List<Mark> marks(String key, long now) {
        List<Mark> m = MARKS.getOrDefault(key, List.of());
        if (!m.isEmpty()) {
            m.removeIf(x -> now - x.time() > 20 * 60);
        }
        return m;
    }
    private static final Map<String, Integer> RADARS = new HashMap<>();
    private static final Map<String, Long> LAST_ALERT = new HashMap<>();

    private RadarNet() {
    }

    /** Ключ сети: фракция игрока или он сам. */
    public static String key(@Nullable UUID owner) {
        War.Faction f = War.of(owner);
        return f != null ? "f:" + f.id : owner == null ? "none" : "p:" + owner;
    }

    public static String typeOf(Entity e) {
        if (e instanceof Missile m) {
            return switch (m.kind()) {
                case BALLISTIC -> "ballistic";
                case GRAD -> "rocket";
                default -> "missile";
            };
        }
        if (e instanceof Drone d) {
            return switch (d.kind()) {
                case SHAHED, SHAHED_131 -> "shahed";
                case SHAHED_238 -> "jet";
                case GERBERA -> "unknown";
                default -> "drone";
            };
        }
        return "unknown";
    }

    /** РЛС засекла цель. */
    public static void report(String key, Entity e, long now) {
        Contact c = NETS.computeIfAbsent(key, k -> new HashMap<>()).computeIfAbsent(e.getId(), Contact::new);
        c.type = typeOf(e);
        c.pos = e.position();
        c.vel = e.getDeltaMovement();
        c.seen = now;
    }

    /** Где стоят живые РЛС фракции (они тоже воют сиреной при налёте). */
    private static final Map<String, List<int[]>> RADAR_POS = new java.util.HashMap<>();

    public static void radarAt(String key, net.minecraft.core.BlockPos p, long now) {
        List<long[]> seen = RADAR_SEEN.computeIfAbsent(key, k -> new ArrayList<>());
        seen.removeIf(r -> r[0] == p.getX() && r[2] == p.getZ() || now - r[3] > 60);
        seen.add(new long[] {p.getX(), p.getY(), p.getZ(), now});
    }

    public static void radarAlive(String key, long now) {
        RADARS.merge(key, 1, Integer::sum);
    }

    public static List<Contact> contacts(String key, long now) {
        List<Contact> out = new ArrayList<>();
        Map<Integer, Contact> m = NETS.get(key);
        if (m == null) {
            return out;
        }
        for (Iterator<Contact> it = m.values().iterator(); it.hasNext();) {
            Contact c = it.next();
            if (now - c.seen > 200) {
                it.remove(); // метка устарела
            } else {
                out.add(c);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ сирены и тревога

    private static final Map<String, Integer> RADAR_COUNT = new HashMap<>();

    public static int radarCount(String key) {
        return RADAR_COUNT.getOrDefault(key, 0);
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) {
            return;
        }
        RADAR_COUNT.clear();
        RADAR_COUNT.putAll(RADARS);
        RADARS.clear();
        ServerLevel level = server.overworld();
        Map<String, List<int[]>> all = new java.util.HashMap<>();
        War.sirens().forEach((k, v) -> all.computeIfAbsent(k, x -> new java.util.ArrayList<>()).addAll(v));
        RADAR_POS.forEach((k, v) -> all.computeIfAbsent(k, x -> new java.util.ArrayList<>()).addAll(v));
        RADAR_POS.clear();
        for (Map.Entry<String, List<int[]>> e : all.entrySet()) {
            String key = e.getKey();
            List<Contact> cs = contacts(key, now);
            int near = 0;
            for (Contact c : cs) {
                if (now - c.seen > 40) {
                    continue;
                }
                for (int[] s : e.getValue()) {
                    double dx = c.pos.x - s[0];
                    double dz = c.pos.z - s[2];
                    if (dx * dx + dz * dz < SIREN_RANGE * SIREN_RANGE) {
                        near++;
                        break;
                    }
                }
            }
            if (near == 0) {
                continue;
            }
            Long last = LAST_ALERT.get(key);
            if (last != null && now - last < 120) {
                continue;
            }
            LAST_ALERT.put(key, now);
            for (int[] s : e.getValue()) {
                BlockPos pos = new BlockPos(s[0], s[1], s[2]);
                if (level.isLoaded(pos)) {
                    level.playSound(null, pos, com.harddayz.registry.ModSounds.SIREN.get(), SoundSource.BLOCKS, 10f, 1f);
                }
            }
            Component msg = Component.translatable("hud.harddayz.air_raid", near).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (key(p.getUUID()).equals(key)) {
                    Net.toPlayer(p, new Net.Alert(msg.getString()));
                }
            }
        }
    }

    /** Есть ли в сети фракции свежая метка этой цели (для ЗРК). */
    public static boolean tracked(String key, Entity e, long now) {
        Map<Integer, Contact> m = NETS.get(key);
        Contact c = m == null ? null : m.get(e.getId());
        return c != null && now - c.seen < 40;
    }

    public static void clear() {
        LAUNCHES.clear();
        MARKS.clear();
        RADAR_SEEN.clear();
        NETS.clear();
        RADARS.clear();
        RADAR_COUNT.clear();
        LAST_ALERT.clear();
    }
}
