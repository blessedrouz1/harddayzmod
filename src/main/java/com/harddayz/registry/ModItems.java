package com.harddayz.registry;

import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.harddayz.HardDayz;
import com.harddayz.entity.Drone;
import com.harddayz.entity.Launcher;
import com.harddayz.item.AmmoItem;
import com.harddayz.item.DesignatorItem;
import com.harddayz.item.DroneItem;
import com.harddayz.item.LauncherItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HardDayz.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HardDayz.MODID);

    /** Зарегистрировать предмет: id в свойствах выставляется сам. */
    private static RegistryObject<Item> item(String name, Function<Item.Properties, Item> factory, UnaryOperator<Item.Properties> props) {
        return ITEMS.register(name, () -> factory.apply(props.apply(new Item.Properties().setId(ITEMS.key(name)))));
    }

    // ---- Пусковые установки (ставятся на землю) ----
    public static final RegistryObject<Item> GRAD = item("grad_launcher", p -> new LauncherItem(p, Launcher.Kind.GRAD),
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> ISKANDER = item("iskander_launcher", p -> new LauncherItem(p, Launcher.Kind.ISKANDER),
            p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final RegistryObject<Item> SAM = item("sam_launcher", p -> new LauncherItem(p, Launcher.Kind.SAM),
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> SHAHED_LAUNCHER = item("shahed_launcher", p -> new LauncherItem(p, Launcher.Kind.SHAHED),
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> RAMP = item("ramp_launcher", p -> new LauncherItem(p, Launcher.Kind.RAMP), p -> p.stacksTo(4));
    public static final RegistryObject<Item> GRAD_P = item("grad_p_launcher", p -> new LauncherItem(p, Launcher.Kind.GRAD_P), p -> p.stacksTo(4));
    public static final RegistryObject<Item> S400 = item("s400_launcher", p -> new LauncherItem(p, Launcher.Kind.S400), p -> p.stacksTo(1));
    public static final RegistryObject<Item> TOS = item("tos_launcher", p -> new LauncherItem(p, Launcher.Kind.TOS), p -> p.stacksTo(1));
    public static final RegistryObject<Item> TOS_ROCKET = item("tos_rocket", AmmoItem::new, p -> p.stacksTo(24));
    public static final RegistryObject<Item> HIMARS = item("himars_launcher", p -> new LauncherItem(p, Launcher.Kind.HIMARS), p -> p.stacksTo(1));
    public static final RegistryObject<Item> HIMARS_ROCKET = item("himars_rocket", AmmoItem::new, p -> p.stacksTo(6));
    public static final RegistryObject<Item> PATRIOT = item("patriot_launcher", p -> new LauncherItem(p, Launcher.Kind.PATRIOT), p -> p.stacksTo(1));
    public static final RegistryObject<Item> PATRIOT_MISSILE = item("patriot_missile", AmmoItem::new, p -> p.stacksTo(4));
    public static final RegistryObject<Item> NASAMS_MISSILE = item("nasams_missile", AmmoItem::new, p -> p.stacksTo(6));
    public static final RegistryObject<Item> NASAMS = item("nasams_launcher", p -> new LauncherItem(p, Launcher.Kind.NASAMS), p -> p.stacksTo(1));
    public static final RegistryObject<Item> JAVELIN = item("javelin", com.harddayz.item.JavelinItem::new, p -> p.stacksTo(1));
    public static final RegistryObject<Item> JAVELIN_MISSILE = item("javelin_missile", AmmoItem::new, p -> p.stacksTo(2));
    public static final RegistryObject<Item> PICKUP_MG = item("pickup_mg", p -> new LauncherItem(p, Launcher.Kind.PICKUP_MG), p -> p.stacksTo(1));
    public static final RegistryObject<Item> PICKUP_SAM = item("pickup_sam", p -> new LauncherItem(p, Launcher.Kind.PICKUP_SAM), p -> p.stacksTo(1));
    public static final RegistryObject<Item> BMP2 = item("bmp2", p -> new LauncherItem(p, Launcher.Kind.BMP2), p -> p.stacksTo(1));
    public static final RegistryObject<Item> BRADLEY = item("bradley", p -> new LauncherItem(p, Launcher.Kind.BRADLEY), p -> p.stacksTo(1));
    public static final RegistryObject<Item> CANNON_SHELLS = item("cannon_shells", AmmoItem::new, p -> p.stacksTo(64));
    // ------------------------------------------------------------------ стрелковое
    public static final RegistryObject<Item> RIFLE_AMMO = item("rifle_ammo", AmmoItem::new, p -> p.stacksTo(64));
    public static final RegistryObject<Item> SNIPER_AMMO = item("sniper_ammo", AmmoItem::new, p -> p.stacksTo(32));
    public static final RegistryObject<Item> RPG_ROUND = item("rpg_round", AmmoItem::new, p -> p.stacksTo(4));
    public static final RegistryObject<Item> AK74 = item("ak74", p -> new com.harddayz.item.GunItem(p, new com.harddayz.item.GunItem.Spec(6f, 2, 2.2f, 0.6f, 30, 45,
            () -> ModItems.RIFLE_AMMO.get(), 160, 1.15f, true, 0.75f, 1.4f, false)).sound(() -> com.harddayz.registry.ModSounds.GUN_AK.get()), p -> p.stacksTo(1));
    public static final RegistryObject<Item> M4 = item("m4", p -> new com.harddayz.item.GunItem(p, new com.harddayz.item.GunItem.Spec(5.5f, 2, 1.8f, 0.45f, 30, 40,
            () -> ModItems.RIFLE_AMMO.get(), 170, 1.3f, true, 0.7f, 1.0f, false)).sound(() -> com.harddayz.registry.ModSounds.GUN_M4.get()), p -> p.stacksTo(1));
    public static final RegistryObject<Item> PKM = item("pkm", p -> new com.harddayz.item.GunItem(p, new com.harddayz.item.GunItem.Spec(6f, 2, 3.2f, 1.5f, 100, 100,
            () -> ModItems.RIFLE_AMMO.get(), 180, 0.95f, true, 0.8f, 0.9f, false)).sound(() -> com.harddayz.registry.ModSounds.GUN_PKM.get()), p -> p.stacksTo(1));
    public static final RegistryObject<Item> SVD = item("svd", p -> new com.harddayz.item.GunItem(p, new com.harddayz.item.GunItem.Spec(17f, 9, 1.6f, 0.05f, 10, 55,
            () -> ModItems.SNIPER_AMMO.get(), 320, 0.8f, false, 0.25f, 3.5f, false)).sound(() -> com.harddayz.registry.ModSounds.GUN_SVD.get()), p -> p.stacksTo(1));
    public static final RegistryObject<Item> RPG7 = item("rpg7", p -> new com.harddayz.item.GunItem(p, new com.harddayz.item.GunItem.Spec(0f, 20, 1.2f, 0.4f, 1, 60,
            () -> ModItems.RPG_ROUND.get(), 300, 1f, false, 0.7f, 4f, true)), p -> p.stacksTo(1));
    public static final RegistryObject<Item> RED_DOT = item("red_dot", p -> new com.harddayz.item.AttachmentItem(p, "sight", "reddot"),
            p -> p.stacksTo(1));
    public static final RegistryObject<Item> SCOPE = item("scope", p -> new com.harddayz.item.AttachmentItem(p, "sight", "scope"), p -> p.stacksTo(1));
    public static final RegistryObject<Item> SUPPRESSOR = item("suppressor", p -> new com.harddayz.item.AttachmentItem(p, "muzzle", "suppressor"),
            p -> p.stacksTo(1));
    public static final RegistryObject<Item> THERMAL_SIGHT = item("thermal_sight", p -> new com.harddayz.item.AttachmentItem(p, "sight", "thermal"),
            p -> p.stacksTo(1));
    public static final RegistryObject<Item> UNDERBARREL = item("underbarrel", p -> new com.harddayz.item.AttachmentItem(p, "under", "gl"),
            p -> p.stacksTo(1));
    public static final RegistryObject<Item> VOG_ROUND = item("vog_round", AmmoItem::new, p -> p.stacksTo(16));
    public static final RegistryObject<Item> RANGEFINDER = item("rangefinder", com.harddayz.item.RangefinderItem::new, p -> p.stacksTo(1));
    public static final RegistryObject<Item> PISTOL_AMMO = item("pistol_ammo", AmmoItem::new, p -> p.stacksTo(64));
    public static final RegistryObject<Item> PM = item("pm", p -> new com.harddayz.item.GunItem(p, new com.harddayz.item.GunItem.Spec(5f, 4, 2.4f, 1.0f, 8, 30,
            () -> ModItems.PISTOL_AMMO.get(), 80, 1.4f, false, 0.85f, 2.2f, false)).sound(() -> com.harddayz.registry.ModSounds.GUN_PM.get()), p -> p.stacksTo(1));
    public static final RegistryObject<Item> GLOCK = item("glock", p -> new com.harddayz.item.GunItem(p, new com.harddayz.item.GunItem.Spec(5.5f, 3, 2.0f, 0.8f, 17, 30,
            () -> ModItems.PISTOL_AMMO.get(), 90, 1.5f, false, 0.85f, 1.8f, false)).sound(() -> com.harddayz.registry.ModSounds.GUN_GLOCK.get()), p -> p.stacksTo(1));
    public static final RegistryObject<Item> COMBAT_KNIFE = item("combat_knife", Item::new,
            p -> p.tool(net.minecraft.world.item.ToolMaterial.IRON, net.minecraft.tags.BlockTags.SWORD_EFFICIENT, 4.5f, -1.4f, 0f));
    public static final RegistryObject<Item> MACHETE = item("machete", Item::new,
            p -> p.tool(net.minecraft.world.item.ToolMaterial.IRON, net.minecraft.tags.BlockTags.SWORD_EFFICIENT, 6f, -2.4f, 0f));
    public static final RegistryObject<Item> FIELD_SHOVEL = item("field_shovel", com.harddayz.item.FieldShovelItem::new, p -> p.stacksTo(1)
            .durability(400));
    public static final RegistryObject<Item> BANDAGE = item("bandage", p -> new com.harddayz.item.MedItem(p, com.harddayz.item.MedItem.Kind.BANDAGE),
            p -> p.stacksTo(16));
    public static final RegistryObject<Item> TOURNIQUET = item("tourniquet", p -> new com.harddayz.item.MedItem(p, com.harddayz.item.MedItem.Kind.TOURNIQUET),
            p -> p.stacksTo(8));
    public static final RegistryObject<Item> MEDKIT = item("medkit", p -> new com.harddayz.item.MedItem(p, com.harddayz.item.MedItem.Kind.MEDKIT),
            p -> p.stacksTo(4));
    public static final RegistryObject<Item> PAINKILLER = item("painkiller", p -> new com.harddayz.item.MedItem(p, com.harddayz.item.MedItem.Kind.PAINKILLER),
            p -> p.stacksTo(8));
    public static final RegistryObject<Item> FRAG_GRENADE = item("frag_grenade", p -> new com.harddayz.item.GrenadeItem(p, false), p -> p.stacksTo(8));
    public static final RegistryObject<Item> SMOKE_GRENADE = item("smoke_grenade", p -> new com.harddayz.item.GrenadeItem(p, true), p -> p.stacksTo(8));
    public static final RegistryObject<Item> RELAY = item("relay", p -> new LauncherItem(p, Launcher.Kind.RELAY), p -> p.stacksTo(2));
    public static final RegistryObject<Item> T72 = item("t72", p -> new LauncherItem(p, Launcher.Kind.T72), p -> p.stacksTo(1));
    public static final RegistryObject<Item> ABRAMS = item("abrams", p -> new LauncherItem(p, Launcher.Kind.ABRAMS), p -> p.stacksTo(1));
    public static final RegistryObject<Item> TANK_SHELL = item("tank_shell", AmmoItem::new, p -> p.stacksTo(22));
    public static final RegistryObject<Item> KA52 = item("ka52", p -> new LauncherItem(p, Launcher.Kind.KA52), p -> p.stacksTo(1));
    public static final RegistryObject<Item> MI8 = item("mi8", p -> new LauncherItem(p, Launcher.Kind.MI8), p -> p.stacksTo(1));
    public static final RegistryObject<Item> S8_ROCKET = item("s8_rocket", AmmoItem::new, p -> p.stacksTo(40));
    public static final RegistryObject<Item> MORTAR = item("mortar", p -> new LauncherItem(p, Launcher.Kind.MORTAR), p -> p.stacksTo(2));
    public static final RegistryObject<Item> MORTAR_SHELL = item("mortar_shell", AmmoItem::new, p -> p.stacksTo(12));
    public static final RegistryObject<Item> JERRYCAN = item("jerrycan", AmmoItem::new, p -> p.stacksTo(4));
    public static final RegistryObject<Item> EMPTY_JERRYCAN = item("empty_jerrycan", Item::new, p -> p.stacksTo(4));
    public static final RegistryObject<Item> SAM_POST = item("sam_post", p -> new LauncherItem(p, Launcher.Kind.SAM_POST), p -> p.stacksTo(4));
    public static final RegistryObject<Item> EW_STATION = item("ew_station", p -> new LauncherItem(p, Launcher.Kind.EW),
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final RegistryObject<Item> SAM_MANUAL = item("sam_manual", p -> new LauncherItem(p, Launcher.Kind.SAM_MANUAL), p -> p.stacksTo(4));
    public static final RegistryObject<Item> MG_NEST = item("mg_nest", p -> new LauncherItem(p, Launcher.Kind.MG), p -> p.stacksTo(4));
    public static final RegistryObject<Item> MG_BELT = item("mg_belt", AmmoItem::new, p -> p.stacksTo(16));

    public static final RegistryObject<Item> RADAR_TRUCK = item("radar_truck", p -> new LauncherItem(p, Launcher.Kind.RADAR),
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> RADAR_DISPLAY = item("radar_display", com.harddayz.item.RadarDisplayItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    // ---- Дроны ----
    public static final RegistryObject<Item> SHAHED_238 = item("shahed_238", AmmoItem::new, p -> p.stacksTo(5).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> SHAHED_131 = item("shahed_131", AmmoItem::new, p -> p.stacksTo(5));
    public static final RegistryObject<Item> GERBERA = item("gerbera", AmmoItem::new, p -> p.stacksTo(5));
    public static final RegistryObject<Item> SHAHED_DRONE = item("shahed_drone", AmmoItem::new, p -> p.stacksTo(5));
    public static final RegistryObject<Item> INTERCEPTOR = item("interceptor_drone", p -> new DroneItem(p, Drone.Kind.INTERCEPTOR),
            p -> p.stacksTo(4));
    public static final RegistryObject<Item> LANCET = item("lancet", p -> new DroneItem(p, Drone.Kind.LANCET), p -> p.stacksTo(2));
    public static final RegistryObject<Item> WING_RECON = item("wing_recon", p -> new DroneItem(p, Drone.Kind.WING_RECON), p -> p.stacksTo(1));
    public static final RegistryObject<Item> FPV_DRONE = item("fpv_drone", p -> new DroneItem(p, Drone.Kind.FPV), p -> p.stacksTo(4));
    public static final RegistryObject<Item> RECON_DRONE = item("recon_drone", p -> new DroneItem(p, Drone.Kind.RECON),
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    // ---- Боеприпасы ----
    public static final RegistryObject<Item> GRAD_ROCKET = item("grad_rocket", AmmoItem::new, p -> p.stacksTo(40));
    public static final RegistryObject<Item> BALLISTIC_MISSILE = item("ballistic_missile", AmmoItem::new, p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final RegistryObject<Item> SAM_MISSILE = item("sam_missile", AmmoItem::new, p -> p.stacksTo(8));

    // ---- Управление ----
    public static final RegistryObject<Item> DESIGNATOR = item("designator", DesignatorItem::new, p -> p.stacksTo(1));
    public static final RegistryObject<Item> TARGET_LOG = item("target_log", com.harddayz.item.TargetLogItem::new, p -> p.stacksTo(1));
    public static final RegistryObject<Item> SUPPLY_TABLET = item("supply_tablet", com.harddayz.item.SupplyTabletItem::new,
            p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final RegistryObject<Item> OPERATOR_TERMINAL = item("operator_terminal", com.harddayz.item.OperatorTerminalItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    // ---- Мины ----
    public static final RegistryObject<Item> MINE_AP = item("mine_ap", p -> new com.harddayz.item.MineItem(p, com.harddayz.entity.Mine.Kind.AP),
            p -> p.stacksTo(16));
    public static final RegistryObject<Item> MINE_AT = item("mine_at", p -> new com.harddayz.item.MineItem(p, com.harddayz.entity.Mine.Kind.AT),
            p -> p.stacksTo(8));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.harddayz"))
            .icon(() -> new ItemStack(ISKANDER.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> {
                if (!weapon(e.get()) && !eng(e.get())) {
                    out.accept(e.get());
                }
            }))
            .build());

    /** Отдельная вкладка: стрелковое оружие, гранаты, обвесы, патроны. */
    public static final RegistryObject<CreativeModeTab> WEAPONS_TAB = TABS.register("weapons", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.harddayz.weapons"))
            .icon(() -> new ItemStack(AK74.get()))
            .withTabsBefore(TAB.getKey())
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> {
                if (weapon(e.get())) {
                    out.accept(e.get());
                }
            }))
            .build());

    /** Третья вкладка: инженерка и медицина. */
    public static final RegistryObject<CreativeModeTab> ENG_TAB = TABS.register("engineering", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.harddayz.engineering"))
            .icon(() -> new ItemStack(FIELD_SHOVEL.get()))
            .withTabsBefore(WEAPONS_TAB.getKey())
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> {
                if (eng(e.get())) {
                    out.accept(e.get());
                }
            }))
            .build());

    static boolean eng(Item i) {
        return i instanceof com.harddayz.item.MedItem || i instanceof com.harddayz.item.FieldShovelItem
                || i instanceof net.minecraft.world.item.BlockItem b && b.getBlock() instanceof com.harddayz.war.EngBlock;
    }

    private static boolean weapon(Item i) {
        return i instanceof com.harddayz.item.GunItem || i instanceof com.harddayz.item.GrenadeItem || i instanceof com.harddayz.item.JavelinItem
                || i instanceof com.harddayz.item.AttachmentItem || i == RIFLE_AMMO.get() || i == SNIPER_AMMO.get() || i == RPG_ROUND.get() || i == JAVELIN_MISSILE.get() || i == VOG_ROUND.get()
                || i == RANGEFINDER.get() || i == PISTOL_AMMO.get() || i == COMBAT_KNIFE.get() || i == MACHETE.get();
    }

    private ModItems() {
    }
}
