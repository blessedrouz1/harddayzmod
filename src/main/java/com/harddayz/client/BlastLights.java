package com.harddayz.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Свет от взрыва: на доли секунды ставит невидимые блоки света, только у этого клиента, сервер о них не знает.
 * Яркость падает ступенями (каждая ступень пересчитывает освещение), потом на место возвращается воздух.
 */
final class BlastLights {
    private static final int MAX = 60;
    private static final Map<BlockPos, Lamp> LAMPS = new HashMap<>();
    private static ClientLevel world;

    private BlastLights() {
    }

    private static final class Lamp {
        int age;
        int life;
        int level;
    }

    static void add(ClientLevel level, double x, double y, double z, double ground, float p) {
        if (!com.harddayz.ClientConfig.light() || p < 0.8f) {
            return;
        }
        int life = (int) Mth.clamp(6 + p * 1.1f, 6, 22);
        lamp(level, BlockPos.containing(x, Math.max(y, ground) + 1.5, z), life);
        if (p >= 4) {
            ring(level, x, z, 9, p >= 8 ? 8 : 5, life);
        }
        if (p >= 10) {
            lamp(level, BlockPos.containing(x, Math.max(y, ground) + 12, z), life);
            ring(level, x, z, 20, 10, life);
        }
    }

    private static void ring(ClientLevel level, double x, double z, double r, int n, int life) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int bx = Mth.floor(x + Math.cos(a) * r), bz = Mth.floor(z + Math.sin(a) * r);
            if (ClientBlasts.loaded(level, bx, bz)) {
                lamp(level, new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + 2, bz), life);
            }
        }
    }

    private static void lamp(ClientLevel level, BlockPos at, int life) {
        for (int i = 0; i < 4; i++) {
            BlockPos p = at.above(i).immutable();
            Lamp old = LAMPS.get(p);
            if (old != null) {
                old.age = 0;
                old.life = Math.max(old.life, life);
                return;
            }
            // своего света в этом месте ещё нет: ставим только вместо воздуха (чужой блок света — осветительной мины — не трогаем)
            if (LAMPS.size() < MAX && !level.isOutsideBuildHeight(p) && level.getBlockState(p).isAir()) {
                Lamp l = new Lamp();
                l.life = life;
                LAMPS.put(p, l);
                return;
            }
        }
    }

    static void tick(ClientLevel level) {
        if (world != level) {
            LAMPS.clear(); // другой мир: старые блоки ушли вместе с чанками
            world = level;
        }
        if (level == null) {
            return;
        }
        for (Iterator<Map.Entry<BlockPos, Lamp>> it = LAMPS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Lamp l = e.getValue();
            float k = l.age++ / (float) l.life;
            int want = k >= 1 ? 0 : k < 0.3f ? 15 : k < 0.55f ? 11 : k < 0.8f ? 7 : 3;
            if (want != l.level) {
                BlockState now = level.getBlockState(e.getKey());
                boolean mine = l.level == 0 ? now.isAir() : now.is(Blocks.LIGHT);
                if (!mine) {
                    it.remove(); // сервер уже поставил сюда что-то своё
                    continue;
                }
                level.setBlock(e.getKey(), want == 0 ? Blocks.AIR.defaultBlockState()
                        : Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, want), 18);
                l.level = want;
            }
            if (want == 0) {
                it.remove();
            }
        }
    }
}
