package com.harddayz.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.harddayz.registry.ModItems;
import com.harddayz.war.War;
import com.harddayz.weapon.Blast;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/**
 * Мина. Противопехотная срабатывает на людей и мобов, противотанковая — только на технику.
 * Взводится через 3 секунды после установки, свои (та же фракция) не подрывают. Shift+удар своим — снять.
 */
public class Mine extends Entity {
    public enum Kind {
        AP(3.2f, 0.7), AT(7.5f, 1.6);

        public final float power;
        public final double trigger;

        Kind(float power, double trigger) {
            this.power = power;
            this.trigger = trigger;
        }
    }

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Mine.class, EntityDataSerializers.INT);
    private @Nullable UUID owner;
    private int armed;

    public Mine(EntityType<? extends Mine> type, Level level) {
        super(type, level);
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(entityData.get(KIND), Kind.values().length)];
    }

    public void setup(Kind k, @Nullable UUID owner) {
        entityData.set(KIND, k.ordinal());
        this.owner = owner;
    }

    public boolean armed() {
        return armed >= 60;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (!level.getBlockState(blockPosition().below()).isSolid() && !level.getBlockState(blockPosition()).isSolid()) {
            setPos(getX(), getY() - 0.1, getZ()); // упасть на землю
        }
        if (armed < 60) {
            if (++armed == 60) {
                level.playSound(null, blockPosition(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.BLOCKS, 0.5f, 1.4f);
            }
            return;
        }
        if (tickCount % 2 != 0) {
            return;
        }
        double r = kind().trigger;
        AABB box = getBoundingBox().inflate(r, 0.6, r);
        for (Entity e : level.getEntities(this, box)) {
            boolean target = kind() == Kind.AT ? e instanceof Vehicle : e instanceof LivingEntity && !(e instanceof Player p && p.isSpectator());
            if (!target) {
                continue;
            }
            detonate(level);
            return;
        }
    }

    private void detonate(ServerLevel level) {
        discard();
        Blast.detonate(level, position().add(0, 0.2, 0), kind().power, true, this);
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (!(level() instanceof ServerLevel level)) {
            return true;
        }
        if (source.getEntity() instanceof Player p && source.getDirectEntity() == p && (p.isShiftKeyDown() || p.isCreative())
                && (owner == null || War.allied(owner, p.getUUID()) || p.isCreative())) {
            spawnAtLocation(new ItemStack(kind() == Kind.AT ? ModItems.MINE_AT.get() : ModItems.MINE_AP.get()));
            discard();
            return true;
        }
        if (!isRemoved()) {
            detonate(level); // взрыв рядом, выстрел — детонация
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag input) {
        entityData.set(KIND, com.harddayz.Compat.getInt(input, "kind", 0));
        armed = com.harddayz.Compat.getInt(input, "armed", 60);
        com.harddayz.Compat.optString(input, "owner").ifPresent(s -> owner = UUID.fromString(s));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag output) {
        output.putInt("kind", entityData.get(KIND));
        output.putInt("armed", armed);
        if (owner != null) {
            output.putString("owner", owner.toString());
        }
    }
}
