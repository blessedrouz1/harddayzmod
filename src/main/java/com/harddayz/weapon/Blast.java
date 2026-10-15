package com.harddayz.weapon;

import javax.annotation.Nullable;

import com.harddayz.Config;
import com.harddayz.network.Net;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Взрыв боеприпаса: разлёт настоящих блоков из воронки, сам взрыв, выжженная земля, ударная волна
 * (бьёт и отбрасывает дальше воронки) и эффекты для клиентов.
 */
public final class Blast {
    private Blast() {
    }

    /** Звук взрыва: тип оружия 1..7 (см. ModSounds.BLAST_TYPES), +16 — подрыв в воздухе. 0 — по силе. */
    private static int soundOf(@Nullable Entity source, boolean breakBlocks) {
        int air = breakBlocks ? 0 : 16;
        if (source instanceof com.harddayz.entity.Missile m) {
            if (m.kind() == com.harddayz.entity.Missile.Kind.SAM) {
                return 3 | 16;
            }
            if (m.look() == 7) {
                return 2 | air;
            }
            if (m.isThermo()) {
                return 4 | air;
            }
            return switch (m.kind()) {
                case GRAD -> 3;
                case BALLISTIC -> 5;
                default -> 1;
            } | air;
        }
        if (source instanceof com.harddayz.entity.Drone d) {
            return switch (d.kind()) {
                case CRUISE, BANDEROL -> 7;
                case SHAHED, SHAHED_131, SHAHED_238, GERAN5, GERBERA -> 6;
                case LANCET -> 3;
                default -> 1;
            } | air;
        }
        return 0;
    }

