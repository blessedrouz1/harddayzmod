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

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Пусковые: колёса, руль, домкраты, башня, пакет, стрела с цилиндрами, радар. */
public class LauncherRenderer extends EntityRenderer<Launcher> {
    public static final ResourceLocation PALETTE = new net.minecraft.resources.ResourceLocation(HardDayz.MODID, "textures/entity/palette.png");

    public static class State {
        int lightCoords;
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
        float rotor;
        float tilt;
        /** Сколько тиков назад отлетела крышка контейнера i; -1 — крышка на месте. */
        float[] capAge = new float[8];
        /** Тики вскрытия очередной трубы (0 — закрыта), с долей тика. */
        float arm;
        Launcher launcher;
    }

    /** Модели друга (geo.json): кости с теми же именами, что и в HdModels. */
    static class GeoLauncherModel extends HdModel<State> {
        final GeoModel geo;
        final String name;
        final boolean variants;
        final com.harddayz.client.model.HydraulicLift[] lifts;

        GeoLauncherModel(String name, GeoModel.Marks marks, boolean variants) {
            this(GeoModel.get(name, marks), name, variants);
        }

        private GeoLauncherModel(GeoModel geo, String name, boolean variants) {
            super(geo.root);
            this.geo = geo;
            this.name = name;
            this.variants = variants;
            lifts = com.harddayz.client.model.HydraulicLift.create(geo);
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
            for (int i = 0; i < 4; i++) {
                for (String side : new String[] {"r", "l"}) {
                    each("wheel_" + i + side, b -> b.xRot -= s.wheelSpin);
                    each("steer_" + i + side, b -> b.yRot -= (float) Math.toRadians(s.steer));
                }
                String jack = "jack" + i;
                float[] foot = geo.boxes.get(jack + "_foot");
                float[] rod = geo.boxes.get(jack + "_extension");
                if (foot != null && rod != null) {
                    float drop = s.outrig * Math.max(0, foot[1]);
                    // The sleeve stays attached to the frame; only the foot and inner rod extend.
                    each(jack + "_foot", b -> b.y -= drop);
                    each(jack + "_extension", b -> b.yScale = 1 + drop / Math.max(0.01f, rod[4] - rod[1]));
                }
            }
            each("turret", b -> b.yRot -= (float) Math.toRadians(s.turret));
            each("rack", b -> {
                b.xRot += (float) Math.toRadians(s.elevation);
                if (s.kind.gunner() && !s.kind.lockOn()) b.z += s.flash * 0.35f;
            });
            each("radar", b -> b.yRot += s.time * 0.15f);
            each("rotor", b -> b.yRot += s.rotor);
            each("rotor2", b -> b.yRot -= s.rotor);
            each("trotor", b -> b.xRot += s.rotor * 1.6f);
            for (int i = 0; i < 40; i++) {
                boolean shown = i < s.ammo && !otherDrone(s, i) && s.kind != Launcher.Kind.HIMARS;
                each("tip" + i, b -> b.visible = shown);
            }
            each("missile", b -> b.visible = s.ammo > 0);
            each("erector", b -> b.xRot += (float) Math.toRadians(s.erect * 90));
            // Solve after rack/erector animation, in each cylinder's own parent frame.
            for (var lift : lifts) lift.animate();
            animateContainers(s);
        }

