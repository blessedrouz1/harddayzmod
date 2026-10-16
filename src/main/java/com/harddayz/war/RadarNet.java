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
    public static final int RADAR_RANGE = 600;
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

    public static void radarAt(String key, net.minecraft.core.BlockPos p) {
        RADAR_POS.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(new int[] {p.getX(), p.getY(), p.getZ()});
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
        NETS.clear();
        RADARS.clear();
        RADAR_COUNT.clear();
        LAST_ALERT.clear();
    }
}
