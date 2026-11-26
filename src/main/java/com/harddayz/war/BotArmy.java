package com.harddayz.war;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

import com.harddayz.Compat;
import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;
import com.harddayz.registry.ModEntities;
import com.harddayz.world.FrontMap;
import com.harddayz.world.FrontWorld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Штаб ботов стороны: свой бюджет, покупает и разворачивает технику (РСЗО, ПВО, РЛС), запускает разведку
 * и бьёт по тому, что видит своя сеть: засечки пусков, техника, скопления пехоты.
 */
public final class BotArmy {
    /** Что может купить штаб ботов: вид, цена, сколько держать (по сложности 1..3). */
    private record Buy(Launcher.Kind kind, int price, int[] cap, double depth) {
    }

    private static final Buy[] SHOP = {
            new Buy(Launcher.Kind.RADAR, 700, new int[] {1, 1, 1}, 0.25),
            new Buy(Launcher.Kind.GRAD, 900, new int[] {1, 1, 2}, 0.55),
            new Buy(Launcher.Kind.SAM, 900, new int[] {1, 1, 2}, 0.45),
            new Buy(Launcher.Kind.GRAD_P, 450, new int[] {0, 1, 2}, 0.75),
            new Buy(Launcher.Kind.URAGAN, 1400, new int[] {0, 1, 1}, 0.4),
            new Buy(Launcher.Kind.ISKANDER, 2200, new int[] {0, 0, 1}, 0.12),
    };
    private static final Random R = new Random();

    private BotArmy() {
    }

    /** Раз в секунду для каждой стороны с ботами. */
    static void tick(ServerLevel level, War.Faction f, int seconds) {
        if (seconds % 60 == 0) {
            int held = (int) War.points().stream().filter(p -> f.id.equals(p.owner)).count();
            f.botBudget += (int) ((90 + held * 45) * (f.ai ? 0.6 + 0.3 * War.difficulty() : 1));
        }
        if (seconds % 10 == 3) {
            assets(level, f);
        }
        if (seconds % 15 == 7) {
            buy(level, f);
        }
        if (seconds % 5 == 1) {
            fire(level, f);
        }
        if (seconds % 150 == 40) {
            recon(level, f);
        }
    }

    // ------------------------------------------------------------------ техника

    /** Держим чанки своей техники загруженными и убираем уничтоженную из списка. */
    private static void assets(ServerLevel level, War.Faction f) {
        f.botAssets.removeIf(a -> {
            String[] p = a.split(";");
            BlockPos at = new BlockPos(Integer.parseInt(p[2]), 64, Integer.parseInt(p[3]));
            Compat.ticket(level, new ChunkPos(at), 2);
            if (!level.isPositionEntityTicking(at)) {
                return false;
            }
            Entity e = level.getEntity(UUID.fromString(p[0]));
            if (!(e instanceof Launcher l) || l.wrecked()) {
                return true;
            }
            if (l.ammo() < l.kind().capacity && f.botBudget >= 80 && R.nextInt(4) == 0) {
                f.botBudget -= 80; // подвоз боекомплекта
                l.resupply();
            }
            return false;
        });
    }

    private static List<Launcher> live(ServerLevel level, War.Faction f) {
        List<Launcher> out = new ArrayList<>();
        for (String a : f.botAssets) {
            if (level.getEntity(UUID.fromString(a.split(";")[0])) instanceof Launcher l && !l.wrecked()) {
                out.add(l);
            }
        }
        return out;
    }

    private static int count(War.Faction f, Launcher.Kind k) {
        int n = 0;
        for (String a : f.botAssets) {
            if (a.split(";")[1].equals(k.name())) {
                n++;
            }
        }
        return n;
    }

    private static void buy(ServerLevel level, War.Faction f) {
        int d = War.difficulty() - 1;
        for (Buy b : SHOP) {
            int cap = f.ai ? b.cap[d] : Math.min(b.cap[1], Math.max(0, f.bots / 6));
            if (count(f, b.kind) >= cap || f.botBudget < b.price) {
                continue;
            }
            Vec3 at = rear(level, f, b.depth);
            if (at == null) {
                return;
            }
            Launcher l = ModEntities.LAUNCHER.get().create(level);
            if (l == null) {
                return;
            }
            l.setKind(b.kind);
            l.setPos(at);
            l.setYRot(R.nextFloat() * 360);
            l.setOwner(f.botId());
            l.resupply();
            l.netByBots();
            level.addFreshEntity(l);
            f.botBudget -= b.price;
            f.botAssets.add(l.getUUID() + ";" + b.kind.name() + ";" + Mth.floor(at.x) + ";" + Mth.floor(at.z));
            War.save();
            tell(level, f, Component.translatable("message.harddayz.bots_deploy", Component.translatable("entity.harddayz.kind." + b.kind.name().toLowerCase(java.util.Locale.ROOT)),
                    Mth.floor(at.x), Mth.floor(at.z)));
            return; // по одной покупке за раз
        }
    }

