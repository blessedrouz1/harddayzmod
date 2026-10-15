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
            .mapColor(MapColor.COLOR_GREEN).strength(25f, 9f).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> HQ_ITEM = ModItems.ITEMS.register("hq",
            () -> new BlockItem(HQ.get(), new Item.Properties().rarity(Rarity.EPIC)));

    /** Склад фракции: точка доставки поставок. */
    public static final RegistryObject<Block> WAREHOUSE = BLOCKS.register("warehouse", () -> new com.harddayz.war.WarehouseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(6f, 6f).sound(SoundType.WOOD)));

    public static final RegistryObject<Item> WAREHOUSE_ITEM = ModItems.ITEMS.register("warehouse",
            () -> new BlockItem(WAREHOUSE.get(), new Item.Properties()));

    /** Громкоговоритель воздушной тревоги. */
    public static final RegistryObject<Block> SIREN = BLOCKS.register("siren", () -> new com.harddayz.war.SirenBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f, 6f).sound(SoundType.METAL)));

    public static final RegistryObject<Item> SIREN_ITEM = ModItems.ITEMS.register("siren",
            () -> new BlockItem(SIREN.get(), new Item.Properties()));

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HardDayz.MODID);

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<com.harddayz.war.HqBlockEntity>> HQ_BE =
            BLOCK_ENTITIES.register("hq", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.harddayz.war.HqBlockEntity::new,
                    HQ.get()).build(null));

    /** Рубеж: точка захвата. */
    public static final RegistryObject<Block> FRONTLINE = BLOCKS.register("frontline", () -> new com.harddayz.war.FrontlineBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1f, 3600000f).noLootTable()));

    public static final RegistryObject<Item> FRONTLINE_ITEM = ModItems.ITEMS.register("frontline",
            () -> new BlockItem(FRONTLINE.get(), new Item.Properties()
                    .rarity(Rarity.EPIC)));

    // ------------------------------------------------------------------ инженерка: обустройство позиций
    public static final RegistryObject<Block> SANDBAGS = BLOCKS.register("sandbags", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND).strength(2.0f, 14f).sound(SoundType.SAND),
            com.harddayz.war.EngBlock.Type.SOLID, 0.0, 0.0, 0.0, 16.0, 16.0, 16.0));
    public static final RegistryObject<Item> SANDBAGS_ITEM = ModItems.ITEMS.register("sandbags",
            () -> new BlockItem(SANDBAGS.get(), new Item.Properties()));

    public static final RegistryObject<Block> SANDBAG_WALL = BLOCKS.register("sandbag_wall", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SAND).strength(1.5f, 12f).sound(SoundType.SAND).noOcclusion(),
            com.harddayz.war.EngBlock.Type.SOLID, 0.0, 0.0, 4.0, 16.0, 12.0, 12.0));
    public static final RegistryObject<Item> SANDBAG_WALL_ITEM = ModItems.ITEMS.register("sandbag_wall",
            () -> new BlockItem(SANDBAG_WALL.get(), new Item.Properties()));

    public static final RegistryObject<Block> CONCRETE_FORT = BLOCKS.register("concrete_fort", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(8.0f, 40f).sound(SoundType.STONE),
            com.harddayz.war.EngBlock.Type.SOLID, 0.0, 0.0, 0.0, 16.0, 16.0, 16.0));
    public static final RegistryObject<Item> CONCRETE_FORT_ITEM = ModItems.ITEMS.register("concrete_fort",
            () -> new BlockItem(CONCRETE_FORT.get(), new Item.Properties()));

    public static final RegistryObject<Block> CONCRETE_BARRIER = BLOCKS.register("concrete_barrier", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(6.0f, 30f).sound(SoundType.STONE).noOcclusion(),
            com.harddayz.war.EngBlock.Type.SOLID, 0.0, 0.0, 3.0, 16.0, 14.0, 13.0));
    public static final RegistryObject<Item> CONCRETE_BARRIER_ITEM = ModItems.ITEMS.register("concrete_barrier",
            () -> new BlockItem(CONCRETE_BARRIER.get(), new Item.Properties()));

    public static final RegistryObject<Block> HEDGEHOG = BLOCKS.register("hedgehog", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BROWN).strength(6.0f, 25f).sound(SoundType.METAL).noOcclusion(),
            com.harddayz.war.EngBlock.Type.SOLID, 1.0, 0.0, 1.0, 15.0, 15.0, 15.0));
    public static final RegistryObject<Item> HEDGEHOG_ITEM = ModItems.ITEMS.register("hedgehog",
            () -> new BlockItem(HEDGEHOG.get(), new Item.Properties()));

    public static final RegistryObject<Block> DRAGON_TEETH = BLOCKS.register("dragon_teeth", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(6.0f, 30f).sound(SoundType.STONE).noOcclusion(),
            com.harddayz.war.EngBlock.Type.SOLID, 2.0, 0.0, 2.0, 14.0, 12.0, 14.0));
    public static final RegistryObject<Item> DRAGON_TEETH_ITEM = ModItems.ITEMS.register("dragon_teeth",
            () -> new BlockItem(DRAGON_TEETH.get(), new Item.Properties()));

    public static final RegistryObject<Block> BARBED_WIRE = BLOCKS.register("barbed_wire", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(1.0f, 2f).sound(SoundType.CHAIN).noOcclusion(),
            com.harddayz.war.EngBlock.Type.WIRE, 0.0, 0.0, 0.0, 16.0, 12.0, 16.0));
    public static final RegistryObject<Item> BARBED_WIRE_ITEM = ModItems.ITEMS.register("barbed_wire",
            () -> new BlockItem(BARBED_WIRE.get(), new Item.Properties()));

    public static final RegistryObject<Block> CAMO_NET = BLOCKS.register("camo_net", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT).strength(0.3f, 0.5f).sound(SoundType.WOOL).noOcclusion(),
            com.harddayz.war.EngBlock.Type.NET, 0.0, 15.0, 0.0, 16.0, 16.0, 16.0));
    public static final RegistryObject<Item> CAMO_NET_ITEM = ModItems.ITEMS.register("camo_net",
            () -> new BlockItem(CAMO_NET.get(), new Item.Properties()));

    public static final RegistryObject<Block> CAMO_NET_WALL = BLOCKS.register("camo_net_wall", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.PLANT).strength(0.3f, 0.5f).sound(SoundType.WOOL).noOcclusion(),
            com.harddayz.war.EngBlock.Type.NET, 0.0, 0.0, 7.5, 16.0, 16.0, 8.5));
    public static final RegistryObject<Item> CAMO_NET_WALL_ITEM = ModItems.ITEMS.register("camo_net_wall",
            () -> new BlockItem(CAMO_NET_WALL.get(), new Item.Properties()));

    public static final RegistryObject<Block> AMMO_CRATE = BLOCKS.register("ammo_crate", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN).strength(2.0f, 3f).sound(SoundType.WOOD).noOcclusion(),
            com.harddayz.war.EngBlock.Type.SOLID, 1.0, 0.0, 3.0, 15.0, 10.0, 13.0));
    public static final RegistryObject<Item> AMMO_CRATE_ITEM = ModItems.ITEMS.register("ammo_crate",
            () -> new BlockItem(AMMO_CRATE.get(), new Item.Properties()));

    public static final RegistryObject<Block> REVETMENT = BLOCKS.register("revetment", () -> new com.harddayz.war.EngBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOD).strength(2.0f, 6f).sound(SoundType.WOOD).noOcclusion(),
            com.harddayz.war.EngBlock.Type.SOLID, 0.0, 0.0, 13.0, 16.0, 16.0, 16.0));
    public static final RegistryObject<Item> REVETMENT_ITEM = ModItems.ITEMS.register("revetment",
            () -> new BlockItem(REVETMENT.get(), new Item.Properties()));

    public static void init() {
    }

    private ModBlocks() {
    }
}
