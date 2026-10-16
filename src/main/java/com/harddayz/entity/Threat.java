package com.harddayz.entity;

import java.util.UUID;

import javax.annotation.Nullable;

/** То, что ПВО может сбить: ракеты и дроны. */
public interface Threat {
    boolean isThreat();

    @Nullable UUID owner();

    /** Точка пуска (для засечки РЛС), null — неизвестна. */
    default @Nullable net.minecraft.world.phys.Vec3 origin() {
        return null;
    }

    /** Тип для засечки пуска: ballistic, cruise, mlrs, mortar, shahed, jetdrone, drone. */
    default String launchType() {
        return "unknown";
    }
}