        /**
         * Контейнерные пусковые. Вскрытие трубы (s.arm, тики): сначала крышку выбивает газом, потом ракета выезжает
         * из трубы вдоль её оси, и только после этого пуск. Всё считается в системе пакета (rack), поэтому работает
         * при любом подъёме: вертикальный пуск С-400, наклонные «Пэтриот» и NASAMS.
         */
        private void animateContainers(State s) {
            for (var entry : geo.parts.entrySet()) {
                String n = entry.getKey();
                ModelPart part = entry.getValue();
                int at = n.lastIndexOf("cap");
                if (at >= 0 && at + 3 < n.length() && Character.isDigit(n.charAt(at + 3))) {
                    animateLid(n, part, n.charAt(at + 3) - '0', s);
                } else if (n.startsWith("msl") && n.length() == 4 && Character.isDigit(n.charAt(3))) {
                    int idx = n.charAt(3) - '0';
                    part.visible = idx < s.ammo;
                    float[] box = geo.boxes.get(n);
                    if (part.visible && idx == s.ammo - 1 && s.arm > 6 && box != null) {
                        float t = Math.min(1, (s.arm - 6) / 16f); // 6..22: выезжает на половину длины
                        part.z -= (box[5] - box[2]) * 0.5f * (t * t * (3 - 2 * t));
                    }
                }
            }
        }

        private static final float LID_GRAVITY = 0.045f; // блоков/тик²
        private static final float LID_GROUND = 0.03f;   // центр лежащей крышки над землёй (половина толщины)
        /** Через сколько тиков после вылета крышка исчезает с земли (последние 20 тиков — уменьшается). */
        private static final float LID_LIFE = 260;

        /**
         * Крышка: вылетает вдоль оси трубы, кувыркается, падает на землю, отскакивает, ложится плашмя, лежит и потом исчезает.
         * Движение считается в осях корпуса (земля y=0), а в кость пакета переводится обратной матрицей: так крышка остаётся
         * на земле, даже когда пакет уже опустился. Старт запоминается в Launcher.capStart.
         */
        private void animateLid(String bone, ModelPart cap, int idx, State s) {
            float age = idx < s.capAge.length ? s.capAge[idx] : -1;
            cap.xScale = cap.yScale = cap.zScale = 1;
            cap.visible = age < LID_LIFE;
            if (age < 0 || age >= LID_LIFE || s.launcher == null || idx >= s.launcher.capStart.length) {
                return;
            }
            String parent = geo.parents.get(bone);
            PoseStack ps = new PoseStack();
            if (parent != null) {
                geo.transformTo(ps, parent);
            }
            Matrix4f mp = new Matrix4f(ps.last().pose());
            float dir = idx % 2 == 0 ? 1 : -1;
            float[] st = s.launcher.capStart[idx];
            if (st == null) { // первый кадр после вылета: крышка ещё на месте в позе покоя, берём её положение и ось трубы
                Matrix4f d0 = new Matrix4f(mp).translate(cap.x / 16f, cap.y / 16f, cap.z / 16f);
                Vector3f p0 = d0.getTranslation(new Vector3f());
                Quaternionf q0 = d0.getUnnormalizedRotation(new Quaternionf()).normalize();
                Vector3f v = new Vector3f(dir * 0.10f, 0.04f, -0.42f);
                mp.transformDirection(v);
                st = new float[] {p0.x, p0.y, p0.z, q0.x, q0.y, q0.z, q0.w, v.x, v.y, v.z};
                s.launcher.capStart[idx] = st;
            }
            float g = LID_GRAVITY;
            float vy = st[8];
            float h = st[1] - LID_GROUND;
            float t1 = h <= 0 ? 0 : (vy + (float) Math.sqrt(vy * vy + 2 * g * h)) / g; // удар о землю
            Vector3f pos = new Vector3f();
            Quaternionf axisSpin = new Quaternionf();
            Vector3f axis = new Vector3f(1, 0.4f * dir, 0.6f).normalize();
            Quaternionf q0 = new Quaternionf(st[3], st[4], st[5], st[6]);
            Quaternionf q;
            float spin = 0.35f;
            if (age <= t1) {
                pos.set(st[0] + st[7] * age, st[1] + vy * age - 0.5f * g * age * age, st[2] + st[9] * age);
                q = axisSpin.rotateAxis(spin * age, axis).mul(q0);
            } else {
                float hx = st[0] + st[7] * t1;
                float hz = st[2] + st[9] * t1;
                float vHit = vy - g * t1;                // < 0
                float vb = Math.max(0.05f, -vHit * 0.3f); // отскок
                float t2 = 2 * vb / g;
                float u = age - t1;
                float uc = Math.min(u, t2);
                float y = u < t2 ? LID_GROUND + vb * u - 0.5f * g * u * u : LID_GROUND;
                pos.set(hx + st[7] * 0.35f * uc, y, hz + st[9] * 0.35f * uc);
                Quaternionf qHit = new Quaternionf().rotateAxis(spin * t1, axis).mul(q0);
                Quaternionf flat = new Quaternionf().rotateY(idx * 1.1f).rotateX((float) (Math.PI / 2));
                q = qHit.slerp(flat, Math.min(1, u / 5f));
            }
            Matrix4f local = new Matrix4f(mp).invert().mul(new Matrix4f().translation(pos).rotate(q));
            Vector3f lp = local.getTranslation(new Vector3f()).mul(16);
            Vector3f eul = new Vector3f();
            local.get3x3(new Matrix3f()).getEulerAnglesZYX(eul);
            cap.x = lp.x;
            cap.y = lp.y;
            cap.z = lp.z;
            cap.xRot = eul.x;
            cap.yRot = eul.y;
            cap.zRot = eul.z;
            if (age > LID_LIFE - 20) {
                cap.xScale = cap.yScale = cap.zScale = Math.max(0.02f, (LID_LIFE - age) / 20f);
            }
        }

