package com.harddayz.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.harddayz.HardDayz;
import com.harddayz.client.model.GeoModel;
import com.harddayz.client.model.HdModels;
import com.harddayz.entity.Launcher;
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

/** Пусковые: колёса, руль, домкраты, башня, пакет, стрела с цилиндрами, радар. */
public class LauncherRenderer extends EntityRenderer<Launcher, LauncherRenderer.State> {
    public static final Identifier PALETTE = Identifier.fromNamespaceAndPath(HardDayz.MODID, "textures/entity/palette.png");

    public static class State extends EntityRenderState {
        Launcher.Kind kind = Launcher.Kind.GRAD;
        float bodyYaw;
        float turret;
        float elevation;
        float erect;
        float outrig;
        float wheelSpin;
        float steer;
        int ammo;
        float time;
        int flash;
        int faction = -1;
        float hp = 1;
        boolean inside;
        int[] drones = new int[0];
    }

    /** Модели друга (geo.json): кости с теми же именами, что и в HdModels. */
    static class GeoLauncherModel extends Model<State> {
        final GeoModel geo;
        final String name;
        final boolean variants;

        GeoLauncherModel(String name, GeoModel.Marks marks, boolean variants) {
            this(GeoModel.get(name, marks), name, variants);
        }

        private GeoLauncherModel(GeoModel geo, String name, boolean variants) {
            super(geo.root, RenderTypes::entityCutout);
            this.geo = geo;
            this.name = name;
            this.variants = variants;
        }

        private void each(String n, java.util.function.Consumer<ModelPart> f) {
            ModelPart p = geo.parts.get(n);
            if (p != null) {
                f.accept(p);
            }
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            float drop = s.kind == Launcher.Kind.RAMP ? 2f : 8.5f;
            for (int i = 0; i < 4; i++) {
                for (String side : new String[] {"r", "l"}) {
                    each("wheel_" + i + side, b -> b.xRot -= s.wheelSpin);
                    each("steer_" + i + side, b -> b.yRot -= (float) Math.toRadians(s.steer));
                }
                each("jack" + i, b -> b.y -= s.outrig * drop);
            }
            each("turret", b -> b.yRot -= (float) Math.toRadians(s.turret));
            each("rack", b -> {
                b.xRot += (float) Math.toRadians(s.elevation);
                b.z += s.flash * 0.35f;
            });
            each("radar", b -> b.yRot += s.time * 0.15f);
            for (int i = 0; i < 40; i++) {
                boolean shown = i < s.ammo && !otherDrone(s, i) && s.kind != Launcher.Kind.HIMARS;
                each("tip" + i, b -> b.visible = shown);
            }
            each("missile", b -> b.visible = s.ammo > 0);
            // стрела «Искандера»: шарнир (0, 28, 44), цилиндры толкают точку в 34 px по стреле
            double a = Math.toRadians(s.erect * 90);
            each("erector", b -> b.xRot += (float) a);
            for (String n : new String[] {"piston_l", "piston_r"}) {
                each(n, b -> {
                    double dy0 = 28.0 - 30.0;
                    double dz0 = 44.0 - 34.0 - 30.0;
                    double dy = 28.0 + Math.sin(a) * 34.0 - 30.0;
                    double dz = 44.0 - Math.cos(a) * 34.0 - 30.0;
                    b.xRot += (float) (Math.atan2(dy, -dz) - Math.atan2(dy0, -dz0));
                    b.zScale = (float) (Math.hypot(dy, dz) / Math.hypot(dy0, dz0));
                });
            }
        }

        Identifier texture(State s) {
            return texture0(s);
        }

        private Identifier texture0(State s) {
            String t = name;
            if (variants) {
                t += s.hp < 0.2f ? "_burnt" : s.hp < 0.5f ? "_damaged" : "";
            }
            return Identifier.fromNamespaceAndPath(HardDayz.MODID, "textures/entity/" + t + ".png");
        }

        Identifier glow(State s) {
            String t = texture(s).getPath();
            return Identifier.fromNamespaceAndPath(HardDayz.MODID, t.replace(".png", "_glowmask.png"));
        }
    }

    /** На направляющей i стоит не обычный «Шахед» — рисуем его собственную модель. */
    static boolean otherDrone(State s, int i) {
        return i < s.drones.length && s.drones[i] != com.harddayz.entity.Drone.Kind.SHAHED.ordinal();
    }

    private record Payload(Model.Simple model, GeoModel geo, Identifier texture) {
    }

    private static final Map<Integer, Payload> PAYLOADS = new java.util.HashMap<>();

