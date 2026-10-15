package com.harddayz.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

/**
 * Тип отрисовки моделей техники. С Oculus (Iris) полупрозрачные сущности рисуются отдельным проходом и могут
 * не появиться вовсе, поэтому с ним рисуем «вырезкой» (стёкла станут непрозрачными, зато модель видна всегда).
 */
public final class HdRender {
    private static final boolean OCULUS = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");

    private HdRender() {
    }

    public static RenderType body(ResourceLocation tex) {
        return OCULUS ? RenderType.entityCutoutNoCull(tex) : RenderType.entityTranslucent(tex);
    }
}