        ResourceLocation texture(State s) {
            return texture0(s);
        }

        private ResourceLocation texture0(State s) {
            String t = name;
            if (variants) {
                t += s.hp < 0.2f ? "_burnt" : s.hp < 0.5f ? "_damaged" : "";
            }
            return new net.minecraft.resources.ResourceLocation(HardDayz.MODID, "textures/entity/" + t + ".png");
        }

        ResourceLocation glow(State s) {
            String t = texture(s).getPath();
            return new net.minecraft.resources.ResourceLocation(HardDayz.MODID, t.replace(".png", "_glowmask.png"));
        }
    }

    /** На направляющей i стоит не обычный «Шахед» — рисуем его собственную модель. */
    static boolean otherDrone(State s, int i) {
        return i < s.drones.length && s.drones[i] != com.harddayz.entity.Drone.Kind.SHAHED.ordinal();
    }

    private record Payload(HdModel<Object> model, GeoModel geo, ResourceLocation texture) {
    }

    private static final Map<Integer, Payload> PAYLOADS = new java.util.HashMap<>();

    private static Payload payload(int kind) {
        return PAYLOADS.computeIfAbsent(kind, k -> {
            String n = switch (com.harddayz.entity.Drone.Kind.values()[k]) {
                case SHAHED_238 -> "shahed238";
                case SHAHED_131 -> "geran2";
                case GERBERA -> "gerbera";
                case GERAN5 -> "shahed238";
                case BANDEROL -> "m_gmlrs";
                default -> "shahed136";
            };
            GeoModel g = GeoModel.get(n, new GeoModel.Marks("wing", GeoModel.Mode.WINGS));
            return new Payload(new HdModel<Object>(g.root), g,
                    new net.minecraft.resources.ResourceLocation(HardDayz.MODID, "textures/entity/" + n + ".png"));
        });
    }