    /** Позиция в тылу: от базы к центру карты на долю пути depth, вбок до 300 блоков. */
    private static @Nullable Vec3 rear(ServerLevel level, War.Faction f, double depth) {
        int[] b = FrontWorld.baseOf(level.getServer(), f);
        if (b == null && !f.hqs.isEmpty()) {
            b = f.hqs.get(0);
        }
        if (b == null) {
            return null;
        }
        Vec3 to = new Vec3(-b[0], 0, -b[2]);
        double dist = to.length();
        to = dist < 1 ? new Vec3(1, 0, 0) : to.scale(1 / dist);
        if (FrontWorld.generator(level.getServer()) == null) {
            dist = 160; // обычный мир: вокруг штаба
        }
        for (int i = 0; i < 12; i++) {
            double along = dist * depth * (0.8 + R.nextDouble() * 0.4), side = (R.nextDouble() - 0.5) * 600;
            int x = Mth.floor(b[0] + to.x * along - to.z * side), z = Mth.floor(b[2] + to.z * along + to.x * side);
            if (Math.abs(x) > FrontMap.HALF - 80 || Math.abs(z) > FrontMap.HALF - 80) {
                continue;
            }
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (!level.getFluidState(p.below()).isEmpty() || !level.noCollision(new net.minecraft.world.phys.AABB(p).inflate(1.5, 0, 1.5).expandTowards(0, 2, 0))) {
                continue;
            }
            Integer want = FrontWorld.groundAt(level, x, z);
            if (want != null && Math.abs(want - y) > 2) {
                continue; // крыша или яма
            }
            return Vec3.atBottomCenterOf(p);
        }
        return null;
    }

    // ------------------------------------------------------------------ огонь

    /** Цели, которые знает сеть стороны: засечки пусков важнее, потом техника, потом пехота. */
    private static List<Vec3> targets(ServerLevel level, War.Faction f, List<Vec3> priority) {
        String key = RadarNet.key(f.botId());
        long now = level.getGameTime();
        List<Vec3> out = new ArrayList<>();
        for (RadarNet.Launch l : RadarNet.launches(key, now)) {
            if (now - l.last < 1200 && l.err < 60) {
                priority.add(l.pos);
            }
        }
        for (RadarNet.Spot s : RadarNet.ground(key, now)) {
            if (s.type.equals("veh")) {
                priority.add(s.pos);
            } else {
                out.add(s.pos);
            }
        }
        return out;
    }

    /** Рядом с целью свои — по ней не бьём. */
    private static boolean friendly(ServerLevel level, War.Faction f, Vec3 t) {
        for (BotCommander.Squad s : War.squads()) {
            if (s.faction.equals(f.id) && s.men > 0 && Math.hypot(s.x - t.x, s.z - t.z) < 45) {
                return true;
            }
        }
        for (ServerPlayer p : level.players()) {
            if (f.has(p.getUUID()) && p.distanceToSqr(t) < 45 * 45) {
                return true;
            }
        }
        return false;
    }

    private static void fire(ServerLevel level, War.Faction f) {
        if (War.prep()) {
            return;
        }
        List<Vec3> hot = new ArrayList<>();
        List<Vec3> soft = targets(level, f, hot);
        if (hot.isEmpty() && soft.isEmpty()) {
            return;
        }
        for (Launcher l : live(level, f)) {
            if (l.kind().samLike() || l.kind() == Launcher.Kind.RADAR || !l.ready()) {
                continue;
            }
            List<Vec3> pick = l.kind() == Launcher.Kind.ISKANDER || hot.size() > 0 && R.nextInt(3) > 0 ? hot : soft;
            if (pick.isEmpty()) {
                pick = hot.isEmpty() ? soft : hot;
            }
            for (int tries = 0; tries < 4 && !pick.isEmpty(); tries++) {
                Vec3 t = pick.get(R.nextInt(pick.size()));
                if (friendly(level, f, t)) {
                    continue;
                }
                BlockPos at = BlockPos.containing(t.x + R.nextGaussian() * 6, t.y, t.z + R.nextGaussian() * 6);
                if (l.order(null, at)) {
                    tell(level, f, Component.translatable("message.harddayz.bots_fire",
                            Component.translatable("entity.harddayz.kind." + l.kind().name().toLowerCase(java.util.Locale.ROOT)), at.getX(), at.getZ()));
                    break;
                }
            }
        }
    }

    /** Разведчик над фронтом: всё, что увидит, попадает в сеть стороны. */
    private static void recon(ServerLevel level, War.Faction f) {
        if (f.botBudget < 300 || War.prep()) {
            return;
        }
        int[] b = FrontWorld.baseOf(level.getServer(), f);
        if (b == null) {
            return;
        }
        // смотрим туда, где чужие или спорные точки ближе всего
        Frontline.Point best = null;
        double bd = 1.0E9;
        for (Frontline.Point p : War.points()) {
            double d = Math.hypot(p.x - b[0], p.z - b[2]);
            if (!f.id.equals(p.owner) && d < bd) {
                bd = d;
                best = p;
            }
        }
        Vec3 over = best != null ? new Vec3(best.x, best.y, best.z) : new Vec3(0, 72, 0);
        f.botBudget -= 300;
        BotCommander.launchLater(new Vec3(b[0], b[1] + 20, b[2]), over, f.botId(), Drone.Kind.WING_RECON);
    }

    private static long lastTell;

    /** Сообщить людям этой стороны (не чаще раза в 8 с). */
    private static void tell(ServerLevel level, War.Faction f, Component msg) {
        if (f.ai || level.getGameTime() - lastTell < 160) {
            return;
        }
        lastTell = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (f.has(p.getUUID())) {
                p.sendSystemMessage(msg.copy().withStyle(ChatFormatting.DARK_AQUA));
            }
        }
    }
}
