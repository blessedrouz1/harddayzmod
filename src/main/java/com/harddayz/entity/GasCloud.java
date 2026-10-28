package com.harddayz.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Облако газа или дыма. Сносится ветром, газ тяжелее воздуха и стекает в окопы и подвалы.
 * Газ без противогаза: тошнота, замедление, отравление. За закрытой дверью бункера безопасно.
 */
public class GasCloud extends Entity {
    private static final EntityDataAccessor<Integer> TYPE = SynchedEntityData.defineId(GasCloud.class, EntityDataSerializers.INT);
    public static final int GAS = 0;
    public static final int SMOKE = 1;
    private static final DustParticleOptions GAS_DUST = new DustParticleOptions(new org.joml.Vector3f(0.62f, 0.68f, 0.22f), 3.5f);
    private int life;

    public GasCloud(EntityType<? extends GasCloud> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 at, int type) {
        GasCloud c = new GasCloud(com.harddayz.registry.ModEntities.GAS_CLOUD.get(), level);
        c.entityData.set(TYPE, type);
        c.setPos(at.x, at.y + 0.5, at.z);
        level.addFreshEntity(c);
    }

    public int type() {
        return entityData.get(TYPE);
    }

    private int maxLife() {
        return type() == GAS ? 900 : 700;
    }

    public double radius() {
        double grow = Math.min(1, life / 160.0);
        double fade = life > maxLife() - 200 ? (maxLife() - life) / 200.0 : 1;
        return (type() == GAS ? 3 + grow * 5 : 4 + grow * 6) * (0.5 + 0.5 * fade);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (++life > maxLife()) {
            discard();
            return;
        }
        Vec3 w = com.harddayz.war.Wind.vec(level.getGameTime()).scale(type() == GAS ? 0.025 : 0.04);
        double dy = 0;
        BlockPos below = BlockPos.containing(getX(), getY() - 0.6, getZ());
        if (level.getBlockState(BlockPos.containing(position())).isSolidRender(level, blockPosition())) {
            dy = 0.15; // застряло в земле — выталкиваем
        } else if (type() == GAS && !level.getBlockState(below).isSolidRender(level, below)) {
            dy = -0.06; // газ тяжелее воздуха: стекает вниз
        } else if (type() == SMOKE && life % 4 == 0) {
            dy = 0.01;
        }
        Vec3 next = position().add(w.x, dy, w.z);
        if (level.getBlockState(BlockPos.containing(next)).isSolidRender(level, BlockPos.containing(next))) {
            next = new Vec3(getX(), getY() + 0.05, getZ()); // упёрлось в стену — переваливает через неё
        }
        setPos(next);
        double r = radius();
        if (life % 3 == 0) {
            int n = (int) (r * 2.5);
            if (type() == GAS) {
                com.harddayz.Compat.particles(level, GAS_DUST, getX(), getY() + 0.6, getZ(), n, r * 0.45, 0.7, r * 0.45, 0.0);
            } else {
                com.harddayz.Compat.particles(level, com.harddayz.registry.ModParticles.SMOKE_HUGE.get(), getX(), getY() + 1.5, getZ(), n / 2, r * 0.4, 1.2, r * 0.4, 0.005);
                com.harddayz.Compat.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1, getZ(), n / 3, r * 0.4, 0.8, r * 0.4, 0.005);
            }
        }
        if (type() == GAS && life % 20 == 0) {
            poison(level, r);
        }
    }

    private void poison(ServerLevel level, double r) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(r, 3, r))) {
            if (e.distanceToSqr(getX(), e.getY(), getZ()) > r * r || e.getY() > getY() + 3 || e.getY() < getY() - 3) {
                continue;
            }
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
                continue;
            }
            if (masked(e) || sealed(level, e)) {
                continue;
            }
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            e.hurt(level.damageSources().magic(), 1f);
        }
    }

    public static boolean masked(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.HEAD).is(com.harddayz.registry.ModItems.GAS_MASK.get());
    }

    /** Между облаком и целью глухая стена — газ не дошёл. */
    private boolean sealed(ServerLevel level, LivingEntity e) {
        Vec3 from = position().add(0, 0.5, 0);
        Vec3 to = e.getEyePosition();
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(to) > 1;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TYPE, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TYPE, tag.getInt("type"));
        life = tag.getInt("life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("type", type());
        tag.putInt("life", life);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
