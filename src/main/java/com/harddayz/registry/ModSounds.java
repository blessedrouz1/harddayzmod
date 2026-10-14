package com.harddayz.registry;

import com.harddayz.HardDayz;

import net.minecraft.resources.Identifier;
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

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(HardDayz.MODID, name)));
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
    public static final RegistryObject<SoundEvent> TANK_FIRE = reg("tank_fire");
    public static final RegistryObject<SoundEvent> MORTAR_FIRE = reg("mortar_fire");
}
