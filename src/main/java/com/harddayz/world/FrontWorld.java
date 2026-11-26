package com.harddayz.world;

import javax.annotation.Nullable;

import com.harddayz.registry.ModBlocks;
import com.harddayz.war.Frontline;
import com.harddayz.war.War;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;

/** Фронтовая карта в матче: граница мира, штабы и склады на базах, точки, объекты города, появление на своей базе. */
public final class FrontWorld {
    private FrontWorld() {
    }

    public static @Nullable FrontGenerator generator(@Nullable MinecraftServer server) {
        return server != null && server.overworld().getChunkSource().getGenerator() instanceof FrontGenerator g ? g : null;
    }

    /** Первый запуск мира: граница, никаких мобов, появление у западной базы. */
    public static void started(MinecraftServer server) {
        FrontGenerator g = generator(server);
        if (g == null || server.overworld().getWorldBorder().getSize() < 1.0E7) {
            return;
        }
        ServerLevel level = server.overworld();
        level.getWorldBorder().setCenter(0, 0);
        level.getWorldBorder().setSize(FrontMap.HALF * 2);
        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(2, server);
        level.setDefaultSpawnPos(pos(g.buildings().spawn(g.map().west)), 90);
    }

    private static BlockPos pos(int[] p) {
        return new BlockPos(p[0], p[1], p[2]);
    }

    /** База фракции: 0 — запад, 1 — восток, остальным баз нет. */
    private static @Nullable FrontMap.Site base(FrontGenerator g, War.Faction f) {
        int i = War.factions().indexOf(f);
        // в осаде атакуют с запада, защита сидит в городе
        return War.siege() ? (i == 0 ? g.map().west : g.map().city) : i == 0 ? g.map().west : i == 1 ? g.map().east : null;
    }

    /** Центр базы стороны на фронтовой карте: x, y, z. */
    public static @Nullable int[] baseOf(MinecraftServer server, War.Faction f) {
        FrontGenerator g = generator(server);
        FrontMap.Site s = g == null ? null : base(g, f);
        return s == null ? null : new int[] {s.x(), s.y() + 1, s.z()};
    }

    /** Высота земли по плану карты (чтобы отличить землю от крыши), null — мир обычный. */
    public static @Nullable Integer groundAt(ServerLevel level, int x, int z) {
        if (!(level.getChunkSource().getGenerator() instanceof FrontGenerator g)) {
            return null;
        }
        FrontMap m = g.map();
        return (int) Math.round(m.height(x + 0.5, z + 0.5, m.road(x + 0.5, z + 0.5))) + 1;
    }

    private static void put(ServerLevel level, BlockPos p, Block b) {
        level.setBlockAndUpdate(p, b.defaultBlockState());
        for (int i = 1; i <= 2; i++) { // чтобы было видно и можно подойти
            if (!level.getBlockState(p.above(i)).isAir()) {
                level.removeBlock(p.above(i), false);
            }
        }
    }

    /** Новый матч на фронтовой карте. Возвращает false на обычном мире. */
    public static boolean setup(MinecraftServer server) {
        FrontGenerator g = generator(server);
        if (g == null) {
            return false;
        }
        ServerLevel level = server.overworld();
        FrontMap m = g.map();
        FrontBuildings b = g.buildings();
        if (War.siege()) {
            War.setCity(new BlockPos(m.city.x(), m.city.y(), m.city.z()), 300);
            for (int[] o : b.objects) {
                put(level, pos(o), ModBlocks.CITY_OBJECT.get());
                War.addMapObject(pos(o));
            }
        } else {
            for (FrontMap.Site s : m.sites) {
                if (s.type() == FrontMap.Type.BASE || s.type() == FrontMap.Type.VILLAGE) {
                    continue;
                }
                BlockPos at = switch (s.type()) {
                    case CITY -> pos(b.plaza);
                    case AIRFIELD -> new BlockPos(s.x(), s.y() + 1, s.z());
                    case TOWER -> new BlockPos(s.x(), s.y() + 1, s.z());
                    case HILL -> new BlockPos(s.x() - 7, s.y() + 1, s.z() + 7);
                    default -> new BlockPos(s.x(), s.y() + 1, s.z() + (s.type() == FrontMap.Type.POWER ? -2 : 0));
                };
                put(level, at, ModBlocks.FRONTLINE.get());
                Frontline.add(at, s.name(), s.type().name());
            }
        }
        for (War.Faction f : War.factions()) {
            FrontMap.Site s = base(g, f);
            if (s == null) {
                continue;
            }
            boolean city = s.type() == FrontMap.Type.CITY;
            BlockPos hq = city ? pos(b.hall) : pos(b.hq(s));
            BlockPos wh = city ? hq.offset(3, 0, 0) : pos(b.warehouse(s));
            put(level, hq, ModBlocks.HQ.get());
            War.addHq(f, hq);
            put(level, wh, ModBlocks.WAREHOUSE.get());
            f.warehouses.add(new int[] {wh.getX(), wh.getY(), wh.getZ()});
        }
        War.save();
        return true;
    }

    /** Игрок вступил во фракцию: появиться на своей базе и возрождаться там же. */
    public static void joined(ServerPlayer p, War.Faction f) {
        FrontGenerator g = generator(p.getServer());
        FrontMap.Site s = g == null ? null : base(g, f);
        if (s == null) {
            return;
        }
        ServerLevel level = p.getServer().overworld();
        BlockPos at = s.type() == FrontMap.Type.CITY ? pos(g.buildings().plaza) : pos(g.buildings().spawn(s));
        int top = level.getChunk(at).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at.getX() & 15, at.getZ() & 15) + 1;
        at = at.atY(top);
        p.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, s.x() <= 0 ? -90 : 90, 0);
        p.setRespawnPosition(level.dimension(), at, 0, true, false);
    }
}
