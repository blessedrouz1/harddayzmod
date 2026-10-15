package com.harddayz.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Модель из дерева ModelPart с анимацией по состоянию, как Model&lt;S&gt; в новых версиях:
 * перед каждой анимацией все кости возвращаются в исходную позу.
 */
public class HdModel<S> {
    public final ModelPart root;
    private final ModelPart[] animatedParts;

    public HdModel(ModelPart root) {
        this.root = root;
        animatedParts = com.harddayz.client.model.GeoGpu.animatedParts(root);
    }

    public void setupAnim(S s) {
        for (ModelPart part : animatedParts) part.resetPose();
    }

    public void render(S s, PoseStack pose, MultiBufferSource buffers, RenderType type, int light) {
        setupAnim(s);
        draw(pose, buffers, type, light);
    }

    /** Нарисовать без анимации (поза уже выставлена). */
    public void draw(PoseStack pose, MultiBufferSource buffers, RenderType type, int light) {
        if (!com.harddayz.client.model.GeoGpu.render(root, pose, buffers, type, light, OverlayTexture.NO_OVERLAY)) {
            com.harddayz.client.model.GeoGpu.renderVertices(root, pose, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY);
        }
    }
}
