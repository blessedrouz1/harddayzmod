package com.harddayz;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки мира (saves/&lt;мир&gt;/serverconfig/harddayz-server.toml). */
public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue BLOCK_DAMAGE = B
            .comment("Ломают ли взрывы блоки")
            .define("blockDamage", true);
    public static final ForgeConfigSpec.DoubleValue BLAST_SCALE = B
            .comment("Множитель силы всех взрывов")
            .defineInRange("blastScale", 1.0, 0.1, 5.0);
    public static final ForgeConfigSpec.IntValue BALLISTIC_RANGE = B
            .comment("Максимальная дальность баллистической ракеты, блоков")
            .defineInRange("ballisticRange", 2000, 200, 10000);

    public static final ForgeConfigSpec SPEC = B.build();

    private Config() {
    }

    public static boolean blockDamage() {
        return SPEC.isLoaded() ? BLOCK_DAMAGE.get() : true;
    }

    public static float blastScale() {
        return SPEC.isLoaded() ? BLAST_SCALE.get().floatValue() : 1f;
    }

    public static int ballisticRange() {
        return SPEC.isLoaded() ? BALLISTIC_RANGE.get() : 2000;
    }
}
