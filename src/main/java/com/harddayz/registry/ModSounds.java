package com.harddayz.registry;

import com.harddayz.HardDayz;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Звуки мода. Источники (OpenGameArt): взрывы — Joth, TinyWorlds, NenadSimic, pauliuw (CC0);
 * пуски — mikeask, qubodup (CC0); пролёт — pyranostudios (CC0); сирена — aquinn (CC0);
 * пулемёт — KuraiWolf (CC-BY 4.0); турбина — dklon (CC-BY 3.0); мотор — qubodup (CC-BY 3.0).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HardDayz.MODID);

    public static final RegistryObject<SoundEvent> EXPLODE_NEAR = reg("explode_near");
    public static final RegistryObject<SoundEvent> EXPLODE_BIG = reg("explode_big");
    public static final RegistryObject<SoundEvent> EXPLODE_FAR = reg("explode_far");
    public static final RegistryObject<SoundEvent> MISSILE_LAUNCH = reg("missile_launch");
    public static final RegistryObject<SoundEvent> ROCKET_LAUNCH = reg("rocket_launch");
    public static final RegistryObject<SoundEvent> WHOOSH = reg("whoosh");
    public static final RegistryObject<SoundEvent> MG_FIRE = reg("mg_fire");
    public static final RegistryObject<SoundEvent> JET_LOOP = reg("jet_loop");
    public static final RegistryObject<SoundEvent> ENGINE_LOOP = reg("engine_loop");
    public static final RegistryObject<SoundEvent> SIREN = reg("siren");


    public static final RegistryObject<SoundEvent> LAUNCH_CRUISE = reg("launch_cruise");
    public static final RegistryObject<SoundEvent> LAUNCH_SHAHED = reg("launch_shahed");
    public static final RegistryObject<SoundEvent> SAM_LAUNCH = reg("sam_launch");
    public static final RegistryObject<SoundEvent> FPV_LOOP = reg("fpv_loop");
    public static final RegistryObject<SoundEvent> INCOMING_MORTAR = reg("incoming_mortar");
    public static final RegistryObject<SoundEvent> INCOMING_ROCKET = reg("incoming_rocket");
    public static final RegistryObject<SoundEvent> INCOMING_BALLISTIC = reg("incoming_ballistic");
    public static final RegistryObject<SoundEvent> INCOMING_SHAHED = reg("incoming_shahed");
    public static final RegistryObject<SoundEvent> INCOMING_CRUISE = reg("incoming_cruise");

    public static final RegistryObject<SoundEvent> EXP_MEDIUM = reg("exp_medium");
    public static final RegistryObject<SoundEvent> EXP_LARGE = reg("exp_large");
    public static final RegistryObject<SoundEvent> FLY_ROCKET = reg("fly_rocket");
    public static final RegistryObject<SoundEvent> FLY_MISSILE = reg("fly_missile");
    public static final RegistryObject<SoundEvent> FLY_JET = reg("fly_jet");


    /** Взрывы по оружию: exp_&lt;тип&gt;[_air]_&lt;near|mid|far&gt;. */
    public static final String[] BLAST_TYPES = {"small", "mortar", "rocket", "thermo", "ballistic", "shahed", "cruise"};
    public static final java.util.Map<String, RegistryObject<SoundEvent>> BLASTS = new java.util.HashMap<>();

    static {
        for (String t : BLAST_TYPES) {
            for (String air : new String[] {"", "_air"}) {
                for (String tier : new String[] {"near", "mid", "far"}) {
                    String n = "exp_" + t + air + "_" + tier;
                    BLASTS.put(n, reg(n));
                }
            }
        }
    }

    public static SoundEvent blast(int type, boolean air, int tier) {
        return BLASTS.get("exp_" + BLAST_TYPES[type] + (air ? "_air" : "") + "_" + new String[] {"near", "mid", "far"}[tier]).get();
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new net.minecraft.resources.ResourceLocation(HardDayz.MODID, name)));
    }

    private ModSounds() {
    }
    public static final RegistryObject<SoundEvent> GUN_AK = reg("gun_ak");
    public static final RegistryObject<SoundEvent> GUN_M4 = reg("gun_m4");
    public static final RegistryObject<SoundEvent> GUN_PKM = reg("gun_pkm");
    public static final RegistryObject<SoundEvent> GUN_SVD = reg("gun_svd");
    public static final RegistryObject<SoundEvent> GUN_PM = reg("gun_pm");
    public static final RegistryObject<SoundEvent> GUN_GLOCK = reg("gun_glock");
    public static final RegistryObject<SoundEvent> RELOAD_RIFLE = reg("reload_rifle");
    public static final RegistryObject<SoundEvent> RELOAD_PISTOL = reg("reload_pistol");
    public static final RegistryObject<SoundEvent> HELI_LOOP = reg("heli_loop");
    public static final RegistryObject<SoundEvent> DIESEL_LOOP = reg("diesel_loop");
    public static final RegistryObject<SoundEvent> TURBINE_LOOP = reg("turbine_loop");
    public static final RegistryObject<SoundEvent> SHELL_WHISTLE = reg("shell_whistle");
    public static final RegistryObject<SoundEvent> MORTAR_FIRE = reg("mortar_fire");
}