    private static Payload payload(int kind) {
        return PAYLOADS.computeIfAbsent(kind, k -> {
            String n = switch (com.harddayz.entity.Drone.Kind.values()[k]) {
                case SHAHED_238 -> "shahed238";
                case SHAHED_131 -> "geran2";
                case GERBERA -> "gerbera";
                default -> "shahed136";
            };
            GeoModel g = GeoModel.get(n, new GeoModel.Marks("wing", GeoModel.Mode.WINGS));
            return new Payload(new Model.Simple(g.root, RenderTypes::entityTranslucent), g,
                    Identifier.fromNamespaceAndPath(HardDayz.MODID, "textures/entity/" + n + ".png"));
        });
    }

    private void submitPayloads(GeoLauncherModel g, State s, PoseStack pose, SubmitNodeCollector collector) {
        g.setupAnim(s);
        for (int i = 0; i < Math.min(s.ammo, s.drones.length); i++) {
            String tip = "tip" + i;
            float[] box = g.geo.boxes.get(tip);
            String parent = g.geo.parents.get(tip);
            if (!otherDrone(s, i) || box == null || parent == null) {
                continue;
            }
            Payload p = payload(s.drones[i]);
            float[] db = p.geo().bounds();
            float[] pv = g.geo.pivots.get(parent);
            float k = (box[5] - box[2]) / Math.max(1, db[5] - db[2]); // по длине направляющей
            pose.pushPose();
            g.geo.transformTo(pose, parent);
            pose.translate(((box[0] + box[3]) / 2 - pv[0]) / 16f, ((box[1] + box[4]) / 2 - pv[1]) / 16f, ((box[2] + box[5]) / 2 - pv[2]) / 16f);
            pose.scale(k, k, k);
            pose.translate(-(db[0] + db[3]) / 32f, -(db[1] + db[4]) / 32f, -(db[2] + db[5]) / 32f);
            collector.submitModel(p.model(), net.minecraft.util.Unit.INSTANCE, pose, RenderTypes.entityTranslucent(p.texture()), s.lightCoords,
                    OverlayTexture.NO_OVERLAY, s.outlineColor);
            pose.popPose();
        }
    }

    public static final Identifier MARKS = Identifier.fromNamespaceAndPath(HardDayz.MODID, "textures/entity/marks.png");
    private static final java.util.Map<ModelPart, Model.Simple> MARK_MODELS = new java.util.IdentityHashMap<>();

    /** Опознавательный знак фракции поверх модели. */
    public static void submitMarks(GeoModel geo, int faction, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (geo.marks == null || faction < 0 || faction >= geo.marks.length) {
            return;
        }
        Model.Simple m = MARK_MODELS.computeIfAbsent(geo.marks[faction], p -> new Model.Simple(p, RenderTypes::entityCutout));
        collector.submitModel(m, net.minecraft.util.Unit.INSTANCE, pose, RenderTypes.entityCutout(MARKS), light, OverlayTexture.NO_OVERLAY, 0);
    }

    static class LauncherModel extends Model<State> {
        private final ModelPart turret;
        private final ModelPart rack;
        private final ModelPart erector;
        private final ModelPart missile;
        private final ModelPart radar;
        private final ModelPart pistonL;
        private final ModelPart pistonR;
        private final List<ModelPart> wheels = new ArrayList<>();
        private final List<ModelPart> steers = new ArrayList<>();
        private final List<ModelPart> jacks = new ArrayList<>();
        private final List<ModelPart> tips = new ArrayList<>();

        LauncherModel(ModelPart root) {
            super(root, RenderTypes::entityCutout);
            turret = child(root, "turret");
            rack = turret != null ? child(turret, "rack") : null;
            erector = child(root, "erector");
            missile = erector != null ? child(erector, "missile") : null;
            radar = turret != null ? child(turret, "radar") : null;
            pistonL = child(root, "piston_l");
            pistonR = child(root, "piston_r");
            for (int i = 0; i < 4; i++) {
                for (String s : new String[] {"r", "l"}) {
                    String n = i + s;
                    ModelPart st = child(root, "steer_" + n);
                    if (st != null) {
                        steers.add(st);
                        wheels.add(st.getChild("wheel_" + n));
                    } else if (root.hasChild("wheel_" + n)) {
                        wheels.add(root.getChild("wheel_" + n));
                    }
                }
                ModelPart j = child(root, "jack" + i);
                if (j != null) {
                    jacks.add(j);
                }
            }
            for (int i = 0; rack != null && rack.hasChild("tip" + i); i++) {
                tips.add(rack.getChild("tip" + i));
            }
        }

        private static ModelPart child(ModelPart p, String n) {
            return p.hasChild(n) ? p.getChild(n) : null;
        }

