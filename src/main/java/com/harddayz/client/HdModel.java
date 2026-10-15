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

    public HdModel(ModelPart root) {
        this.root = root;
    }

    public void setupAnim(S s) {
        root.getAllParts().forEach(ModelPart::resetPose);
    }

    public void render(S s, PoseStack pose, MultiBufferSource buffers, RenderType type, int light) {
        setupAnim(s);
        root.render(pose, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY);
    }

    /** Нарисовать без анимации (поза уже выставлена). */
    public void draw(PoseStack pose, MultiBufferSource buffers, RenderType type, int light) {
        root.render(pose, buffers.getBuffer(type), light, OverlayTexture.NO_OVERLAY);
    }
}