    private void submitPayloads(GeoLauncherModel g, State s, PoseStack pose, MultiBufferSource buffers) {
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
            p.model().render(null, pose, buffers, HdRender.body(p.texture()), s.lightCoords);
            pose.popPose();
        }
    }

    public static final ResourceLocation MARKS = new net.minecraft.resources.ResourceLocation(HardDayz.MODID, "textures/entity/marks.png");
    private static final java.util.Map<ModelPart, HdModel<Object>> MARK_MODELS = new java.util.IdentityHashMap<>();

    /** Опознавательный знак фракции поверх модели. */
    public static void submitMarks(GeoModel geo, int faction, PoseStack pose, MultiBufferSource buffers, int light) {
        if (geo.marks == null || faction < 0 || faction >= geo.marks.length) {
            return;
        }
        HdModel<Object> m = MARK_MODELS.computeIfAbsent(geo.marks[faction], p -> new HdModel<Object>(p));
        m.render(null, pose, buffers, HdRender.cutout(MARKS), light);
    }

    static class LauncherModel extends HdModel<State> {
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
            super(root);
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
        geo.put(Launcher.Kind.DECOY, new GeoLauncherModel("grad", cab, true));
        geo.put(Launcher.Kind.ISKANDER, new GeoLauncherModel("iskander", cab, true));
        geo.put(Launcher.Kind.TOCHKA, new GeoLauncherModel("tochka", cab, true));
        geo.put(Launcher.Kind.SMERCH, new GeoLauncherModel("smerch", cab, true));
        geo.put(Launcher.Kind.URAGAN, new GeoLauncherModel("uragan", cab, true));
        geo.put(Launcher.Kind.KALIBR, new GeoLauncherModel("kalibr", cab, true));
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
        geo.put(Launcher.Kind.MORTAR, new GeoLauncherModel("mortar", null, true));
        geo.put(Launcher.Kind.EW, new GeoLauncherModel("ew", cab, true));
        geo.put(Launcher.Kind.RADAR, new GeoLauncherModel("radar", cab, true));
        geo.put(Launcher.Kind.CBR, new GeoLauncherModel("radar", cab, true));
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
    public boolean shouldRender(Launcher e, Frustum frustum, double x, double y, double z) {
        return e.shouldRender(x, y, z) && frustum.isVisible(e.getBoundingBox().inflate(6, 8, 6));
    }

    public State createRenderState() {
        return new State();
    }

    private void extractRenderState(Launcher e, State s, float pt) {
        s.kind = e.kind();
        s.bodyYaw = net.minecraft.util.Mth.rotLerp(pt, e.yRotO, e.getYRot());
        s.turret = e.turret(pt);
        s.elevation = e.elevation(pt);
        s.erect = e.erect(pt);
        s.outrig = e.outrig(pt);
        s.wheelSpin = e.prevWheelSpin + (e.wheelSpin - e.prevWheelSpin) * pt;
        s.steer = e.prevSteer + (e.steer - e.prevSteer) * pt;
        s.ammo = e.ammo();
        s.time = e.tickCount + pt;
        s.flash = e.flash();
        // крышки контейнеров: слетают при вскрытии трубы (или при пуске, если вскрытия не было), после перезарядки все на месте
        s.launcher = e;
        int ammo = e.ammo();
        float now = e.tickCount + pt;
        if (e.capLastAmmo >= 0 && ammo < e.capLastAmmo) {
            for (int i = ammo; i < Math.min(e.capLastAmmo, e.capLaunched.length); i++) {
                if (e.capLaunched[i] == -1) {
                    e.capLaunched[i] = now;
                }
            }
        } else if (ammo > e.capLastAmmo) {
            java.util.Arrays.fill(e.capLaunched, -1);
            java.util.Arrays.fill(e.capStart, null);
            for (int i = ammo; i < Math.min(e.kind().capacity, e.capLaunched.length); i++) {
                e.capLaunched[i] = -1000; // уже стреляные (загрузка мира): крышек нет
            }
        }
        e.capLastAmmo = ammo;
        int arm = e.arm();
        s.arm = arm > 0 ? Math.min(Launcher.ARM_TICKS, arm + pt) : 0;
        if (arm > 0 && ammo > 0 && ammo - 1 < e.capLaunched.length && e.capLaunched[ammo - 1] == -1) {
            e.capLaunched[ammo - 1] = now - s.arm; // труба вскрывается: крышка летит с этого момента
        }
        for (int i = 0; i < s.capAge.length; i++) {
            s.capAge[i] = e.capLaunched[i] < -500 ? 1000 : e.capLaunched[i] < 0 ? -1 : now - e.capLaunched[i];
        }
        s.faction = e.faction();
        s.hp = e.health() / Math.max(1f, e.kind().health);
        s.drones = e.droneKinds();
        s.rotor = e.kind().heli() ? (e.getPassengers().isEmpty() && e.onGround() ? 0 : (e.tickCount + pt) * 1.4f) : 0;
        s.tilt = 0;
        var mc = net.minecraft.client.Minecraft.getInstance();
        s.inside = mc.player != null && mc.player.getVehicle() == e && mc.options.getCameraType().isFirstPerson();
    }

    @Override
    public ResourceLocation getTextureLocation(Launcher e) {
        return PALETTE;
    }

    @Override
    public void render(Launcher e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        State s = createRenderState();
        extractRenderState(e, s, pt);
        s.lightCoords = light;
        draw(s, pose, buffers);
        if (e.netOn() && !s.inside) {
            drawNet(e, s, pose, buffers, light);
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    private static final ResourceLocation NET = new ResourceLocation("minecraft", "textures/block/azalea_leaves.png");

    /** Маскировочная сеть шатром над кузовом. */
    private static void drawNet(Launcher e, State s, PoseStack pose, MultiBufferSource buffers, int light) {
        var b = e.body();
        double f = b.front() + 0.4, k = b.back() + 0.4, w = b.half() + 0.5, h = 3.4, out = 0.7;
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(NET));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-s.bodyYaw));
        var m = pose.last();
        // верх
        quad(vc, m, light, -w, h, f, w, h, f, w, h, -k, -w, h, -k);
        // скаты до земли
        quad(vc, m, light, -w, h, f, w, h, f, w + out, 0, f + out, -w - out, 0, f + out);
        quad(vc, m, light, w, h, -k, -w, h, -k, -w - out, 0, -k - out, w + out, 0, -k - out);
        quad(vc, m, light, w, h, f, w, h, -k, w + out, 0, -k - out, w + out, 0, f + out);
        quad(vc, m, light, -w, h, -k, -w, h, f, -w - out, 0, f + out, -w - out, 0, -k - out);
        pose.popPose();
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose m, int light, double x1, double y1, double z1, double x2, double y2,
            double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        float u = (float) Math.hypot(x2 - x1, z2 - z1), v = (float) Math.sqrt((x3 - x2) * (x3 - x2) + (y3 - y2) * (y3 - y2) + (z3 - z2) * (z3 - z2));
        float[][] p = {{(float) x1, (float) y1, (float) z1, 0, 0}, {(float) x2, (float) y2, (float) z2, u, 0}, {(float) x3, (float) y3, (float) z3, u, v},
                {(float) x4, (float) y4, (float) z4, 0, v}};
        for (float[] q : p) {
            vc.vertex(m.pose(), q[0], q[1], q[2]).color(255, 255, 255, 255).uv(q[3], q[4])
                    .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(m.normal(), 0, 1, 0).endVertex();
        }
    }

    private void draw(State s, PoseStack pose, MultiBufferSource buffers) {
        GeoLauncherModel g = geo.get(s.kind);
        if (s.kind.removed() || g == null && models.get(s.kind) == null) {
            return; // удалённая техника (исчезнет при первом тике сервера)
        }
        if (s.inside) { // сидим внутри от первого лица — кабина не закрывает обзор
            return;
        }
        if (g != null) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180 - s.bodyYaw));
            if (s.tilt != 0) {
                pose.mulPose(Axis.XP.rotationDegrees(-s.tilt)); // в полёте нос опущен
            }
            int tint = -1;
            g.render(s, pose, buffers, HdRender.body(g.texture(s)), s.lightCoords);
            submitPayloads(g, s, pose, buffers);
            g.render(s, pose, buffers, HdRender.eyes(g.glow(s)), s.lightCoords);
            submitMarks(g.geo, s.faction, pose, buffers, s.lightCoords);
            pose.popPose();
            return;
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-s.bodyYaw));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501f, 0);
        models.get(s.kind).render(s, pose, buffers, HdRender.body(PALETTE), s.lightCoords);
        pose.popPose();
    }
}
