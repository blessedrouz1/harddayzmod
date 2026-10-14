package com.harddayz.client;

import com.harddayz.HardDayz;
import com.harddayz.entity.Mine;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Мины: противопехотная (маленькая, с нажимной крышкой) и противотанковая (большой диск со взрывателем). */
public class MineRenderer extends EntityRenderer<Mine, MineRenderer.State> {
    public static final ModelLayerLocation AP = new ModelLayerLocation(Identifier.fromNamespaceAndPath(HardDayz.MODID, "mine_ap"), "main");
    public static final ModelLayerLocation AT = new ModelLayerLocation(Identifier.fromNamespaceAndPath(HardDayz.MODID, "mine_at"), "main");

    public static class State extends EntityRenderState {
        Mine.Kind kind = Mine.Kind.AP;
        float yaw;
    }

    static class M extends Model<State> {
        M(ModelPart root) {
            super(root, RenderTypes::entityCutout);
        }
    }

    // цвета палитры (см. HdModels): 0 олива, 1 тёмная олива, 10 чёрный, 13 тёмная сталь, 7 красный
    private static CubeListBuilder box(int color, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs((color % 4) * 256, (color / 4) * 256).addBox(x, y, z, w, h, d);
    }

    public static LayerDefinition ap() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("a", box(0, -2.5f, 22.4f, -1.8f, 5, 1.6f, 3.6f), PartPose.ZERO);
        root.addOrReplaceChild("b", box(0, -1.8f, 22.4f, -2.5f, 3.6f, 1.6f, 5), PartPose.ZERO);
        root.addOrReplaceChild("cap", box(10, -1.4f, 21.8f, -1.4f, 2.8f, 0.6f, 2.8f), PartPose.ZERO);
        root.addOrReplaceChild("pin", box(7, 2.4f, 22.6f, -0.3f, 1.2f, 0.6f, 0.6f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    public static LayerDefinition at() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("a", box(1, -5, 21.5f, -3.4f, 10, 2.5f, 6.8f), PartPose.ZERO);
        root.addOrReplaceChild("b", box(1, -3.4f, 21.5f, -5, 6.8f, 2.5f, 10), PartPose.ZERO);
        root.addOrReplaceChild("c", box(1, -4.4f, 21.5f, -4.4f, 8.8f, 2.5f, 8.8f), PartPose.ZERO);
        root.addOrReplaceChild("fuse", box(13, -1.5f, 20.5f, -1.5f, 3, 1, 3), PartPose.ZERO);
        root.addOrReplaceChild("ring", box(10, -4.6f, 22.8f, -4.6f, 9.2f, 0.4f, 9.2f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    private final M ap;
    private final M at;

    public MineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ap = new M(ctx.bakeLayer(AP));
        at = new M(ctx.bakeLayer(AT));
        this.shadowRadius = 0.2f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Mine e, State s, float pt) {
        super.extractRenderState(e, s, pt);
        s.kind = e.kind();
        s.yaw = e.getYRot();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.rotate(Axis.YP.rotationDegrees(s.yaw));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501f, 0);
        collector.submitModel(s.kind == Mine.Kind.AT ? at : ap, s, pose, RenderTypes.entityCutout(LauncherRenderer.PALETTE), s.lightCoords,
                OverlayTexture.NO_OVERLAY, s.outlineColor);
        pose.popPose();
        super.submit(s, pose, collector, camera);
    }
}
