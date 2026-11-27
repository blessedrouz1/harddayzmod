package com.harddayz;

import java.util.UUID;

import com.harddayz.entity.Soldier;
import com.harddayz.registry.ModEntities;
import com.harddayz.war.BotCommander;
import com.harddayz.war.War;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("harddayz")
@PrefixGameTestTemplate(false)
public class BotGameTests {
    private static Soldier soldier(GameTestHelper h, War.Faction f, int x) {
        BlockPos p = h.absolutePos(new BlockPos(x, 2, 1));
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                h.getLevel().setBlockAndUpdate(p.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < 4; dy++) {
                    h.getLevel().setBlockAndUpdate(p.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        Soldier s = ModEntities.SOLDIER.get().create(h.getLevel());
        s.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        s.setup(f.botId(), -1, Soldier.RIFLE);
        h.getLevel().addFreshEntity(s);
        return s;
    }

    /** Боты отдают приказ РСЗО без игрока; ракеты уходят вверх, а не по прямой. */
    @GameTest(template = "empty", batch = "bots2", timeoutTicks = 600)
    public static void botsOrderLaunchers(GameTestHelper h) {
        BlockPos p = h.absolutePos(new BlockPos(1, 2, 1));
        com.harddayz.entity.Launcher l = ModEntities.LAUNCHER.get().create(h.getLevel());
        l.setKind(com.harddayz.entity.Launcher.Kind.GRAD);
        l.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        l.resupply();
        h.getLevel().addFreshEntity(l);
        h.assertTrue(l.ready(), "fresh launcher ready");
        h.assertTrue(l.order(null, p.offset(0, 0, 320)), "order without player");
        h.assertTrue(!l.order(null, p.offset(0, 0, 330)), "busy launcher refuses");
        double[] maxPitch = {0};
        h.onEachTick(() -> {
            for (var m : h.getLevel().getEntitiesOfClass(com.harddayz.entity.Missile.class, new net.minecraft.world.phys.AABB(p).inflate(40))) {
                var v = m.getDeltaMovement();
                maxPitch[0] = Math.max(maxPitch[0], Math.toDegrees(Math.atan2(v.y, Math.hypot(v.x, v.z))));
            }
        });
        h.succeedWhen(() -> h.assertTrue(maxPitch[0] > 40, "rocket must leave steep, got " + maxPitch[0]));
    }

    /** Удары режима выживания приходят издалека и падают у цели (у каждого оружия своя площадка: воронка соседа не мешает). */
    @GameTest(template = "empty", batch = "surv", timeoutTicks = 900)
    public static void survivalShotsLand(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        // ПВО из других тестов сбило бы наши ракеты
        for (var e : level.getAllEntities()) {
            if (e instanceof com.harddayz.entity.Launcher) {
                e.discard();
            }
        }
        var m = com.harddayz.war.Survival.class.getDeclaredMethod("shoot", net.minecraft.server.level.ServerLevel.class, net.minecraft.world.phys.Vec3.class, int.class, UUID.class);
        m.setAccessible(true);
        UUID side = UUID.randomUUID();
        int[] weapons = {0, 1, 6};
        java.util.Map<Long, double[]> hits = new java.util.HashMap<>();
        net.minecraft.world.phys.Vec3[] targets = new net.minecraft.world.phys.Vec3[weapons.length];
        for (int i = 0; i < weapons.length; i++) {
            // площадки в открытом небе: рельеф тестового мира не мешает
            BlockPos c = h.absolutePos(new BlockPos(1, 1, 1)).offset(i * 120, 0, 0).atY(200);
            for (int dx = -30; dx <= 30; dx++) {
                for (int dz = -30; dz <= 30; dz++) {
                    level.setBlockAndUpdate(c.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
                }
            }
            targets[i] = net.minecraft.world.phys.Vec3.atBottomCenterOf(c.above());
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.level.ExplosionEvent.Start e) -> {
            if (e.getExplosion().getDirectSourceEntity() instanceof com.harddayz.entity.Missile mm && mm.owner() != null
                    && mm.owner().getMostSignificantBits() == side.getMostSignificantBits()) {
                var at = e.getExplosion().getPosition();
                hits.putIfAbsent(mm.owner().getLeastSignificantBits(), new double[] {at.x, at.y, at.z});
            }
        });
        for (int i = 0; i < weapons.length; i++) {
            m.invoke(null, level, targets[i], weapons[i], new UUID(side.getMostSignificantBits(), i));
        }
        h.succeedWhen(() -> {
            h.assertTrue(hits.size() == weapons.length, "shots landed: " + hits.size());
            for (var e : hits.entrySet()) {
                var t = targets[e.getKey().intValue()];
                double d = Math.hypot(e.getValue()[0] - t.x, e.getValue()[2] - t.z);
                h.assertTrue(d < 12 && Math.abs(e.getValue()[1] - t.y) < 4, "weapon " + weapons[e.getKey().intValue()] + " missed by " + (int) d);
            }
        });
    }

    /** Солдаты разных сторон находят и убивают друг друга, своих не трогают; отряды на бумаге берут точки. */
    @GameTest(template = "empty", batch = "bots", timeoutTicks = 1200)
    public static void soldiersFight(GameTestHelper h) {
        War.create(UUID.randomUUID(), 2, 1000, 0);
        War.Faction a = War.factions().get(0), b = War.factions().get(1);
        h.assertTrue(War.allied(a.botId(), a.botId()) && !War.allied(a.botId(), b.botId()), "bot sides");
        Soldier s1 = soldier(h, a, 0), mate = soldier(h, a, 3), foe = soldier(h, b, 14);
        s1.setYRot(-90);
        h.assertTrue(!s1.hurt(h.getLevel().damageSources().mobAttack(mate), 5), "friendly bullet must not hurt");
        // отряд на бумаге доходит до точки
        BotCommander.Squad q = new BotCommander.Squad();
        q.id = War.nextSquad();
        q.faction = a.id;
        q.men = 4;
        q.x = 5000;
        q.z = 5000;
        War.squads().add(q);
        h.assertTrue(BotCommander.present(new BlockPos(5000, 70, 5004), 17).contains(a.id), "squad presence");
        BotCommander.blast(new net.minecraft.world.phys.Vec3(5000, 70, 5000), 16);
        h.assertTrue(q.men < 4, "blast must hit paper squad");
        h.succeedWhen(() -> {
            // бой случился: обе стороны ранены (раненый по задумке отходит в укрытие, поэтому до смерти не ждём)
            h.assertTrue(foe.getHealth() < foe.getMaxHealth() && (s1.getHealth() < s1.getMaxHealth() || mate.getHealth() < mate.getMaxHealth()),
                    "both sides must take hits: " + s1.getHealth() + " " + mate.getHealth() + " " + foe.getHealth());
            h.assertTrue(s1.isAlive() && mate.isAlive() && foe.isAlive() || a.deaths + b.deaths > 0, "deaths counted");
            War.end();
        });
    }
}
