package com.harddayz.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
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
        var cache = level.getChunkSource();
        cache.addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4), 2);
        // путь вперёд: на 2 секунды полёта
        double speed = vel.length();
        if (speed > 1.0E-3) {
            Vec3 dir = vel.scale(1 / speed);
            double ahead = Math.min(160, speed * 40 + 16);
            for (double d = 8; d <= ahead; d += 8) {
                Vec3 p = pos.add(dir.scale(d));
                cache.addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4), 2);
            }
        }
        if (target != null && level.getGameTime() % 20 == 0) {
            cache.addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(Mth.floor(target.x) >> 4, Mth.floor(target.z) >> 4), 1);
        }
        BlockPos next = BlockPos.containing(pos.add(vel));
        int nx = next.getX() >> 4;
        int nz = next.getZ() >> 4;
        if (!level.hasChunk(nx, nz)) {
            level.getChunk(nx, nz); // загрузить сразу
        }
        cache.addTicketWithRadius(TicketType.ENDER_PEARL, new ChunkPos(nx, nz), 2);
        if (level.isPositionEntityTicking(next) || ++waited > 40) {
            waited = 0;
            return true; // готово или ждали достаточно — летим дальше
        }
        return false;
    }

    /** Тикеты истекают сами. */
    public void release(ServerLevel level) {
    }
}
