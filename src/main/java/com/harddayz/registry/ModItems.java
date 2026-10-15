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
        return ITEMS.register(name, () -> factory.apply(props.apply(new Item.Properties())));
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
    public static final RegistryObject<Item> RANGEFINDER = item("rangefinder", com.harddayz.item.RangefinderItem::new, p -> p.stacksTo(1));
    // SwordItem сам прибавляет урон железа (+2); в 1.20.1 бонус только целый, нож 4.5 -> 4
    public static final RegistryObject<Item> COMBAT_KNIFE = item("combat_knife", p -> new net.minecraft.world.item.SwordItem(
            net.minecraft.world.item.Tiers.IRON, 4, -1.4f, p), p -> p);
    public static final RegistryObject<Item> MACHETE = item("machete", p -> new net.minecraft.world.item.SwordItem(
            net.minecraft.world.item.Tiers.IRON, 6, -2.4f, p), p -> p);
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
    public static final RegistryObject<Item> GERAN5 = item("geran5", AmmoItem::new, p -> p.stacksTo(5).rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> BANDEROL = item("banderol", AmmoItem::new, p -> p.stacksTo(5));
    public static final RegistryObject<Item> KUB = item("kub_drone", p -> new DroneItem(p, Drone.Kind.KUB), p -> p.stacksTo(2));
    public static final RegistryObject<Item> MOLNIYA = item("molniya", p -> new DroneItem(p, Drone.Kind.MOLNIYA), p -> p.stacksTo(2));
    public static final RegistryObject<Item> BABA_YAGA = item("baba_yaga", p -> new DroneItem(p, Drone.Kind.BABA_YAGA), p -> p.stacksTo(1));
    public static final RegistryObject<Item> TOCHKA = item("tochka_launcher", p -> new LauncherItem(p, Launcher.Kind.TOCHKA), p -> p.stacksTo(1));
    public static final RegistryObject<Item> SMERCH = item("smerch_launcher", p -> new LauncherItem(p, Launcher.Kind.SMERCH), p -> p.stacksTo(1));
    public static final RegistryObject<Item> URAGAN = item("uragan_launcher", p -> new LauncherItem(p, Launcher.Kind.URAGAN), p -> p.stacksTo(1));
    public static final RegistryObject<Item> KALIBR = item("kalibr_launcher", p -> new LauncherItem(p, Launcher.Kind.KALIBR), p -> p.stacksTo(1));
    public static final RegistryObject<Item> TOCHKA_MISSILE = item("tochka_missile", AmmoItem::new, p -> p.stacksTo(1));
    public static final RegistryObject<Item> SMERCH_ROCKET = item("smerch_rocket", AmmoItem::new, p -> p.stacksTo(12));
    public static final RegistryObject<Item> URAGAN_ROCKET = item("uragan_rocket", AmmoItem::new, p -> p.stacksTo(16));
    public static final RegistryObject<Item> KALIBR_MISSILE = item("kalibr_missile", AmmoItem::new, p -> p.stacksTo(4));
    public static final RegistryObject<Item> S400_MISSILE = item("s400_missile", AmmoItem::new, p -> p.stacksTo(4));
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
                if (!weapon(e.get()) && !eng(e.get()) && !ammo(e.get())) {
                    out.accept(e.get());
                }
            }))
            .build());

    /** Отдельная вкладка: стрелковое оружие, гранаты, обвесы, патроны. */
    /** Отдельная вкладка: ракеты, снаряды и мины для пусковых (без дронов). */
    public static final RegistryObject<CreativeModeTab> AMMO_TAB = TABS.register("ammo", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.harddayz.ammo"))
            .icon(() -> new ItemStack(ModItems.BALLISTIC_MISSILE.get()))
            .withTabsBefore(ModItems.TAB.getKey())
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> {
                if (ammo(e.get())) {
                    out.accept(e.get());
                }
            }))
            .build());

    /** Боеприпасы для пусковых: ракеты, снаряды, ленты (дроны для пусковой Шахедов остаются с техникой). */
    static boolean ammo(Item i) {
        return i instanceof AmmoItem && com.harddayz.entity.Launcher.droneKind(new ItemStack(i)) == null && i != JERRYCAN.get()
                && i != JAVELIN_MISSILE.get();
    }

    public static final RegistryObject<CreativeModeTab> WEAPONS_TAB = TABS.register("weapons", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.harddayz.weapons"))
            .icon(() -> new ItemStack(JAVELIN.get()))
            .withTabsBefore(ModItems.AMMO_TAB.getKey())
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
                || i instanceof net.minecraft.world.item.BlockItem b && (b.getBlock() instanceof com.harddayz.war.EngBlock
                        || ModBlocks.isBuild(b.getBlock()));
    }

    private static boolean weapon(Item i) {
        return i instanceof com.harddayz.item.GrenadeItem || i instanceof com.harddayz.item.JavelinItem || i == JAVELIN_MISSILE.get()
                || i == RANGEFINDER.get() || i == COMBAT_KNIFE.get() || i == MACHETE.get();
    }

    private ModItems() {
    }
}
