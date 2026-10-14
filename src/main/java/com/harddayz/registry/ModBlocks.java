package com.harddayz.registry;

import com.harddayz.HardDayz;
import com.harddayz.war.HqBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HardDayz.MODID);

    /** Штаб: прочный, но ракетой разрушается. */
    public static final RegistryObject<Block> HQ = BLOCKS.register("hq", () -> new HqBlock(BlockBehaviour.Properties.of()
            .setId(BLOCKS.key("hq")).mapColor(MapColor.COLOR_GREEN).strength(25f, 9f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> HQ_ITEM = ModItems.ITEMS.register("hq",
            () -> new BlockItem(HQ.get(), new Item.Properties().setId(ModItems.ITEMS.key("hq")).useBlockDescriptionPrefix().rarity(Rarity.EPIC)));

    /** Склад фракции: точка доставки поставок. */
    public static final RegistryObject<Block> WAREHOUSE = BLOCKS.register("warehouse", () -> new com.harddayz.war.WarehouseBlock(
            BlockBehaviour.Properties.of().setId(BLOCKS.key("warehouse")).mapColor(MapColor.WOOD).strength(6f, 6f).sound(SoundType.WOOD)));

    public static final RegistryObject<Item> WAREHOUSE_ITEM = ModItems.ITEMS.register("warehouse",
            () -> new BlockItem(WAREHOUSE.get(), new Item.Properties().setId(ModItems.ITEMS.key("warehouse")).useBlockDescriptionPrefix()));

    /** Громкоговоритель воздушной тревоги. */
    public static final RegistryObject<Block> SIREN = BLOCKS.register("siren", () -> new com.harddayz.war.SirenBlock(
            BlockBehaviour.Properties.of().setId(BLOCKS.key("siren")).mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL)));

    public static final RegistryObject<Item> SIREN_ITEM = ModItems.ITEMS.register("siren",
            () -> new BlockItem(SIREN.get(), new Item.Properties().setId(ModItems.ITEMS.key("siren")).useBlockDescriptionPrefix()));

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HardDayz.MODID);

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<com.harddayz.war.HqBlockEntity>> HQ_BE =
            BLOCK_ENTITIES.register("hq", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(com.harddayz.war.HqBlockEntity::new,
                    java.util.Set.of(HQ.get())));

    /** Рубеж: точка захвата. */
    public static final RegistryObject<Block> FRONTLINE = BLOCKS.register("frontline", () -> new com.harddayz.war.FrontlineBlock(
            BlockBehaviour.Properties.of().setId(BLOCKS.key("frontline")).mapColor(MapColor.COLOR_RED).strength(-1f, 3600000f).noLootTable()));

    public static final RegistryObject<Item> FRONTLINE_ITEM = ModItems.ITEMS.register("frontline",
            () -> new BlockItem(FRONTLINE.get(), new Item.Properties().setId(ModItems.ITEMS.key("frontline")).useBlockDescriptionPrefix()
                    .rarity(Rarity.EPIC)));

    public static void init() {
    }

    private ModBlocks() {
    }
}
