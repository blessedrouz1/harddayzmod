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
    private @Nullable ChunkPos held;

    public FlightChunks(UUID owner) {
    }

    /**
     * Обновить загруженную область. Возвращает false, если чанк, куда объект влетит на этом тике,
     * ещё не готов — тогда объекту надо подождать тик, а не лететь в незагруженный мир.
     */
    public boolean update(ServerLevel level, Vec3 pos, Vec3 vel, @Nullable Vec3 target) {
        long t = level.getGameTime() + phase;
        // свой чанк держим всегда сами: чужой тикет (соседней ракеты залпа, уходящего игрока) может истечь в любой момент,
        // а остановленный объект уже никогда не попросит новый
        ChunkPos here = new ChunkPos(BlockPos.containing(pos));
        if (waited > 0 || !here.equals(held) || t % 10 == 0) {
            held = here;
            com.harddayz.Compat.ticket(level, here, 2);
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
        waited++; // в неживой чанк влетать нельзя — там объект замрёт навсегда
        return false;
    }

    private static final java.util.List<java.lang.ref.WeakReference<net.minecraft.world.entity.Entity>> NEW = new java.util.ArrayList<>();

    /** Только что запущенный объект: держим его чанк, пока он не сделает первый тик (дальше он держит сам). */
    public static void born(ServerLevel level, net.minecraft.world.entity.Entity e) {
        com.harddayz.Compat.ticket(level, new ChunkPos(e.blockPosition()), 2);
        NEW.add(new java.lang.ref.WeakReference<>(e));
    }

    /** Каждый тик сервера. */
    public static void tickBorn(ServerLevel level) {
        if (NEW.isEmpty() || level.getGameTime() % 10 != 0) {
            return;
        }
        NEW.removeIf(r -> {
            var e = r.get();
            if (e == null || e.isRemoved() || e.tickCount > 1) {
                return true;
            }
            com.harddayz.Compat.ticket(level, new ChunkPos(e.blockPosition()), 2);
            return false;
        });
    }

    /** Сдвиг по времени, чтобы ракеты одного залпа не обновлялись в один и тот же тик. */
    private final int phase = (int) (Math.random() * 20);

    /** Тикеты истекают сами. */
    public void release(ServerLevel level) {
    }
}
