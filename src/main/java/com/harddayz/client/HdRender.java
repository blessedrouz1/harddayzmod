package com.harddayz.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.harddayz.client.model.GeoGpu;

/** Standard entity materials, also recognized by Oculus shader packs. */
public final class HdRender {

    private HdRender() {
    }

    public static RenderType body(ResourceLocation tex) {
        return GeoGpu.pass(RenderType.entityTranslucent(tex), GeoGpu.Pass.TRANSLUCENT);
    }

    public static RenderType eyes(ResourceLocation tex) {
        return GeoGpu.pass(RenderType.eyes(tex), GeoGpu.Pass.EYES);
    }

    public static RenderType cutout(ResourceLocation tex) {
        return GeoGpu.pass(RenderType.entityCutout(tex), GeoGpu.Pass.CUTOUT);
    }
}
