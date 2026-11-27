package com.harddayz.war;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.harddayz.Compat;
import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;
import com.harddayz.entity.Missile;
import com.harddayz.entity.Soldier;
import com.harddayz.entity.Threat;
import com.harddayz.entity.Vehicle;
import com.harddayz.registry.ModBlocks;
import com.harddayz.registry.ModEntities;
import com.harddayz.registry.ModSounds;
import com.harddayz.weapon.Blast;
import com.harddayz.world.FrontWorld;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Командир ботов. Отряды живут по всей карте: вдали от игроков это точка с числом бойцов (идёт, захватывает, воюет
 * с чужими отрядами по вероятности), рядом с игроком или его дроном отряд становится живыми солдатами.
 * В режиме против ботов командует ещё и ударами врага.
 */
public final class BotCommander {
    /** Отряд (хранится в War). */
    public static final class Squad {
        public int id;
        public String faction = "";
        public double x;
        public double z;
        public int tx;
        public int tz;
        /** Что держит или берёт: имя точки, "obj:x,y,z", "hq:фракция". */
        public @Nullable String target;
        public int men;
        /** Секунд у вражеского объекта без помех. */
        public int plant;
        transient List<Soldier> live = new ArrayList<>();

        public boolean isLive() {
            return live != null && !live.isEmpty();
        }
    }

    private record Strike(Vec3 from, Vec3 to, UUID side, Drone.Kind drone, int[] age) {
    }

    private static final double SPEED = 4.4; // блоков в секунду
    private static final int MAX_LIVE = 48;
    private static final Random R = new Random();
    private static final Map<String, Integer> NEXT = new HashMap<>();
    private static final List<Strike> STRIKES = new ArrayList<>();
    private static int seconds;
    private static int nextStrike;

    private BotCommander() {
    }

