package com.harddayz.registry;

import com.harddayz.HardDayz;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Свои частицы: мягкий дым, огонь, пыль. Видны издалека (без ограничителя дистанции). */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HardDayz.MODID);

    public static final RegistryObject<SimpleParticleType> SMOKE = reg("smoke");
    public static final RegistryObject<SimpleParticleType> SMOKE_HUGE = reg("smoke_huge");
    public static final RegistryObject<SimpleParticleType> SMOKE_WHITE = reg("smoke_white");
    public static final RegistryObject<SimpleParticleType> DUST = reg("dust");
    public static final RegistryObject<SimpleParticleType> FIRE = reg("fire");
    public static final RegistryObject<SimpleParticleType> FLASH = reg("flash");
    public static final RegistryObject<SimpleParticleType> JET_SMOKE = reg("jet_smoke");
    public static final RegistryObject<SimpleParticleType> JET_FIRE = reg("jet_fire");
    public static final RegistryObject<SimpleParticleType> GLOW = reg("glow");

    private static RegistryObject<SimpleParticleType> reg(String name) {
        return TYPES.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {
    }
}
