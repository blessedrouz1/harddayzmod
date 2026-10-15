package com.harddayz.client;

import java.util.EnumMap;
import java.util.Map;

import com.harddayz.client.model.HdModels;
import com.harddayz.entity.Missile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Ракета в полёте: корпус по направлению скорости и мерцающий факел двигателя. */
public class MissileRenderer extends EntityRenderer<Missile> {
    public static class State {
        int lightCoords;
        Missile.Kind kind = Missile.Kind.GRAD;
        float yaw;
        float pitch;
        float time;
        int look;
    }

    static class Simple<S> extends HdModel<S> {
        Simple(ModelPart root) {
            super(root);
        }
    }

    private final Map<Missile.Kind, Simple<State>> bodies = new EnumMap<>(Missile.Kind.class);
    private final Simple<State> flame;
    /** Ракета «Искандера» из модели друга — та же, что лежит на пусковой. */
    private final HdModel<State> iskanderMissile;
    /** Свои модели ракет (geo): нос в -Z, центр в нуле. */
    private final java.util.Map<String, HdModel<State>> geoBodies = new java.util.HashMap<>();

    private HdModel<State> geoBody(String n) {
        return geoBodies.computeIfAbsent(n, k -> new HdModel<>(com.harddayz.client.model.GeoModel.get(k, null).root) {
        });
    }

    private static String lookName(State s) {
        return switch (s.look) {
            case 1 -> "m_gmlrs";
            case 2 -> "m_s400";
            case 3 -> "m_patriot";
            case 4 -> "m_sam";
            case 5 -> "m_javelin";
            case 6 -> "m_rpg";
            case 7 -> "m_mortar";
            case 8 -> "m_s8";
            default -> switch (s.kind) {
                case GRAD -> "m_grad";
                case SAM -> "m_sam";
                default -> null;
            };
        };
    }
    private final float[] iskanderOffset;
    private static final net.minecraft.resources.ResourceLocation ISKANDER_TEX = new net.minecraft.resources.ResourceLocation(
            com.harddayz.HardDayz.MODID, "textures/entity/iskander.png");

    public MissileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        bodies.put(Missile.Kind.GRAD, new Simple<>(ctx.bakeLayer(HdModels.ROCKET_GRAD)));
        bodies.put(Missile.Kind.BALLISTIC, new Simple<>(ctx.bakeLayer(HdModels.ROCKET_BALLISTIC)));
        bodies.put(Missile.Kind.SAM, new Simple<>(ctx.bakeLayer(HdModels.ROCKET_SAM)));
        bodies.put(Missile.Kind.BOMB, new Simple<>(ctx.bakeLayer(HdModels.BOMB)));
        flame = new Simple<>(ctx.bakeLayer(HdModels.FLAME));
        com.harddayz.client.model.GeoModel isk = com.harddayz.client.model.GeoModel.get("iskander",
                new com.harddayz.client.model.GeoModel.Marks("cab", com.harddayz.client.model.GeoModel.Mode.SIDES_TOP));
        ModelPart m = isk.parts.get("missile");
        iskanderMissile = new HdModel<>(m) {
            @Override
            public void setupAnim(State s) {
                super.setupAnim(s);
                m.visible = true; // на пусковой эту же кость прячут, когда ракета ушла
            }
        };
        float[] b = isk.subtreeBox("missile");
        float[] e = isk.pivots.get(isk.parents.get("missile"));
        iskanderOffset = new float[] {(e[0] - (b[0] + b[3]) / 2) / 16f, (e[1] - (b[1] + b[4]) / 2) / 16f, (e[2] - (b[2] + b[5]) / 2) / 16f};
    }

    @Override
    public boolean shouldRender(Missile e, Frustum frustum, double x, double y, double z) {
        return e.shouldRender(x, y, z) && frustum.isVisible(e.getBoundingBox().inflate(8));
    }

    public State createRenderState() {
        return new State();
    }

    private void extractRenderState(Missile e, State s, float pt) {
        s.kind = e.kind();
        s.yaw = net.minecraft.util.Mth.rotLerp(pt, e.yRotO, e.getYRot());
        s.pitch = net.minecraft.util.Mth.lerp(pt, e.xRotO, e.getXRot());
        s.time = e.tickCount + pt;
        s.look = e.look();
    }

    @Override
    public ResourceLocation getTextureLocation(Missile e) {
        return LauncherRenderer.PALETTE;
    }

    @Override
    public void render(Missile e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        State s = createRenderState();
        extractRenderState(e, s, pt);
        s.lightCoords = light;
        draw(s, pose, buffers);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    private void draw(State s, PoseStack pose, MultiBufferSource buffers) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(s.yaw));
        pose.mulPose(Axis.XP.rotationDegrees(-s.pitch));
        String gn = lookName(s);
        if (gn != null) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180));
            geoBody(gn).render(s, pose, buffers, RenderType.entityTranslucent(new net.minecraft.resources.ResourceLocation(
                    com.harddayz.HardDayz.MODID, "textures/entity/" + gn + ".png")), s.lightCoords);
            pose.popPose();
        } else if (s.kind == Missile.Kind.BALLISTIC) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180)); // у модели друга нос смотрит в -Z
            pose.translate(iskanderOffset[0], iskanderOffset[1], iskanderOffset[2]);
            iskanderMissile.render(s, pose, buffers, RenderType.entityTranslucent(ISKANDER_TEX), s.lightCoords);
            pose.popPose();
        } else {
            bodies.get(s.kind).render(s, pose, buffers, RenderType.entityCutout(LauncherRenderer.PALETTE), s.lightCoords);
        }
        if (s.kind != Missile.Kind.BOMB) {
            float tail = switch (s.kind) {
                case BALLISTIC -> -58 / 16f;
                case SAM -> -24 / 16f;
                default -> -23 / 16f;
            };
            float size = s.kind == Missile.Kind.BALLISTIC ? 2.6f : 1.0f;
            float flicker = 0.85f + 0.3f * (float) Math.abs(Math.sin(s.time * 2.7f));
            pose.translate(0, 0, tail);
            pose.scale(size, size, size * flicker * 1.6f);
            // факел — частицами (Missile.trail), модель пламени больше не рисуем
        }
        pose.popPose();
    }
}
