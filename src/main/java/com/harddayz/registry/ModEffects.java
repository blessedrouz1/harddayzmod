package com.harddayz.registry;

import com.harddayz.HardDayz;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HardDayz.MODID);

    /** Кровотечение: урон раз в 2 с (сильное — раз в секунду), пока не перевяжут. */
    public static final RegistryObject<MobEffect> BLEEDING = EFFECTS.register("bleeding", Bleeding::new);

    private ModEffects() {
    }

    static final class Bleeding extends MobEffect {
        Bleeding() {
            super(MobEffectCategory.HARMFUL, 0x8A0303);
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity mob, int amp) {
            mob.hurtServer(level, mob.damageSources().generic(), 1f);
            return true;
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int tick, int amp) {
            return tick % (amp > 0 ? 20 : 40) == 0;
        }
    }
}
