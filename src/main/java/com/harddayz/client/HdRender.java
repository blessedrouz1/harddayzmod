package com.harddayz.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.harddayz.client.model.GeoGpu;

/** Standard entity materials, also recognized by Oculus shader packs. */
public final class HdRender {

    private HdRender() {
    }

    /**
     * Model textures are alpha 0 or 255, so the cutout material looks the same as the translucent one.
     * Shader packs (Oculus/Iris) draw translucent entities see-through, so use cutout whenever such a mod is loaded.
     */
    public static RenderType body(ResourceLocation tex) {
        if (GeoGpu.shaderPackMod()) {
            return GeoGpu.pass(RenderType.entityCutout(tex), GeoGpu.Pass.CUTOUT);
        }
        return GeoGpu.pass(RenderType.entityTranslucent(tex), GeoGpu.Pass.TRANSLUCENT);
    }

    public static RenderType eyes(ResourceLocation tex) {
        return GeoGpu.pass(RenderType.eyes(tex), GeoGpu.Pass.EYES);
    }

    public static RenderType cutout(ResourceLocation tex) {
        return GeoGpu.pass(RenderType.entityCutout(tex), GeoGpu.Pass.CUTOUT);
    }
}