        @Override
        public void setupAnim(State s) {
            super.setupAnim(s);
            for (ModelPart w : wheels) {
                w.xRot = s.wheelSpin;
            }
            for (ModelPart st : steers) {
                st.yRot = (float) Math.toRadians(s.steer);
            }
            for (ModelPart j : jacks) {
                j.y += s.outrig * 8.5f;
            }
            if (turret != null) {
                turret.yRot = (float) Math.toRadians(s.turret);
            }
            if (rack != null) {
                rack.xRot = (float) Math.toRadians(s.elevation);
                if (s.flash > 0) {
                    rack.z -= s.flash * 0.35f; // отдача
                }
            }
            if (erector != null) {
                float a = (float) Math.toRadians(s.erect * 90);
                erector.xRot = a;
                // цилиндры тянутся от корпуса к середине стрелы
                for (ModelPart p : new ModelPart[] {pistonL, pistonR}) {
                    if (p == null) {
                        continue;
                    }
                    double ey = erector.y - Math.sin(a) * 34;
                    double ez = erector.z + Math.cos(a) * 34;
                    double dy = ey - p.y;
                    double dz = ez - p.z;
                    p.xRot = (float) Math.atan2(-dy, dz);
                    p.zScale = (float) (Math.sqrt(dy * dy + dz * dz) / 30.0);
                }
            }
            if (missile != null) {
                missile.visible = s.ammo > 0;
            }
            if (radar != null) {
                radar.yRot = s.time * 0.15f;
            }
            for (int i = 0; i < tips.size(); i++) {
                tips.get(i).visible = i < s.ammo;
            }
        }
    }

    private final Map<Launcher.Kind, LauncherModel> models = new EnumMap<>(Launcher.Kind.class);
    private final Map<Launcher.Kind, GeoLauncherModel> geo = new EnumMap<>(Launcher.Kind.class);

