package com.harddayz.war;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import com.harddayz.entity.Drone;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Пули TaCZ ищут цели только среди живых существ, наши дроны для них прозрачны. Здесь следим за каждой пулей
 * по её пути за тик и, если путь прошёл через дрон, наносим урон от стрелка.
 */
public final class GunHits {
    private static final ResourceLocation BULLET = new ResourceLocation("tacz", "bullet");
    private static final List<Track> FLYING = new ArrayList<>();

    private static final class Track {
        final WeakReference<Entity> bullet;
        Vec3 last;
        Vec3 vel;

        Track(Entity e) {
            bullet = new WeakReference<>(e);
            last = e.position();
            vel = e.getDeltaMovement();
        }
    }

    private GunHits() {
    }

    public static void init() {
        var bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
        bus.addListener((net.minecraftforge.event.entity.EntityJoinLevelEvent e) -> {
            if (!e.getLevel().isClientSide() && BULLET.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType()))) {
                FLYING.add(new Track(e.getEntity()));
            }
        });
        bus.addListener((net.minecraftforge.event.TickEvent.LevelTickEvent e) -> {
            if (e.phase == net.minecraftforge.event.TickEvent.Phase.END && e.level instanceof ServerLevel level) {
                tick(level);
            }
        });
        bus.addListener((net.minecraftforge.event.server.ServerStoppedEvent e) -> FLYING.clear());
    }

    private static void tick(ServerLevel level) {
        if (FLYING.isEmpty()) {
            return;
        }
        FLYING.removeIf(t -> {
            Entity b = t.bullet.get();
            if (b == null || b.level() != level) {
                return true;
            }
            // пуля исчезла (ударилась в блок) — её последний отрезок всё равно проверяем
            Vec3 now = b.isRemoved() ? t.last.add(t.vel) : b.position();
            Drone hit = null;
            double best = Double.MAX_VALUE;
            for (Drone d : level.getEntitiesOfClass(Drone.class, new AABB(t.last, now).inflate(2))) {
                double pad = d.kind().shahedLike() || d.kind().jet() ? 0.9 : 0.5;
                var clip = d.getBoundingBox().inflate(pad).clip(t.last, now);
                if (clip.isPresent() && clip.get().distanceToSqr(t.last) < best) {
                    best = clip.get().distanceToSqr(t.last);
                    hit = d;
                }
            }
            if (hit != null) {
                Entity owner = b instanceof Projectile p ? p.getOwner() : null;
                hit.hurt(owner instanceof Player pl ? level.damageSources().playerAttack(pl) : level.damageSources().generic(), 3.5f);
                b.discard();
                return true;
            }
            t.vel = b.getDeltaMovement();
            t.last = now;
            return b.isRemoved() || b.tickCount > 200;
        });
    }
}
