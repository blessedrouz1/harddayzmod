package com.harddayz.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.harddayz.client.model.GeoModel;
import com.harddayz.client.model.HdModels;
import com.harddayz.entity.Drone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/** Дроны: «Шахед» с толкающим винтом, квадрокоптеры с вращающимися винтами и наклоном. */
public class DroneRenderer extends EntityRenderer<Drone, DroneRenderer.State> {
    public static class State extends EntityRenderState {
        Drone.Kind kind = Drone.Kind.SHAHED;
        float yaw;
        float pitch;
        float rotor;
        int grenades;
        int faction = -1;
    }

    /** Модели друга (geo.json). */
    static class GeoDroneModel extends Model<State> {
        final GeoModel geo;
        final Identifier texture;

        GeoDroneModel(String name, GeoModel.Marks marks) {
            this(GeoModel.get(name, marks), name);
        }

        private GeoDroneModel(GeoModel geo, String name) {
            super(geo.root, RenderTypes::entityCutout);
            this.geo = geo;
            this.texture = Identifier.fromNamespaceAndPath(com.harddayz.HardDayz.MODID, "textures/entity/" + name + ".png");
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            ModelPart prop = geo.parts.get("prop");
            if (prop != null) {
                prop.zRot += s.rotor;
            }
            for (int i = 0; i < 4; i++) {
                ModelPart p = geo.parts.get("prop" + i);
                if (p != null) {
                    p.yRot += s.rotor * 3f * (i == 0 || i == 3 ? 1 : -1);
                }
            }
            ModelPart gimbal = geo.parts.get("gimbal");
            if (gimbal != null) {
                gimbal.xRot += (float) Math.toRadians(-s.pitch * 0.7f);
            }
            for (int i = 0; i < 3; i++) {
                ModelPart g = geo.parts.get("grenade" + i);
                if (g != null) {
                    g.visible = i < s.grenades;
                }
            }
        }
    }

    static class DroneModel extends Model<State> {
        private final List<ModelPart> props = new ArrayList<>();
        private final ModelPart gimbal;

        DroneModel(ModelPart root) {
            super(root, RenderTypes::entityCutout);
            if (root.hasChild("prop")) {
                props.add(root.getChild("prop"));
            }
            for (int i = 0; root.hasChild("prop" + i); i++) {
                props.add(root.getChild("prop" + i));
            }
            gimbal = root.hasChild("gimbal") ? root.getChild("gimbal") : null;
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            for (int i = 0; i < props.size(); i++) {
                ModelPart p = props.get(i);
                if (s.kind.autonomous) {
                    p.zRot = s.rotor;
                } else {
                    p.yRot = s.rotor * (i % 2 == 0 ? 1 : -1);
                }
            }
            if (gimbal != null) {
                gimbal.xRot = (float) Math.toRadians(s.pitch);
            }
        }
    }

    private final Map<Drone.Kind, DroneModel> models = new EnumMap<>(Drone.Kind.class);
    private final Map<Drone.Kind, GeoDroneModel> geo = new EnumMap<>(Drone.Kind.class);

    public DroneRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        models.put(Drone.Kind.SHAHED, new DroneModel(ctx.bakeLayer(HdModels.SHAHED)));
        models.put(Drone.Kind.FPV, new DroneModel(ctx.bakeLayer(HdModels.FPV)));
        models.put(Drone.Kind.RECON, new DroneModel(ctx.bakeLayer(HdModels.RECON)));
        models.put(Drone.Kind.SHAHED_238, new DroneModel(ctx.bakeLayer(HdModels.SHAHED_238)));
        models.put(Drone.Kind.SHAHED_131, new DroneModel(ctx.bakeLayer(HdModels.SHAHED)));
        models.put(Drone.Kind.GERBERA, new DroneModel(ctx.bakeLayer(HdModels.GERBERA)));
        GeoModel.Marks wings = new GeoModel.Marks("wing", GeoModel.Mode.WINGS);
        GeoModel.Marks frame = new GeoModel.Marks("frame", GeoModel.Mode.TOP);
        geo.put(Drone.Kind.SHAHED, new GeoDroneModel("shahed136", wings));
        geo.put(Drone.Kind.SHAHED_131, new GeoDroneModel("geran2", wings));
        geo.put(Drone.Kind.SHAHED_238, new GeoDroneModel("shahed238", wings));
        geo.put(Drone.Kind.GERBERA, new GeoDroneModel("gerbera", wings));
        geo.put(Drone.Kind.FPV, new GeoDroneModel("fpv", frame));
        geo.put(Drone.Kind.INTERCEPTOR, new GeoDroneModel("interceptor", frame));
        geo.put(Drone.Kind.LANCET, new GeoDroneModel("lancet", wings));
        geo.put(Drone.Kind.WING_RECON, new GeoDroneModel("wing_recon", wings));
        geo.put(Drone.Kind.RECON, new GeoDroneModel("recon", frame));
        this.shadowRadius = 0.4f;
    }

    @Override
    protected AABB getBoundingBoxForCulling(Drone e, float pt) {
        return e.getBoundingBox().inflate(2.5);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Drone e, State s, float pt) {
        super.extractRenderState(e, s, pt);
        s.kind = e.kind();
        s.yaw = e.getYRot(pt);
        s.pitch = e.getXRot(pt);
        s.grenades = e.grenades();
        s.faction = e.faction();
        s.rotor = e.rotor + pt * (e.kind().autonomous ? 2.2f : 1.6f);
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        GeoDroneModel g = geo.get(s.kind);
        if (g != null) {
            pose.pushPose();
            pose.translate(0, 0.25, 0);
            pose.rotate(Axis.YP.rotationDegrees(180 - s.yaw));
            // нос смотрит в -Z: положительный наклон по X поднимает его
            float pitch = s.kind.autonomous ? s.pitch : Math.max(-25, Math.min(25, s.pitch * 0.3f));
            pose.rotate(Axis.XP.rotationDegrees(-pitch));
            float k = switch (s.kind) {
                case SHAHED_131 -> 0.72f;
                default -> 1f;
            };
            pose.scale(k, k, k);
            int tint = -1;
            collector.submitModel(g, s, pose, RenderTypes.entityTranslucent(g.texture), s.lightCoords, OverlayTexture.NO_OVERLAY, tint, null,
                    s.outlineColor);
            LauncherRenderer.submitMarks(g.geo, s.faction, pose, collector, s.lightCoords);
            pose.popPose();
            super.submit(s, pose, collector, camera);
            return;
        }
        pose.pushPose();
        pose.translate(0, 0.25, 0);
        pose.rotate(Axis.YP.rotationDegrees(-s.yaw));
        if (s.kind.autonomous) {
            pose.rotate(Axis.XP.rotationDegrees(s.pitch));
            if (s.kind == Drone.Kind.SHAHED_131) {
                pose.scale(0.72f, 0.72f, 0.72f);
            }
        } else {
            // квадрокоптер наклоняется носом при движении, а не по взгляду камеры
            pose.rotate(Axis.XP.rotationDegrees(Math.max(-25, Math.min(25, s.pitch * 0.3f))));
            float scale = s.kind == Drone.Kind.FPV ? 1.8f : 1.6f;
            pose.scale(scale, scale, scale);
        }
        collector.submitModel(models.get(s.kind), s, pose, RenderTypes.entityCutout(LauncherRenderer.PALETTE), s.lightCoords,
                OverlayTexture.NO_OVERLAY, s.outlineColor);
        pose.popPose();
        super.submit(s, pose, collector, camera);
    }
}
