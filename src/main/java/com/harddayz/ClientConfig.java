package com.harddayz;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки клиента (config/harddayz-client.toml), меняются и в меню мода. */
public final class ClientConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue BLAST_PARTICLES = B
            .comment("Свои частицы взрывов: огонь, дым, пыль, обломки (выключи, если стоит другой мод на взрывы)")
            .define("blastParticles", true);
    public static final ForgeConfigSpec.BooleanValue BLAST_SOUNDS = B
            .comment("Свои звуки взрывов")
            .define("blastSounds", true);
    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE = B
            .comment("Тряска камеры от взрывов")
            .define("screenShake", true);
    public static final ForgeConfigSpec.BooleanValue SKY_FLASH = B
            .comment("Вспышка и зарево неба от взрывов")
            .define("skyFlash", true);

    public static final ForgeConfigSpec SPEC = B.build();

    private ClientConfig() {
    }

    private static boolean get(ForgeConfigSpec.BooleanValue v) {
        return !SPEC.isLoaded() || v.get();
    }

    public static boolean particles() {
        return get(BLAST_PARTICLES);
    }

    public static boolean sounds() {
        return get(BLAST_SOUNDS);
    }

    public static boolean shake() {
        return get(SCREEN_SHAKE);
    }

    public static boolean flash() {
        return get(SKY_FLASH);
    }
}
