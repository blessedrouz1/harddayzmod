package com.harddayz;

import org.slf4j.Logger;

import com.harddayz.registry.ModEntities;
import com.harddayz.registry.ModItems;
import com.harddayz.registry.ModParticles;
import com.mojang.logging.LogUtils;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(HardDayz.MODID)
public final class HardDayz {
    public static final String MODID = "harddayz";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HardDayz(FMLJavaModLoadingContext context) {
        var bus = context.getModEventBus();
        com.harddayz.registry.ModBlocks.init();
        com.harddayz.registry.ModBlocks.BLOCKS.register(bus);
        com.harddayz.registry.ModBlocks.BLOCK_ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModParticles.TYPES.register(bus);
        com.harddayz.registry.ModSounds.SOUNDS.register(bus);
        com.harddayz.registry.ModEffects.EFFECTS.register(bus);
        com.harddayz.war.Medic.init();
        context.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        com.harddayz.network.Net.init();
        com.harddayz.war.WarEvents.init();
        if (FMLEnvironment.dist.isClient()) {
            com.harddayz.client.ClientSetup.init(bus);
        }
    }
}