    public static void detonate(ServerLevel level, Vec3 at, float power, boolean breakBlocks, @Nullable Entity source) {
        float p = power * Config.blastScale();
        boolean blocks = breakBlocks && Config.blockDamage();
        RandomSource r = level.getRandom();
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
        double ground = Math.min(at.y, top);
        boolean nearGround = at.y - ground < p * 1.5;

        if (blocks && nearGround) {
            debris(level, at, p, r);
        }
        // свой взрыв без ванильного звука и частиц: всё это рисует ClientBlasts
        var near = new java.util.HashMap<ServerPlayer, Vec3>();
        for (ServerPlayer pl : level.players()) {
            if (pl.position().distanceTo(at) < p * 4 + 8) {
                near.put(pl, pl.getDeltaMovement());
            }
        }
        var ex = new net.minecraft.world.level.Explosion(level, source, null, null, at.x, at.y, at.z, Math.min(p, 16f), false,
                blocks ? net.minecraft.world.level.Explosion.BlockInteraction.DESTROY_WITH_DECAY : net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
        if (!net.minecraftforge.event.ForgeEventFactory.onExplosionStart(level, ex)) {
            ex.explode();
            ex.finalizeExplosion(false);
        }
        // игрока отбрасывает слабее, чем ванильный взрыв
        near.forEach((pl, before) -> {
            Vec3 now = pl.getDeltaMovement();
            pl.setDeltaMovement(before.add(now.subtract(before).scale(0.3)));
        });
        if (blocks && nearGround) {
            scorch(level, at, p, r);
        }

        // ударная волна за пределами воронки
        double radius = p * 3.5;
        for (Entity e : level.getEntities((Entity) null, new AABB(at, at).inflate(radius), e -> e instanceof LivingEntity || e.isPushable() || e instanceof com.harddayz.entity.Vehicle || e instanceof com.harddayz.entity.Drone)) {
            double d = e.position().distanceTo(at);
            if (d > radius || d < 0.01) {
                continue;
            }
            double f = 1 - d / radius;
            if (!(e instanceof com.harddayz.entity.Vehicle) && !(e instanceof com.harddayz.entity.Drone)) {
                double k = e instanceof Player ? 0.3 : 1;
                Vec3 push = e.position().subtract(at).normalize().scale(f * (0.6 + p * 0.12) * k).add(0, f * 0.5 * k, 0);
                e.push(push.x, push.y, push.z);
            }
            if (e instanceof ServerPlayer sp) {
                sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
            }
            if (e instanceof com.harddayz.entity.Vehicle || e instanceof com.harddayz.entity.Drone) {
                // техника: урон от ударной волны и осколков по всей зоне (сам взрыв бьёт отдельно)
                e.hurt(level.damageSources().explosion(source, null), (float) (f * p * 3.0));
                continue;
            }
            if (e instanceof LivingEntity && d > p * 1.8) {
                e.hurt(level.damageSources().explosion(source, null), (float) (f * p * 1.2));
            }
        }

        Net.BlastFx fx = new Net.BlastFx(at.x, at.y, at.z, p, soundOf(source, breakBlocks));
        for (ServerPlayer pl : level.players()) {
            if (pl.position().distanceTo(at) < 900) {
                Net.toPlayer(pl, fx);
            }
        }
    }

    /** Выбросить блоки из будущей воронки — они разлетаются и падают вокруг. */
    private static void debris(ServerLevel level, Vec3 at, float p, RandomSource r) {
        int n = (int) Math.min(48, p * 3);
        double rad = Math.max(1.5, p * 0.7);
        for (int i = 0; i < n; i++) {
            BlockPos pos = BlockPos.containing(at.x + r.nextGaussian() * rad * 0.6, at.y + r.nextGaussian() * rad * 0.4, at.z + r.nextGaussian() * rad * 0.6);
            BlockState st = level.getBlockState(pos);
            if (st.isAir() || st.getDestroySpeed(level, pos) < 0 || st.getDestroySpeed(level, pos) > 50 || !st.getFluidState().isEmpty()
                    || st.getBlock().getExplosionResistance() > p * 4
                    || level.getBlockEntity(pos) != null) {
                continue;
            }
            if (!level.getBlockState(pos.above()).isAir() && r.nextFloat() < 0.6f) {
                continue; // берём в основном поверхностные блоки
            }
            FallingBlockEntity fb = FallingBlockEntity.fall(level, pos, st);
            fb.dropItem = false;
            Vec3 out = Vec3.atCenterOf(pos).subtract(at).multiply(1, 0, 1);
            out = out.lengthSqr() < 0.01 ? new Vec3(r.nextGaussian(), 0, r.nextGaussian()) : out;
            out = out.normalize().scale(0.25 + r.nextDouble() * (0.3 + p * 0.04));
            fb.setDeltaMovement(out.x, 0.55 + r.nextDouble() * (0.4 + p * 0.05), out.z);
        }
    }

    /** Выжечь поверхность вокруг воронки и поджечь кое-где. */
    private static void scorch(ServerLevel level, Vec3 at, float p, RandomSource r) {
        int rad = (int) Math.ceil(p * 1.4);
        int cx = Mth.floor(at.x);
        int cz = Mth.floor(at.z);
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > rad || r.nextFloat() > 1.1 - d / rad) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz) - 1;
                BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                if (Math.abs(y - at.y) > p * 1.5) {
                    continue;
                }
                BlockState st = level.getBlockState(pos);
                BlockState burnt = null;
                if (st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.DIRT) || st.is(Blocks.PODZOL) || st.is(Blocks.MYCELIUM)) {
                    burnt = r.nextFloat() < 0.3f ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState();
                    if (r.nextFloat() < 0.25f) {
                        burnt = Blocks.BLACKSTONE.defaultBlockState();
                    }
                } else if (st.is(BlockTags.SAND)) {
                    burnt = r.nextFloat() < 0.3f ? Blocks.GLASS.defaultBlockState() : null;
                } else if (st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE)) {
                    burnt = r.nextFloat() < 0.4f ? Blocks.BLACKSTONE.defaultBlockState() : null;
                }
                if (burnt != null) {
                    level.setBlockAndUpdate(pos, burnt);
                }
                if (r.nextFloat() < 0.08f && level.getBlockState(pos.above()).isAir()) {
                    level.setBlockAndUpdate(pos.above(), Blocks.FIRE.defaultBlockState());
                }
            }
        }
    }
}
