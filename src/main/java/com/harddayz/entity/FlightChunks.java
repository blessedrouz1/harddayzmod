package com.harddayz.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Держит загруженными чанки вокруг летящей ракеты/дрона и на её пути, чтобы она долетала до цели
 * без игроков рядом. Использует ванильный тикет эндер-жемчуга: он делает чанки «живыми»
 * и сам истекает через 40 тиков, поэтому ничего не остаётся загруженным после взрыва или выхода из мира.
 */
public final class FlightChunks {
    private int waited;

    public FlightChunks(UUID owner) {
    }

    /**
     * Обновить загруженную область. Возвращает false, если чанк, куда объект влетит на этом тике,
     * ещё не готов — тогда объекту надо подождать тик, а не лететь в незагруженный мир.
     */
    public boolean update(ServerLevel level, Vec3 pos, Vec3 vel, @Nullable Vec3 target) {
        long t = level.getGameTime() + phase;
        // свой чанк — раз в 10 тиков (тикет живёт 40), и только если его не держат игроки
        BlockPos here = BlockPos.containing(pos);
        if (t % 10 == 0 && !level.isPositionEntityTicking(here)) {
            com.harddayz.Compat.ticket(level, new ChunkPos(here), 2);
        }
        // путь вперёд — редко и только незагруженные чанки, без «живых» соседей
        double speed = vel.length();
        if (t % 20 == 0 && speed > 1.0E-3) {
            Vec3 dir = vel.scale(1 / speed);
            double ahead = Math.min(96, speed * 30 + 16);
            for (double d = 16; d <= ahead; d += 16) {
                BlockPos p = BlockPos.containing(pos.add(dir.scale(d)));
                if (!level.isPositionEntityTicking(p)) {
                    com.harddayz.Compat.ticket(level, new ChunkPos(p), 1);
                }
            }
        }
        if (target != null && t % 100 == 0) {
            BlockPos tp = BlockPos.containing(target);
            if (!level.isPositionEntityTicking(tp)) {
                com.harddayz.Compat.ticket(level, new ChunkPos(tp), 1);
            }
        }
        BlockPos next = BlockPos.containing(pos.add(vel));
        if (level.isPositionEntityTicking(next)) {
            waited = 0;
            return true;
        }
        com.harddayz.Compat.ticket(level, new ChunkPos(next), 2);
        if (++waited > 40) {
            waited = 0;
            return true; // ждали достаточно — летим дальше
        }
        return false;
    }

    /** Сдвиг по времени, чтобы ракеты одного залпа не обновлялись в один и тот же тик. */
    private final int phase = (int) (Math.random() * 20);

    /** Тикеты истекают сами. */
    public void release(ServerLevel level) {
    }
}