    public static @Nullable Squad squad(int id) {
        for (Squad s : War.squads()) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    /** Новый матч или конец старого. */
    public static void reset(@Nullable MinecraftServer server) {
        for (Squad s : War.squads()) {
            toPaper(s);
        }
        if (server != null && War.coop()) { // техника вражеских ботов уходит вместе с матчем
            War.Faction ai = War.enemyBots();
            if (ai != null) {
                for (Launcher l : server.overworld().getEntities(ModEntities.LAUNCHER.get(), l -> ai.botId().equals(l.owner()))) {
                    l.discard();
                }
            }
        }
        NEXT.clear();
        STRIKES.clear();
        seconds = 0;
        nextStrike = 0;
    }

    // ------------------------------------------------------------------ раз в секунду

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        seconds++;
        for (War.Faction f : War.factions()) {
            if (f.bots > 0 && !f.defeated && War.botAllowed(War.BOT_INFANTRY)) {
                reinforce(server, f);
            }
        }
        War.squads().removeIf(s -> s.men <= 0 && !s.isLive() || War.byId(s.faction) == null);
        int total = 0;
        for (Squad s : War.squads()) {
            reconcile(s);
            total += s.live.size();
        }
        List<Vec3> eyes = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) {
                eyes.add(p.position());
            }
        }
        for (Drone d : level.getEntities(ModEntities.DRONE.get(), d -> d.pilotId() >= 0)) {
            eyes.add(d.position());
        }
        for (Squad s : War.squads()) {
            orders(s);
            double near = near(eyes, s);
            boolean foe = enemyLive(s, 90);
            if (!s.isLive()) {
                walk(s);
                if ((near < 110 || foe) && total + s.men <= MAX_LIVE) {
                    total += materialize(level, s);
                }
            } else if (near > 170 && !foe) {
                toPaper(s);
            }
            sabotage(level, s);
        }
        paperFights();
        for (War.Faction f : War.factions()) {
            if (f.bots > 0 && !f.defeated) {
                BotArmy.tick(level, f, seconds);
            }
            if (seconds % 2 == 0) {
                lookout(level, f);
            }
        }
        War.Faction ai = War.enemyBots();
        if (ai != null && !ai.defeated) {
            enemyFire(level, ai);
        }
    }

    /** Каждый тик: отложенные пуски ждут, пока загрузится место старта. */
    public static void tickStrikes(ServerLevel level) {
        STRIKES.removeIf(s -> {
            BlockPos p = BlockPos.containing(s.from);
            if (s.age[0]++ % 10 == 0) {
                Compat.ticket(level, new ChunkPos(p), 3);
            }
            if (!level.isPositionEntityTicking(p)) {
                return s.age[0] > 400;
            }
            Vec3 dir = s.to.subtract(s.from).multiply(1, 0, 1).normalize();
            if (s.drone == Drone.Kind.WING_RECON) {
                Drone.launchShahed(level, s.from, new Vec3(0, 1, 0), s.to, s.side, s.drone);
            } else if (s.drone == null) {
                Missile.launch(level, Missile.Kind.BALLISTIC, s.from, new Vec3(0, 0.4, 0), s.to, null).owner(s.side).powerMul(0.7f);
            } else {
                Drone.launchShahed(level, s.from, dir.add(0, 0.35, 0).normalize(), s.to, s.side, s.drone);
                ModSounds.launch(level, s.from.x, s.from.y, s.from.z,
                        (s.drone == Drone.Kind.CRUISE ? ModSounds.LAUNCH_CRUISE : s.drone.jetDrone() ? ModSounds.LAUNCH_JETDRONE : ModSounds.LAUNCH_SHAHED).get(), 300, 1f);
            }
            return true;
        });
    }

    private static double near(List<Vec3> eyes, Squad s) {
        double best = 1.0E9;
        for (Vec3 e : eyes) {
            best = Math.min(best, Math.hypot(e.x - s.x, e.z - s.z));
        }
        return best;
    }

    private static boolean enemyLive(Squad s, double r) {
        for (Squad o : War.squads()) {
            if (o.isLive() && !o.faction.equals(s.faction) && Math.hypot(o.x - s.x, o.z - s.z) < r) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ пополнение и приказы

    /** Отряд врага видят свои: игрок, отряд или управляемый дрон поблизости. */
    public static boolean spotted(ServerLevel level, War.Faction by, Squad s) {
        for (Squad o : War.squads()) {
            if (o.men > 0 && o.faction.equals(by.id) && Math.hypot(o.x - s.x, o.z - s.z) < 60) {
                return true;
            }
        }
        for (ServerPlayer p : level.players()) {
            if (by.has(p.getUUID()) && Math.hypot(p.getX() - s.x, p.getZ() - s.z) < 55) {
                return true;
            }
        }
        return !level.getEntities(ModEntities.DRONE.get(), d -> d.pilotId() >= 0 && d.owner() != null && by.has(d.owner())
                && Math.hypot(d.getX() - s.x, d.getZ() - s.z) < 110).isEmpty();
    }

    public static int men(War.Faction f) {
        return menOf(f);
    }

    private static int menOf(War.Faction f) {
        int n = 0;
        for (Squad s : War.squads()) {
            if (s.faction.equals(f.id)) {
                n += s.men;
            }
        }
        return n;
    }

    private static @Nullable int[] base(MinecraftServer server, War.Faction f) {
        int[] b = FrontWorld.baseOf(server, f);
        return b != null ? b : f.hqs.isEmpty() ? null : f.hqs.get(0);
    }

    private static void reinforce(MinecraftServer server, War.Faction f) {
        int[] b = base(server, f);
        if (b == null || f.hadHq && f.hqs.isEmpty() && !War.siege() || seconds < NEXT.getOrDefault(f.id, 0)) {
            return;
        }
        int lack = f.bots - menOf(f);
        if (lack <= 0) {
            return;
        }
        boolean enemy = f == War.enemyBots();
        // первые отряды выходят сразу, потеряшки возвращаются волнами
        int wave = enemy ? 50 - War.difficulty() * 10 : 40;
        NEXT.put(f.id, seconds + (menOf(f) < f.bots / 2 && seconds < 120 ? 6 : wave));
        Squad s = new Squad();
        s.id = War.nextSquad();
        s.faction = f.id;
        s.men = Math.min(4, lack);
        s.x = b[0] + R.nextGaussian() * 8;
        s.z = b[2] + R.nextGaussian() * 8;
        s.tx = b[0];
        s.tz = b[2];
        War.squads().add(s);
    }

    private static boolean valid(Squad s) {
        if (s.target == null) {
            return false;
        }
        if (s.target.startsWith("obj:")) {
            return War.siege() && War.objects().stream().anyMatch(o -> s.target.equals(objKey(o)));
        }
        if (s.target.startsWith("hq:")) {
            War.Faction e = War.byId(s.target.substring(3));
            return e != null && !e.hqs.isEmpty() && !e.defeated && openPoint(s.faction) == null;
        }
        for (Frontline.Point p : War.points()) {
            if (p.name.equals(s.target)) {
                // взяли точку — половина отрядов идёт дальше
                return !s.faction.equals(p.owner) || guards(s.faction, p) <= 1 || openPoint(s.faction) == null;
            }
        }
        return false;
    }

    private static String objKey(int[] o) {
        return "obj:" + o[0] + "," + o[1] + "," + o[2];
    }

    private static int helpers(Frontline.Point p) {
        int n = 0;
        for (Squad o : War.squads()) {
            if (o.target != null && o.target.startsWith("help:" + p.name + ":")) {
                n++;
            }
        }
        return n;
    }

    private static int guards(String faction, Frontline.Point p) {
        int n = 0;
        for (Squad o : War.squads()) {
            if (o.faction.equals(faction) && p.name.equals(o.target)) {
                n++;
            }
        }
        return n;
    }

    private static @Nullable Frontline.Point openPoint(String faction) {
        for (Frontline.Point p : War.points()) {
            if (!faction.equals(p.owner)) {
                return p;
            }
        }
        return null;
    }

    /** Высоты: с них видно округу — враги рядом попадают в разведку владельца. */
    private static void lookout(ServerLevel level, War.Faction f) {
        String key = RadarNet.key(f.botId());
        long now = level.getGameTime();
        for (Frontline.Point p : War.points()) {
            if (!"HILL".equals(p.kind) || !f.id.equals(p.owner)) {
                continue;
            }
            for (Entity e : level.getEntities((Entity) null, new AABB(p.x, p.y, p.z, p.x, p.y, p.z).inflate(260, 60, 260),
                    e -> (e instanceof ServerPlayer || e instanceof Soldier || e instanceof Vehicle) && e.isAlive())) {
                UUID o = Soldier.sideOf(e);
                if (o != null && War.of(o) != null && !War.allied(f.botId(), o) && !(e instanceof ServerPlayer sp && sp.isSpectator())) {
                    RadarNet.spot(key, e, now);
                }
            }
        }
    }

    private static final Map<String, Long> LAST_CONTACT = new HashMap<>();

    /** Боец впервые заметил врага: людям своей стороны — короткий доклад. */
    public static void contact(ServerLevel level, Soldier by, Entity foe) {
        War.Faction f = War.of(by.side());
        if (f == null || f.ai) {
            return;
        }
        long now = level.getGameTime();
        if (now - LAST_CONTACT.getOrDefault(f.id, -1000L) < 300) {
            return;
        }
        LAST_CONTACT.put(f.id, now);
        String where = "";
        var g = com.harddayz.world.FrontWorld.generator(level.getServer());
        if (g != null) {
            where = g.map().region(foe.getX(), foe.getZ()).name();
        }
        Component what = Component.translatable(foe instanceof Vehicle ? "message.harddayz.contact_veh" : "message.harddayz.contact_inf");
        Component msg = Component.translatable("message.harddayz.contact", what, where, foe.getBlockX(), foe.getBlockZ()).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer p : level.players()) {
            if (f.has(p.getUUID())) {
                p.displayClientMessage(msg, true);
            }
        }
    }

    /** Запуск дрона с базы, когда место старта загрузится. */
    public static void launchLater(Vec3 from, Vec3 to, UUID side, Drone.Kind kind) {
        STRIKES.add(new Strike(from, to, side, kind, new int[] {0}));
    }

    private static boolean guarding(Squad s) {
        if (s.target == null) {
            return false;
        }
        for (Frontline.Point p : War.points()) {
            if (p.name.equals(s.target) && s.faction.equals(p.owner)) {
                return true;
            }
        }
        return false;
    }

    /** Свежая цель разведки рядом с отрядом. */
    private static @Nullable Vec3 prey(Squad s, double r) {
        War.Faction f = War.byId(s.faction);
        if (f == null) {
            return null;
        }
        Vec3 best = null;
        double bd = r;
        MinecraftServer server = War.server();
        long now = server == null ? 0 : server.overworld().getGameTime();
        for (RadarNet.Spot sp : RadarNet.ground(RadarNet.key(f.botId()), now)) {
            double d = Math.hypot(sp.pos.x - s.x, sp.pos.z - s.z);
            if (now - sp.seen < 400 && d < bd) {
                bd = d;
                best = sp.pos;
            }
        }
        return best;
    }

    private static void orders(Squad s) {
        // своя точка под захватом — ближние отряды идут на помощь
        if (s.target == null || !s.target.startsWith("help:")) {
            for (Frontline.Point p : War.points()) {
                if (s.faction.equals(p.owner) && p.capturing != null && !p.capturing.equals(s.faction) && p.progress > 0
                        && Math.hypot(p.x - s.x, p.z - s.z) < 450 && !guarding(s) && helpers(p) < 2) {
                    s.target = "help:" + p.name + ":" + seconds;
                    s.tx = p.x;
                    s.tz = p.z;
                    return;
                }
            }
        }
        // замечен враг — отряд идёт на него (охрана точки — только если враг совсем рядом)
        if ((s.target == null || !s.target.startsWith("hunt:") && !s.target.startsWith("help:")) && (seconds + s.id) % 4 == 0) {
            Vec3 e = prey(s, guarding(s) ? 90 : 240);
            if (e != null) {
                s.target = "hunt:" + seconds;
                s.tx = Mth.floor(e.x);
                s.tz = Mth.floor(e.z);
                return;
            }
        }
        if (s.target != null && (s.target.startsWith("hunt:") || s.target.startsWith("help:"))) {
            int at = Integer.parseInt(s.target.substring(s.target.lastIndexOf(':') + 1));
            if (seconds - at < (s.target.startsWith("hunt:") ? 50 : 90)) {
                return;
            }
            s.target = null;
        }
        if (valid(s)) {
            return;
        }
        s.target = null;
        s.plant = 0;
        if (War.siege()) {
            List<int[]> objs = War.objects();
            if (!objs.isEmpty()) {
                int[] o = objs.get(R.nextInt(objs.size()));
                s.target = objKey(o);
                s.tx = o[0];
                s.tz = o[2];
            }
            return;
        }
        // сначала прикрыть свои пустые точки, если отрядов хватает, иначе — ближайшая чужая
        Frontline.Point best = null;
        double bestK = 1.0E9;
        for (Frontline.Point p : War.points()) {
            boolean own = s.faction.equals(p.owner);
            int g = guards(s.faction, p);
            if (own && g > 0 || !own && g >= 2) {
                continue;
            }
            double k = Math.hypot(p.x - s.x, p.z - s.z) * (0.7 + R.nextDouble() * 0.6) * (own ? 1.2 : 1) * (g > 0 ? 1.4 : 1);
            if (k < bestK) {
                bestK = k;
                best = p;
            }
        }
        if (best != null) {
            s.target = best.name;
            s.tx = best.x;
            s.tz = best.z;
            return;
        }
        for (War.Faction e : War.factions()) { // точки все наши — на штаб врага
            if (!e.id.equals(s.faction) && !e.hqs.isEmpty() && !e.defeated) {
                s.target = "hq:" + e.id;
                s.tx = e.hqs.get(0)[0];
                s.tz = e.hqs.get(0)[2];
                return;
            }
        }
    }

    private static void walk(Squad s) {
        double dx = s.tx - s.x, dz = s.tz - s.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d > 5) {
            double k = Math.min(SPEED, d) / d;
            s.x += dx * k;
            s.z += dz * k;
        }
    }

    /** Чьи отряды стоят у точки. */
    public static Set<String> present(BlockPos pos, double r) {
        Set<String> out = new HashSet<>();
        for (Squad s : War.squads()) {
            if (s.men > 0 && Math.hypot(s.x - pos.getX(), s.z - pos.getZ()) < r) {
                out.add(s.faction);
            }
        }
        return out;
    }

    /** Отряд без помех у вражеского штаба или объекта города — закладывает заряд. */
    private static void sabotage(ServerLevel level, Squad s) {
        War.Faction f = War.byId(s.faction);
        if (f == null || s.target == null || Math.hypot(s.tx - s.x, s.tz - s.z) > 12) {
            return;
        }
        BlockPos at = null;
        if (s.target.startsWith("obj:") && f == War.attack()) {
            String[] p = s.target.substring(4).split(",");
            at = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } else if (s.target.startsWith("hq:")) {
            War.Faction e = War.byId(s.target.substring(3));
            if (e != null && !e.hqs.isEmpty()) {
                int[] h = e.hqs.get(0);
                at = new BlockPos(h[0], h[1], h[2]);
            }
        }
        if (at == null) {
            return;
        }
        boolean blocked = present(at, 30).size() > 1;
        for (ServerPlayer p : level.players()) {
            blocked |= !p.isSpectator() && p.isAlive() && at.distToCenterSqr(p.position()) < 30 * 30 && War.of(p.getUUID()) != null && War.of(p.getUUID()) != f;
        }
        if (blocked) {
            s.plant = 0;
            return;
        }
        if (++s.plant >= 45) {
            s.plant = 0;
            level.getChunk(at);
            Vec3 c = Vec3.atCenterOf(at);
            level.removeBlock(at, false);
            Blast.detonate(level, c, 4f, true, null);
        }
    }

    // ------------------------------------------------------------------ бой на бумаге

    private static void paperFights() {
        List<Squad> all = War.squads();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Squad a = all.get(i), b = all.get(j);
                if (a.isLive() || b.isLive() || a.men <= 0 || b.men <= 0 || a.faction.equals(b.faction) || Math.hypot(a.x - b.x, a.z - b.z) > 45) {
                    continue;
                }
                kill(b, a, 0.025 * a.men);
                kill(a, b, 0.025 * b.men);
            }
        }
    }

    private static void kill(Squad victim, Squad by, double chance) {
        if (victim.men > 0 && R.nextDouble() < chance) {
            victim.men--;
            War.Faction v = War.byId(victim.faction), k = War.byId(by.faction);
            if (v != null) {
                v.deaths++;
            }
            if (k != null) {
                k.kills++;
            }
        }
    }

    /** Взрыв рядом с отрядом, которого никто не видит. */
    public static void blast(Vec3 at, float power) {
        if (!War.active() || power < 1.5f) {
            return;
        }
        double r = power * 2.2;
        for (Squad s : War.squads()) {
            double d = Math.hypot(s.x - at.x, s.z - at.z);
            for (int i = s.isLive() ? 0 : s.men; i > 0; i--) {
                if (d < r && R.nextDouble() < 0.7 * (1 - d / r)) {
                    s.men--;
                }
            }
        }
    }

    // ------------------------------------------------------------------ живые солдаты

    private static void reconcile(Squad s) {
        if (s.live == null) {
            s.live = new ArrayList<>();
        }
        // исчез без смерти (выгрузился чанк) — боец просто вернулся «на бумагу»
        s.live.removeIf(Entity::isRemoved);
        s.live.removeIf(e -> !e.isAlive());
        if (!s.live.isEmpty()) {
            double x = 0, z = 0;
            for (Soldier e : s.live) {
                x += e.getX();
                z += e.getZ();
            }
            s.x = x / s.live.size();
            s.z = z / s.live.size();
        }
    }

    private static void toPaper(Squad s) {
        if (s.live != null) {
            for (Soldier e : s.live) {
                e.discard();
            }
            s.live.clear();
        }
    }

    private static int materialize(ServerLevel level, Squad s) {
        War.Faction f = War.byId(s.faction);
        if (f == null) {
            return 0;
        }
        int made = 0;
        for (int i = 0; i < s.men; i++) {
            BlockPos at = ground(level, s.x, s.z);
            if (at == null) {
                continue;
            }
            Soldier e = ModEntities.SOLDIER.get().create(level);
            if (e == null) {
                continue;
            }
            int role = i == 0 ? Soldier.RIFLE : (s.id + i) % 4 == 1 ? Soldier.MG : (s.id + i) % 4 == 2 ? Soldier.RPG : (s.id + i) % 9 == 3 ? Soldier.SNIPER : Soldier.RIFLE;
            e.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            e.setYRot((float) Math.toDegrees(Math.atan2(-(s.tx - s.x), s.tz - s.z)));
            e.yHeadRot = e.getYRot();
            e.setup(f.botId(), s.id, role);
            level.addFreshEntity(e);
            s.live.add(e);
            made++;
        }
        if (made > 0) {
            s.men = made; // кому не нашлось места, тот отстал
        }
        return made;
    }

    /** Земля (не крыша и не вода) рядом с точкой, в загруженном чанке. */
    private static @Nullable BlockPos ground(ServerLevel level, double x, double z) {
        for (int i = 0; i < 16; i++) {
            int bx = Mth.floor(x + R.nextGaussian() * (3 + i)), bz = Mth.floor(z + R.nextGaussian() * (3 + i));
            BlockPos p = new BlockPos(bx, 64, bz);
            if (!level.isPositionEntityTicking(p)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            p = p.atY(y);
            Integer want = FrontWorld.groundAt(level, bx, bz);
            if (want != null && Math.abs(y - want) > 3 || !level.getFluidState(p.below()).isEmpty() || !level.noCollision(new AABB(p).expandTowards(0, 1, 0))) {
                continue;
            }
            return p;
        }
        return null;
    }

    /** Солдат убит: потери отряда, счёт сторонам и награда. */
    public static void died(Soldier e, DamageSource src) {
        Squad s = squad(e.squad());
        if (s != null) {
            s.men = Math.max(0, s.men - 1);
        }
        War.Faction own = War.of(e.side());
        if (own != null) {
            own.deaths++;
        }
        Entity by = src.getEntity();
        UUID k = by instanceof Threat t ? t.owner() : by == null ? null : Soldier.sideOf(by);
        War.Faction kf = War.of(k);
        if (kf != null && kf != own) {
            kf.kills++;
            kf.budget += 25;
        }
    }

    // ------------------------------------------------------------------ удары вражеского командования

    private static void enemyFire(ServerLevel level, War.Faction ai) {
        if (seconds % 30 == 0) { // подвоз ракет к ПВО базы
            for (Launcher l : level.getEntities(ModEntities.LAUNCHER.get(), l -> ai.botId().equals(l.owner()) && !l.wrecked())) {
                l.resupply();
            }
        }
        int[] b = FrontWorld.baseOf(level.getServer(), ai);
        if (b == null) {
            return;
        }
        int dif = War.difficulty();
        if (nextStrike == 0) {
            nextStrike = seconds + 150;
        }
        if (seconds < nextStrike) {
            return;
        }
        nextStrike = seconds + 260 - dif * 50 + R.nextInt(90);
        Vec3 target = strikeTarget(level, ai);
        if (target == null) {
            return;
        }
        int n = dif + R.nextInt(dif + 1);
        for (int i = 0; i < n; i++) {
            boolean heavy = dif >= 2 && i == 0 && R.nextInt(5 - dif) == 0;
            Drone.Kind kind = heavy ? (dif == 3 && R.nextBoolean() ? null : Drone.Kind.CRUISE) : dif >= 2 && R.nextInt(3) == 0 ? Drone.Kind.SHAHED_238
                    : R.nextInt(4) == 0 ? Drone.Kind.GERBERA : Drone.Kind.SHAHED;
            if (kind == null && !War.botAllowed(War.BOT_ISKANDER) || kind == Drone.Kind.CRUISE && !War.botAllowed(War.BOT_CRUISE)) {
                kind = Drone.Kind.SHAHED;
            }
            if (kind != null && kind != Drone.Kind.CRUISE && !War.botAllowed(War.BOT_SHAHED)) {
                continue;
            }
            Vec3 to = target.add(R.nextGaussian() * 14, 0, R.nextGaussian() * 14);
            Vec3 from = new Vec3(b[0] + R.nextGaussian() * 12, b[1] + 22 + R.nextInt(6), b[2] + R.nextGaussian() * 12);
            STRIKES.add(new Strike(from, to, ai.botId(), kind, new int[] {-i * 30}));
        }
    }

    /** Куда бить: техника игроков, точка с игроками, штаб. */
    private static @Nullable Vec3 strikeTarget(ServerLevel level, War.Faction ai) {
        List<Vec3> t = new ArrayList<>();
        for (Launcher l : level.getEntities(ModEntities.LAUNCHER.get(), l -> !l.wrecked() && !l.hidden() && l.owner() != null && War.of(l.owner()) != null
                && !War.allied(ai.botId(), l.owner()))) {
            t.add(l.position());
        }
        for (Frontline.Point p : War.points()) {
            if (p.owner != null && !p.owner.equals(ai.id)) {
                t.add(new Vec3(p.x + R.nextGaussian() * 10, p.y, p.z + R.nextGaussian() * 10));
            }
        }
        for (War.Faction f : War.factions()) {
            if (f != ai && !f.hqs.isEmpty()) {
                int[] h = f.hqs.get(0);
                t.add(new Vec3(h[0] + R.nextGaussian() * 25, h[1], h[2] + R.nextGaussian() * 25));
            }
        }
        return t.isEmpty() ? null : t.get(R.nextInt(t.size()));
    }

    /** ПВО вражеской базы в режиме против ботов. */
    public static void fortify(ServerLevel level, War.Faction ai) {
        int[] b = FrontWorld.baseOf(level.getServer(), ai);
        if (b == null) {
            return;
        }
        Launcher.Kind[] kinds = War.difficulty() == 1 ? new Launcher.Kind[] {Launcher.Kind.SAM_POST, Launcher.Kind.SAM}
                : War.difficulty() == 2 ? new Launcher.Kind[] {Launcher.Kind.SAM, Launcher.Kind.NASAMS, Launcher.Kind.SAM_POST}
                : new Launcher.Kind[] {Launcher.Kind.SAM, Launcher.Kind.NASAMS, Launcher.Kind.PATRIOT, Launcher.Kind.SAM_POST};
        for (int i = 0; i < kinds.length; i++) {
            int x = b[0] - 20 + i * 14, z = b[2] - 10;
            level.getChunk(x >> 4, z >> 4);
            Launcher l = ModEntities.LAUNCHER.get().create(level);
            if (l == null) {
                continue;
            }
            l.setKind(kinds[i]);
            l.setPos(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
            l.setOwner(ai.botId());
            l.resupply();
            level.addFreshEntity(l);
        }
    }

    public static void announce(int count) {
        if (count > 0) {
            War.broadcast(Component.translatable("message.harddayz.bots_on", count).withStyle(ChatFormatting.GRAY));
        }
    }
}
