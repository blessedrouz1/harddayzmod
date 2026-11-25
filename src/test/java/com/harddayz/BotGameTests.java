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
            h.assertTrue(!s1.isAlive() && !mate.isAlive() || !foe.isAlive(), "someone must win");
            h.assertTrue(a.deaths + b.deaths > 0, "deaths counted");
            War.end();
        });
    }
}
