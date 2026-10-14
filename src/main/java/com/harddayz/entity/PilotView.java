package com.harddayz.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Прогрузка мира вокруг дрона, которым управляет игрок. Сервер шлёт игроку чанки вокруг его тела, поэтому
 * на время пилотирования центр «видимой» области игрока переносится на дрон (как у ванильной камеры наблюдателя,
 * но тело остаётся на месте), а сами чанки вокруг дрона держатся тикетом.
 */
public final class PilotView {
    private static final Map<UUID, ChunkPos> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, net.minecraft.world.phys.Vec3> EYES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> DRONES = new ConcurrentHashMap<>();

    /** Откуда пилот «смотрит» (позиция дрона), или null — не пилотирует. */
    public static net.minecraft.world.phys.Vec3 viewPos(ServerPlayer p) {
        return EYES.get(p.getUUID());
    }

    public static boolean isPiloted(ServerPlayer p, Entity e) {
        Integer id = DRONES.get(p.getUUID());
        return id != null && id == e.getId();
    }

    private PilotView() {
    }

    private static int radius(ServerPlayer p) {
        return Math.max(2, p.level().getServer().getPlayerList().getViewDistance());
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
            level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, c, Math.min(r, 8));
        }
        ACTIVE.put(p.getUUID(), c);
        EYES.put(p.getUUID(), drone.position());
        DRONES.put(p.getUUID(), drone.getId());
        apply(p, ChunkTrackingView.of(c, r));
    }

    /** Вернуть обзор телу игрока. */
    public static void restore(ServerPlayer p) {
        EYES.remove(p.getUUID());
        DRONES.remove(p.getUUID());
        if (ACTIVE.remove(p.getUUID()) != null) {
            apply(p, ChunkTrackingView.of(p.chunkPosition(), radius(p)));
        }
    }

    public static boolean active(ServerPlayer p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    private static void apply(ServerPlayer player, ChunkTrackingView next) {
        ChunkTrackingView prev = player.getChunkTrackingView();
        if (prev instanceof ChunkTrackingView.Positioned a && next instanceof ChunkTrackingView.Positioned b && a.center().equals(b.center())
                && a.viewDistance() == b.viewDistance()) {
            return;
        }
        ServerLevel level = player.level();
        if (next instanceof ChunkTrackingView.Positioned to
                && !(prev instanceof ChunkTrackingView.Positioned from && from.center().equals(to.center()))) {
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(to.center().x(), to.center().z()));
        }
        ChunkTrackingView.difference(prev, next, pos -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) {
                player.connection.chunkSender.markChunkPendingToSend(chunk);
            }
        }, pos -> player.connection.chunkSender.dropChunk(player, pos));
        player.setChunkTrackingView(next);
    }
}
