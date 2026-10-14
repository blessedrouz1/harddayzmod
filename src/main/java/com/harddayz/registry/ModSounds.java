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
}
