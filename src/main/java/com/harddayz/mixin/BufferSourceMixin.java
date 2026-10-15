package com.harddayz.mixin;

import com.harddayz.client.model.GeoGpu;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiBufferSource.BufferSource.class)
public abstract class BufferSourceMixin {
    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At("RETURN"))
    private void harddayz$drawStaticMeshes(RenderType type, CallbackInfo ci) {
        GeoGpu.flush((MultiBufferSource.BufferSource) (Object) this, type);
    }
}
