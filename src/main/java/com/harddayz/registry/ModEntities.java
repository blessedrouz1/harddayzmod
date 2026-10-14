package com.harddayz.registry;

import com.harddayz.HardDayz;
import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;
import com.harddayz.entity.Missile;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HardDayz.MODID);

    public static final RegistryObject<EntityType<Missile>> MISSILE = ENTITIES.register("missile",
            () -> EntityType.Builder.<Missile>of(Missile::new, MobCategory.MISC).noLootTable().sized(0.6f, 0.6f).clientTrackingRange(32)
                    .updateInterval(1).build(key("missile")));

    public static final RegistryObject<EntityType<Launcher>> LAUNCHER = ENTITIES.register("launcher",
            () -> EntityType.Builder.<Launcher>of(Launcher::new, MobCategory.MISC).noLootTable().sized(3.0f, 2.6f).clientTrackingRange(16)
                    .updateInterval(1).build(key("launcher")));

    public static final RegistryObject<EntityType<Drone>> DRONE = ENTITIES.register("drone",
            () -> EntityType.Builder.<Drone>of(Drone::new, MobCategory.MISC).noLootTable().sized(0.9f, 0.5f).clientTrackingRange(24)
                    .updateInterval(1).build(key("drone")));

    public static final RegistryObject<EntityType<com.harddayz.entity.Mine>> MINE = ENTITIES.register("mine",
            () -> EntityType.Builder.<com.harddayz.entity.Mine>of(com.harddayz.entity.Mine::new, MobCategory.MISC).noLootTable().sized(0.6f, 0.2f)
                    .clientTrackingRange(8).updateInterval(20).build(key("mine")));

    private static ResourceKey<EntityType<?>> key(String name) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(HardDayz.MODID, name));
    }

    private ModEntities() {
    }
}
