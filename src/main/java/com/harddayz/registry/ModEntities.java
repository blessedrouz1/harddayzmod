package com.harddayz.registry;

import com.harddayz.HardDayz;
import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;
import com.harddayz.entity.Missile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HardDayz.MODID);

    public static final RegistryObject<EntityType<Missile>> MISSILE = ENTITIES.register("missile",
            () -> EntityType.Builder.<Missile>of(Missile::new, MobCategory.MISC).sized(0.6f, 0.6f).clientTrackingRange(32)
                    .updateInterval(1).build("missile"));

    public static final RegistryObject<EntityType<Launcher>> LAUNCHER = ENTITIES.register("launcher",
            () -> EntityType.Builder.<Launcher>of(Launcher::new, MobCategory.MISC).sized(3.0f, 2.6f).clientTrackingRange(16)
                    .updateInterval(1).build("launcher"));

    public static final RegistryObject<EntityType<Drone>> DRONE = ENTITIES.register("drone",
            () -> EntityType.Builder.<Drone>of(Drone::new, MobCategory.MISC).sized(0.9f, 0.5f).clientTrackingRange(24)
                    .updateInterval(1).build("drone"));

    public static final RegistryObject<EntityType<com.harddayz.entity.GasCloud>> GAS_CLOUD = ENTITIES.register("gas_cloud",
            () -> EntityType.Builder.<com.harddayz.entity.GasCloud>of(com.harddayz.entity.GasCloud::new, MobCategory.MISC).sized(1f, 1f)
                    .clientTrackingRange(8).updateInterval(10).build("gas_cloud"));

    public static final RegistryObject<EntityType<com.harddayz.entity.Mine>> MINE = ENTITIES.register("mine",
            () -> EntityType.Builder.<com.harddayz.entity.Mine>of(com.harddayz.entity.Mine::new, MobCategory.MISC).sized(0.6f, 0.2f)
                    .clientTrackingRange(8).updateInterval(20).build("mine"));

    private ModEntities() {
    }
}