    public LauncherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        models.put(Launcher.Kind.GRAD, new LauncherModel(ctx.bakeLayer(HdModels.GRAD)));
        models.put(Launcher.Kind.ISKANDER, new LauncherModel(ctx.bakeLayer(HdModels.ISKANDER)));
        models.put(Launcher.Kind.SAM, new LauncherModel(ctx.bakeLayer(HdModels.SAM)));
        models.put(Launcher.Kind.SHAHED, new LauncherModel(ctx.bakeLayer(HdModels.SHAHED_LAUNCHER)));
        models.put(Launcher.Kind.RAMP, new LauncherModel(ctx.bakeLayer(HdModels.RAMP)));
        models.put(Launcher.Kind.GRAD_P, new LauncherModel(ctx.bakeLayer(HdModels.GRAD_P)));
        models.put(Launcher.Kind.SAM_POST, new LauncherModel(ctx.bakeLayer(HdModels.SAM_POST)));
        models.put(Launcher.Kind.EW, new LauncherModel(ctx.bakeLayer(HdModels.EW)));
        models.put(Launcher.Kind.MG, new LauncherModel(ctx.bakeLayer(HdModels.MG)));
        models.put(Launcher.Kind.RELAY, new LauncherModel(ctx.bakeLayer(HdModels.EW)));
        models.put(Launcher.Kind.PICKUP_MG, new LauncherModel(ctx.bakeLayer(HdModels.PICKUP_MG)));
        models.put(Launcher.Kind.PICKUP_SAM, new LauncherModel(ctx.bakeLayer(HdModels.PICKUP_SAM)));
        models.put(Launcher.Kind.BMP2, new LauncherModel(ctx.bakeLayer(HdModels.BMP2)));
        models.put(Launcher.Kind.BRADLEY, new LauncherModel(ctx.bakeLayer(HdModels.BRADLEY)));
        models.put(Launcher.Kind.RADAR, new LauncherModel(ctx.bakeLayer(HdModels.RADAR)));
        models.put(Launcher.Kind.SAM_MANUAL, new LauncherModel(ctx.bakeLayer(HdModels.SAM_MANUAL)));
        GeoModel.Marks cab = new GeoModel.Marks("cab", GeoModel.Mode.SIDES_TOP);
        geo.put(Launcher.Kind.GRAD, new GeoLauncherModel("grad", cab, true));
        geo.put(Launcher.Kind.ISKANDER, new GeoLauncherModel("iskander", cab, true));
        geo.put(Launcher.Kind.SAM, new GeoLauncherModel("sam", cab, true));
        GeoModel.Marks hull = new GeoModel.Marks("hull", GeoModel.Mode.SIDES_TOP);
        GeoModel.Marks base = new GeoModel.Marks("base", GeoModel.Mode.TOP);
        geo.put(Launcher.Kind.S400, new GeoLauncherModel("s400", cab, true));
        geo.put(Launcher.Kind.TOS, new GeoLauncherModel("tos", hull, true));
        geo.put(Launcher.Kind.HIMARS, new GeoLauncherModel("himars", cab, true));
        geo.put(Launcher.Kind.PATRIOT, new GeoLauncherModel("patriot", cab, true));
        geo.put(Launcher.Kind.NASAMS, new GeoLauncherModel("nasams", cab, true));
        geo.put(Launcher.Kind.PICKUP_MG, new GeoLauncherModel("pickup_mg", cab, true));
        geo.put(Launcher.Kind.PICKUP_SAM, new GeoLauncherModel("pickup_sam", cab, true));
        geo.put(Launcher.Kind.BMP2, new GeoLauncherModel("bmp2", hull, true));
        geo.put(Launcher.Kind.BRADLEY, new GeoLauncherModel("bradley", hull, true));
        geo.put(Launcher.Kind.T72, new GeoLauncherModel("t72", hull, true));
        geo.put(Launcher.Kind.ABRAMS, new GeoLauncherModel("abrams", hull, true));
        geo.put(Launcher.Kind.EW, new GeoLauncherModel("ew", cab, true));
        geo.put(Launcher.Kind.RADAR, new GeoLauncherModel("radar", cab, true));
        geo.put(Launcher.Kind.RELAY, new GeoLauncherModel("relay", base, true));
        geo.put(Launcher.Kind.GRAD_P, new GeoLauncherModel("grad_p", null, true));
        geo.put(Launcher.Kind.SAM_POST, new GeoLauncherModel("sam_post", base, true));
        geo.put(Launcher.Kind.SAM_MANUAL, new GeoLauncherModel("sam_manual", base, true));
        geo.put(Launcher.Kind.MG, new GeoLauncherModel("mg", null, true));
        geo.put(Launcher.Kind.SHAHED, new GeoLauncherModel("shahed_launcher", cab, true));
        geo.put(Launcher.Kind.RAMP, new GeoLauncherModel("shahed_ground", new GeoModel.Marks("base", GeoModel.Mode.SIDES), true));
        this.shadowRadius = 2.2f;
    }

    @Override
    protected AABB getBoundingBoxForCulling(Launcher e, float pt) {
        return e.getBoundingBox().inflate(6, 8, 6);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Launcher e, State s, float pt) {
        super.extractRenderState(e, s, pt);
        s.kind = e.kind();
        s.bodyYaw = e.getYRot(pt);
        s.turret = e.turret(pt);
        s.elevation = e.elevation(pt);
        s.erect = e.erect(pt);
        s.outrig = e.outrig(pt);
        s.wheelSpin = e.prevWheelSpin + (e.wheelSpin - e.prevWheelSpin) * pt;
        s.steer = e.prevSteer + (e.steer - e.prevSteer) * pt;
        s.ammo = e.ammo();
        s.time = e.tickCount + pt;
        s.flash = e.flash();
        s.faction = e.faction();
        s.hp = e.health() / Math.max(1f, e.kind().health);
        s.drones = e.droneKinds();
        var mc = net.minecraft.client.Minecraft.getInstance();
        s.inside = mc.player != null && mc.player.getVehicle() == e && mc.options.getCameraType().isFirstPerson();
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        GeoLauncherModel g = geo.get(s.kind);
        if (s.inside) {
            super.submit(s, pose, collector, camera); // сидим внутри от первого лица — кабина не закрывает обзор
            return;
        }
        if (g != null) {
            pose.pushPose();
            pose.rotate(Axis.YP.rotationDegrees(180 - s.bodyYaw));
            int tint = -1;
            collector.submitModel(g, s, pose, RenderTypes.entityTranslucent(g.texture(s)), s.lightCoords, OverlayTexture.NO_OVERLAY, tint, null,
                    s.outlineColor);
            submitPayloads(g, s, pose, collector);
            collector.submitModel(g, s, pose, RenderTypes.eyes(g.glow(s)), s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            submitMarks(g.geo, s.faction, pose, collector, s.lightCoords);
            pose.popPose();
            super.submit(s, pose, collector, camera);
            return;
        }
        pose.pushPose();
        pose.rotate(Axis.YP.rotationDegrees(-s.bodyYaw));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501f, 0);
        collector.submitModel(models.get(s.kind), s, pose, RenderTypes.entityTranslucent(PALETTE), s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
        pose.popPose();
        super.submit(s, pose, collector, camera);
    }
}
