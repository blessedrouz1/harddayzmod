package com.harddayz.entity;

import java.util.UUID;

import javax.annotation.Nullable;

/** То, что ПВО может сбить: ракеты и дроны. */
public interface Threat {
    boolean isThreat();

    @Nullable UUID owner();
}
