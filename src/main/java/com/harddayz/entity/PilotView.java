package com.harddayz.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Прогрузка мира вокруг дрона, которым управляет игрок. Сервер шлёт игроку чанки вокруг его тела, поэтому
 * на время пилотирования центр «видимой» области игрока переносится на дрон (как у ванильной камеры наблюдателя,
 * но тело остаётся на месте). В 1.20.1 это делает миксин {@code ChunkMapMixin}: для пилота он подставляет
 * секцию дрона вместо секции тела, а {@link #follow} каждый тик просит ChunkMap пересчитать область.
 */
public final class PilotView {
    private static final Map<UUID, ChunkPos> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> EYES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> DRONES = new ConcurrentHashMap<>();

    /** Откуда пилот «смотрит» (позиция дрона), или null — не пилотирует. */
    public static Vec3 viewPos(ServerPlayer p) {
        return EYES.get(p.getUUID());
    }

    /** Секция, от которой считать видимость чанков игрока, или null — от тела. */
    public static SectionPos viewSection(ServerPlayer p) {
        Vec3 eye = EYES.get(p.getUUID());
        return eye == null ? null : SectionPos.of(net.minecraft.core.BlockPos.containing(eye));
    }

    public static boolean isPiloted(ServerPlayer p, Entity e) {
        Integer id = DRONES.get(p.getUUID());
        return id != null && id == e.getId();
    }

    private PilotView() {
    }

    private static int radius(ServerPlayer p) {
        return Math.max(2, p.getServer().getPlayerList().getViewDistance());
    }

    /** Каждый тик, пока игрок ведёт дрон. */
    public static void follow(ServerPlayer p, Entity drone) {
        if (p.level() != drone.level()) {
            return;
        }
        ServerLevel level = (ServerLevel) drone.level();
        ChunkPos c = drone.chunkPosition();
        int r = radius(p);
        if (drone.tickCount % 20 == 0 || !c.equals(ACTIVE.get(p.getUUID()))) {
            com.harddayz.Compat.ticket(level, c, Math.min(r, 8));
        }
        ACTIVE.put(p.getUUID(), c);
        EYES.put(p.getUUID(), drone.position());
        DRONES.put(p.getUUID(), drone.getId());
        level.getChunkSource().move(p); // ChunkMap возьмёт секцию дрона (ChunkMapMixin)
    }

    /** Вернуть обзор телу игрока. */
    public static void restore(ServerPlayer p) {
        EYES.remove(p.getUUID());
        DRONES.remove(p.getUUID());
        if (ACTIVE.remove(p.getUUID()) != null && p.level() instanceof ServerLevel level) {
            level.getChunkSource().move(p);
        }
    }

    public static boolean active(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }
}
