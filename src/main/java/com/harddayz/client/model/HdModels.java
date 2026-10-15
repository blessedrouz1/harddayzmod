package com.harddayz.client.model;

import com.harddayz.HardDayz;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Геометрия техники, ракет, дронов и взрыва. Текстура — палитра 4x4 цветов (textures/entity/palette.png),
 * объявленный размер 1024x1024: каждый цвет занимает 256x256. 16 единиц = 1 блок.
 * <p>Техника: Y вниз, земля на y=24, «вперёд» = +Z (кабина). Ракеты и дроны: Y вверх, центр в начале координат, нос по +Z.
 * <p>Детали с анимацией: wheel_N (вращение), steer_N (поворот передних), turret, rack, erector, jackN, radar, propN.
 */
public final class HdModels {
    public static final ModelLayerLocation GRAD = layer("grad");
    public static final ModelLayerLocation ISKANDER = layer("iskander");
    public static final ModelLayerLocation SAM = layer("sam");
    public static final ModelLayerLocation SHAHED_LAUNCHER = layer("shahed_launcher");
    public static final ModelLayerLocation ROCKET_GRAD = layer("rocket_grad");
    public static final ModelLayerLocation ROCKET_BALLISTIC = layer("rocket_ballistic");
    public static final ModelLayerLocation ROCKET_SAM = layer("rocket_sam");
    public static final ModelLayerLocation BOMB = layer("bomb");
    public static final ModelLayerLocation FLAME = layer("flame");
    public static final ModelLayerLocation SHAHED = layer("shahed");
    public static final ModelLayerLocation FPV = layer("fpv");
    public static final ModelLayerLocation RECON = layer("recon");
    public static final ModelLayerLocation BLOB = layer("blob");
    public static final ModelLayerLocation RAMP = layer("ramp");
    public static final ModelLayerLocation GRAD_P = layer("grad_p");
    public static final ModelLayerLocation SAM_POST = layer("sam_post");
    public static final ModelLayerLocation EW = layer("ew");
    public static final ModelLayerLocation SHAHED_238 = layer("shahed_238");
    public static final ModelLayerLocation GERBERA = layer("gerbera");
    public static final ModelLayerLocation MG = layer("mg");
    public static final ModelLayerLocation PICKUP_MG = layer("pickup_mg");
    public static final ModelLayerLocation PICKUP_SAM = layer("pickup_sam");
    public static final ModelLayerLocation BMP2 = layer("bmp2");
    public static final ModelLayerLocation BRADLEY = layer("bradley");
    public static final ModelLayerLocation RADAR = layer("radar");
    public static final ModelLayerLocation SAM_MANUAL = layer("sam_manual");
    public static final ModelLayerLocation RING = layer("ring");

    public static final int OLIVE = 0, DARK_OLIVE = 1, TIRE = 2, STEEL = 3, GLASS = 4, WHITE = 5, GREEN = 6, RED = 7,
            FLAME_ORANGE = 8, FLAME_YELLOW = 9, BLACK = 10, SAND = 11, LIGHT = 12, DARK_STEEL = 13, LAMP = 14, CAMO = 15;

    private HdModels() {
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(new net.minecraft.resources.ResourceLocation(HardDayz.MODID, name), "main");
    }

    /** Набор кубов разных цветов в одной детали. */
    static final class C {
        final CubeListBuilder b = CubeListBuilder.create();

        C box(int color, float x, float y, float z, float w, float h, float d) {
            b.texOffs((color % 4) * 256, (color / 4) * 256).addBox(x, y, z, w, h, d);
            return this;
        }
    }

    static C c() {
        return new C();
    }

    private static PartDefinition part(PartDefinition parent, String name, C c, PartPose pose) {
        return parent.addOrReplaceChild(name, c.b, pose);
    }

    private static LayerDefinition create(MeshDefinition mesh) {
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    // ------------------------------------------------------------------ колёса

    /** Колесо — почти круглое (две пары кубов под 45°), с диском. */
    private static void wheel(PartDefinition parent, String name, float x, float y, float z, float r, int side) {
        float k = r * 0.42f;
        PartDefinition w = part(parent, name, c()
                .box(TIRE, -3.5f, -r, -k, 7, r * 2, k * 2)
                .box(TIRE, -3.5f, -k, -r, 7, k * 2, r * 2)
                .box(DARK_STEEL, side > 0 ? 3.2f : -4.2f, -r * 0.45f, -r * 0.45f, 1, r * 0.9f, r * 0.9f)
                .box(STEEL, side > 0 ? 3.9f : -4.4f, -1.2f, -1.2f, 0.5f, 2.4f, 2.4f), PartPose.offset(x, y, z));
        part(w, "diag", c()
                .box(TIRE, -3.4f, -r * 0.98f, -k, 6.8f, r * 1.96f, k * 2)
                .box(TIRE, -3.4f, -k, -r * 0.98f, 6.8f, k * 2, r * 1.96f), PartPose.rotation((float) Math.PI / 4, 0, 0));
    }

    /** Ось: два колеса; передние — внутри поворотного узла steer_N. */
    private static void axle(PartDefinition root, int idx, float halfWidth, float r, float z, boolean steer) {
        float y = 24 - r;
        for (int s = -1; s <= 1; s += 2) {
            float x = s * (halfWidth + 3.5f);
            String n = idx + (s < 0 ? "r" : "l");
            if (steer) {
                PartDefinition st = part(root, "steer_" + n, c(), PartPose.offset(x, y, z));
                wheel(st, "wheel_" + n, 0, 0, 0, r, s);
            } else {
                wheel(root, "wheel_" + n, x, y, z, r, s);
            }
        }
    }

    /** Домкрат-опора: опускается при пуске (сдвиг по Y). */
    private static void jack(PartDefinition root, int idx, float x, float z) {
        part(root, "jack" + idx, c()
                .box(DARK_STEEL, -1.5f, -6, -1.5f, 3, 12, 3)
                .box(STEEL, -1, 6, -1, 2, 4, 2)
                .box(DARK_STEEL, -3, 10, -3, 6, 1.5f, 6), PartPose.offset(x, 6, z));
    }

    // ================================================================== БМ-21 «Град» на шасси «Урал»

    public static LayerDefinition grad() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        axle(root, 0, 13, 9.5f, 50, true);
        axle(root, 1, 13, 9.5f, -16, false);
        axle(root, 2, 13, 9.5f, -36, false);
        truckFront(root, OLIVE);
        // платформа и обвес
        part(root, "deck", c()
                .box(OLIVE, -17, 0, -58, 34, 5, 38)
                .box(OLIVE, -17, 0, -20, 34, 5, 40)
                .box(DARK_OLIVE, -18, 5, -58, 2, 3, 76)
                .box(DARK_OLIVE, 16, 5, -58, 2, 3, 76)
                .box(DARK_OLIVE, -19, 5, -6, 4, 7, 16)
                .box(DARK_OLIVE, 15, 5, -6, 4, 7, 16)
                .box(STEEL, -19.2f, 7, -4, 0.3f, 3, 12)
                .box(STEEL, 18.9f, 7, -4, 0.3f, 3, 12)
                .box(CAMO, -17, -6, -58, 10, 6, 14)
                .box(CAMO, 7, -6, -58, 10, 6, 14)
                .box(RED, -16, 1, -58.6f, 4, 2, 0.6f)
                .box(RED, 12, 1, -58.6f, 4, 2, 0.6f)
                .box(DARK_OLIVE, -17, 5, -60, 34, 3, 2), PartPose.ZERO);
        // запаска за кабиной
        part(root, "spare", c()
                .box(TIRE, -9, -16, 16, 18, 18, 6)
                .box(DARK_STEEL, -4, -11, 15.5f, 8, 8, 1), PartPose.ZERO);
        jack(root, 0, -17, -54);
        jack(root, 1, 17, -54);
        // башня и пакет
        PartDefinition turret = part(root, "turret", c()
                .box(DARK_STEEL, -11, 2, -11, 22, 6, 22)
                .box(DARK_STEEL, -9, 0, -9, 18, 2, 18)
                .box(OLIVE, -13, -3, -5, 3, 6, 10)
                .box(OLIVE, 10, -3, -5, 3, 6, 10)
                .box(DARK_OLIVE, 11, -6, 4, 6, 6, 6)
                .box(GLASS, 16.6f, -5, 5, 0.6f, 3, 4), PartPose.offset(0, -8, -24));
        C rack = c()
                .box(OLIVE, -21, -22, -6, 2, 24, 52)
                .box(OLIVE, 19, -22, -6, 2, 24, 52)
                .box(DARK_OLIVE, -19, 1, 0, 38, 2, 6)
                .box(DARK_OLIVE, -19, 1, 30, 38, 2, 6)
                .box(DARK_OLIVE, -19, -23, 10, 38, 2, 4)
                .box(DARK_OLIVE, -19, -23, 36, 38, 2, 4)
                .box(DARK_STEEL, -2, 0, -8, 4, 4, 6);
        // 40 труб: 4 ряда по 10, как у БМ-21
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 10; col++) {
                float x = (col - 4.5f) * 3.6f - 1.7f;
                float y = -(4 + row * 4f) - 1.7f;
                rack.box(DARK_STEEL, x, y, -8, 3.4f, 3.4f, 54);
                rack.box(BLACK, x + 0.5f, y + 0.5f, 45.8f, 2.4f, 2.4f, 0.4f);
            }
            rack.box(DARK_OLIVE, -19, -(4 + row * 4f) + 1.8f, 20, 38, 0.6f, 2);
        }
        PartDefinition rackPart = part(turret, "rack", rack, PartPose.ZERO);
        for (int i = 0; i < 40; i++) {
            int row = i / 10;
            int col = i % 10;
            part(rackPart, "tip" + i, c()
                    .box(RED, (col - 4.5f) * 3.6f - 1.1f, -(4 + row * 4f) - 1.1f, 43.5f, 2.2f, 2.2f, 2.5f), PartPose.ZERO);
        }
        part(turret, "piston", c().box(STEEL, -1.5f, -1.5f, 0, 3, 3, 14), PartPose.offsetAndRotation(0, 4, 8, -0.6f, 0, 0));
        return create(mesh);
    }

    /** Капотная кабина «Урала»: капот, решётка, крылья, фары, зеркала. */
    private static void truckFront(PartDefinition root, int color) {
        part(root, "frame", c()
                .box(DARK_STEEL, -12, 6, -60, 5, 5, 60)
                .box(DARK_STEEL, 7, 6, -60, 5, 5, 60)
                .box(DARK_STEEL, -12, 6, 0, 5, 5, 64)
                .box(DARK_STEEL, 7, 6, 0, 5, 5, 64), PartPose.ZERO);
        part(root, "cab", c()
                .box(color, -17, -22, 22, 34, 24, 22)
                .box(DARK_OLIVE, -17.5f, -23, 21.5f, 35, 2, 23)
                .box(GLASS, -15, -20, 43.6f, 13, 9, 0.6f)
                .box(GLASS, 2, -20, 43.6f, 13, 9, 0.6f)
                .box(GLASS, -17.6f, -19, 30, 0.6f, 8, 11)
                .box(GLASS, 17, -19, 30, 0.6f, 8, 11)
                .box(DARK_STEEL, -17.4f, -9, 33, 0.4f, 1, 3)
                .box(DARK_STEEL, 17, -9, 33, 0.4f, 1, 3)
                // капот
                .box(color, -13, -8, 44, 26, 10, 18)
                .box(DARK_OLIVE, -13.5f, -9, 44, 27, 1, 18)
                .box(BLACK, -11, -6, 61.6f, 22, 8, 0.6f)
                .box(DARK_STEEL, -10, -5, 62, 20, 1, 0.4f)
                .box(DARK_STEEL, -10, -2, 62, 20, 1, 0.4f)
                // крылья над передними колёсами
                .box(color, -19, -2, 40, 7, 2, 20)
                .box(color, 12, -2, 40, 7, 2, 20)
                .box(LAMP, -18, -5, 57, 4, 3, 3)
                .box(LAMP, 14, -5, 57, 4, 3, 3)
                .box(BLACK, -15, 2, 62, 30, 4, 3)
                // зеркала
                .box(DARK_STEEL, -22, -16, 41, 4, 1, 1)
                .box(BLACK, -23, -18, 41, 2, 4, 1)
                .box(DARK_STEEL, 18, -16, 41, 4, 1, 1)
                .box(BLACK, 21, -18, 41, 2, 4, 1)
                // выхлоп и антенна
                .box(BLACK, 15, -26, 20, 2, 12, 2)
                .box(DARK_STEEL, -16, -40, 23, 0.6f, 18, 0.6f)
                // двери, ручки, ступеньки
                .box(DARK_OLIVE, -17.3f, -20, 26, 0.4f, 20, 0.5f)
                .box(DARK_OLIVE, 16.9f, -20, 26, 0.4f, 20, 0.5f)
                .box(DARK_OLIVE, -17.3f, -20, 42, 0.4f, 20, 0.5f)
                .box(DARK_OLIVE, 16.9f, -20, 42, 0.4f, 20, 0.5f)
                .box(STEEL, -17.5f, -8, 38, 0.5f, 1, 2.5f)
                .box(STEEL, 17, -8, 38, 0.5f, 1, 2.5f)
                .box(DARK_STEEL, -20, 3, 28, 4, 1, 10)
                .box(DARK_STEEL, 16, 3, 28, 4, 1, 10)
                // люк на крыше, решётка радиатора, крюки, брызговики
                .box(DARK_OLIVE, -5, -24, 28, 10, 1, 10)
                .box(DARK_STEEL, -9, -6, 61.9f, 1, 7, 0.5f)
                .box(DARK_STEEL, -4, -6, 61.9f, 1, 7, 0.5f)
                .box(DARK_STEEL, 3, -6, 61.9f, 1, 7, 0.5f)
                .box(DARK_STEEL, 8, -6, 61.9f, 1, 7, 0.5f)
                .box(RED, -13, 4, 64.5f, 3, 2, 2)
                .box(RED, 10, 4, 64.5f, 3, 2, 2)
                .box(BLACK, -19, 4, 36, 6, 7, 0.5f)
                .box(BLACK, 13, 4, 36, 6, 7, 0.5f), PartPose.ZERO);
        // крылья над задними колёсами
        part(root, "rear_fenders", c()
                .box(DARK_OLIVE, -19, 4, -48, 7, 1.5f, 42)
                .box(DARK_OLIVE, 12, 4, -48, 7, 1.5f, 42)
                .box(BLACK, -19, 5.5f, -49, 7, 6, 0.5f)
                .box(BLACK, 12, 5.5f, -49, 7, 6, 0.5f), PartPose.ZERO);
    }

    // ================================================================== ОТРК «Искандер»

    public static LayerDefinition iskander() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        axle(root, 0, 15, 10.5f, 50, true);
        axle(root, 1, 15, 10.5f, 26, true);
        axle(root, 2, 15, 10.5f, -26, false);
        axle(root, 3, 15, 10.5f, -50, false);
        part(root, "frame", c()
                .box(DARK_STEEL, -13, 5, -68, 6, 6, 68)
                .box(DARK_STEEL, 7, 5, -68, 6, 6, 68)
                .box(DARK_STEEL, -13, 5, 0, 6, 6, 68)
                .box(DARK_STEEL, 7, 5, 0, 6, 6, 68), PartPose.ZERO);
        // две кабины по бокам, двигатель между ними
        part(root, "cab", c()
                .box(CAMO, -22, -14, 52, 14, 18, 20)
                .box(CAMO, 8, -14, 52, 14, 18, 20)
                .box(GLASS, -20, -12, 71.6f, 10, 7, 0.6f)
                .box(GLASS, 10, -12, 71.6f, 10, 7, 0.6f)
                .box(GLASS, -22.6f, -12, 56, 0.6f, 6, 10)
                .box(GLASS, 21.9f, -12, 56, 0.6f, 6, 10)
                .box(DARK_OLIVE, -8, -6, 50, 16, 10, 22)
                .box(BLACK, -7, -4, 71.6f, 14, 6, 0.6f)
                .box(BLACK, -22, 2, 71, 44, 5, 3)
                .box(LAMP, -20, -2, 71.8f, 5, 3, 1)
                .box(LAMP, 15, -2, 71.8f, 5, 3, 1)
                .box(DARK_OLIVE, -23, -15, 51, 16, 1, 22)
                .box(DARK_OLIVE, 7, -15, 51, 16, 1, 22)
                .box(DARK_STEEL, -18, -30, 60, 0.6f, 16, 0.6f), PartPose.ZERO);
        // корпус пусковой
        part(root, "hull", c()
                .box(CAMO, -22, -8, -72, 44, 12, 62)
                .box(CAMO, -22, -8, -10, 44, 12, 60)
                .box(DARK_OLIVE, -23, -12, -72, 7, 4, 60)
                .box(DARK_OLIVE, 16, -12, -72, 7, 4, 60)
                .box(DARK_OLIVE, -23, -12, -12, 7, 4, 62)
                .box(DARK_OLIVE, 16, -12, -12, 7, 4, 62)
                .box(DARK_OLIVE, -23, 4, -40, 4, 6, 24)
                .box(DARK_OLIVE, 19, 4, -40, 4, 6, 24)
                .box(STEEL, -23.2f, 6, -36, 0.3f, 2, 16)
                .box(STEEL, 22.9f, 6, -36, 0.3f, 2, 16)
                .box(RED, -21, -2, -72.6f, 4, 2, 0.6f)
                .box(RED, 17, -2, -72.6f, 4, 2, 0.6f), PartPose.ZERO);
        jack(root, 0, -21, -66);
        jack(root, 1, 21, -66);
        jack(root, 2, -21, 44);
        jack(root, 3, 21, 44);
        // стрела: шарнир сзади, ракета лежит вдоль машины, при подъёме встаёт вертикально
        PartDefinition erector = part(root, "erector", c()
                .box(DARK_STEEL, -11, -3, -2, 3, 4, 104)
                .box(DARK_STEEL, 8, -3, -2, 3, 4, 104)
                .box(DARK_STEEL, -11, -7, 18, 22, 4, 4)
                .box(DARK_STEEL, -11, -7, 50, 22, 4, 4)
                .box(DARK_STEEL, -11, -7, 82, 22, 4, 4)
                .box(STEEL, -12, -10, 16, 2, 7, 8)
                .box(STEEL, 10, -10, 16, 2, 7, 8)
                .box(STEEL, -12, -10, 80, 2, 7, 8)
                .box(STEEL, 10, -10, 80, 2, 7, 8)
                .box(DARK_STEEL, -4, -4, -6, 8, 8, 8), PartPose.offset(0, -4, -44));
        PartDefinition missile = part(erector, "missile", c().box(BLACK, -5, -17, 2, 10, 10, 4), PartPose.ZERO);
        missileBody(missile, -12, 6);
        part(root, "piston_l", c().box(STEEL, -1.5f, -1.5f, 0, 3, 3, 30).box(DARK_STEEL, -2, -2, 0, 4, 4, 14),
                PartPose.offset(-9, -6, -30));
        part(root, "piston_r", c().box(STEEL, -1.5f, -1.5f, 0, 3, 3, 30).box(DARK_STEEL, -2, -2, 0, 4, 4, 14),
                PartPose.offset(9, -6, -30));
        return create(mesh);
    }

    /** Корпус баллистической ракеты вдоль +Z (диаметр 14), ось на высоте {@code axisY}. */
    private static void missileBody(PartDefinition parent, float axisY, float z0) {
        part(parent, "body", c()
                .box(GREEN, -7, axisY - 7, z0, 14, 14, 92)
                .box(GREEN, -6, axisY - 7.6f, z0 + 4, 12, 0.6f, 84)
                .box(GREEN, -6, axisY + 7, z0 + 4, 12, 0.6f, 84)
                .box(GREEN, -7.6f, axisY - 6, z0 + 4, 0.6f, 12, 84)
                .box(GREEN, 7, axisY - 6, z0 + 4, 0.6f, 12, 84)
                .box(DARK_OLIVE, -7.4f, axisY - 7.4f, z0 + 30, 14.8f, 14.8f, 2)
                .box(DARK_OLIVE, -7.4f, axisY - 7.4f, z0 + 64, 14.8f, 14.8f, 2)
                .box(WHITE, -7.2f, axisY - 7.2f, z0 + 80, 14.4f, 14.4f, 3)
                .box(GREEN, -5.5f, axisY - 5.5f, z0 + 92, 11, 11, 8)
                .box(GREEN, -3.8f, axisY - 3.8f, z0 + 100, 7.6f, 7.6f, 6)
                .box(DARK_OLIVE, -2, axisY - 2, z0 + 106, 4, 4, 4)
                .box(DARK_OLIVE, -0.6f, axisY - 14, z0 + 2, 1.2f, 7, 16)
                .box(DARK_OLIVE, -0.6f, axisY + 7, z0 + 2, 1.2f, 7, 16)
                .box(DARK_OLIVE, -14, axisY - 0.6f, z0 + 2, 7, 1.2f, 16)
                .box(DARK_OLIVE, 7, axisY - 0.6f, z0 + 2, 7, 1.2f, 16)
                .box(BLACK, -5, axisY - 5, z0 - 2, 10, 10, 2), PartPose.ZERO);
    }

    // ================================================================== ЗРК (колёсный, с радаром и пушками)

    public static LayerDefinition sam() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        axle(root, 0, 13, 9.5f, 40, true);
        axle(root, 1, 13, 9.5f, 4, false);
        axle(root, 2, 13, 9.5f, -30, false);
        part(root, "frame", c()
                .box(DARK_STEEL, -12, 6, -50, 5, 5, 50)
                .box(DARK_STEEL, 7, 6, -50, 5, 5, 50)
                .box(DARK_STEEL, -12, 6, 0, 5, 5, 54)
                .box(DARK_STEEL, 7, 6, 0, 5, 5, 54), PartPose.ZERO);
        part(root, "cab", c()
                .box(SAND, -18, -18, 28, 36, 22, 22)
                .box(GLASS, -15, -15, 49.6f, 13, 8, 0.6f)
                .box(GLASS, 2, -15, 49.6f, 13, 8, 0.6f)
                .box(GLASS, -18.6f, -15, 34, 0.6f, 7, 10)
                .box(GLASS, 18, -15, 34, 0.6f, 7, 10)
                .box(BLACK, -18, 2, 49, 36, 5, 3)
                .box(LAMP, -16, -2, 49.8f, 5, 3, 1)
                .box(LAMP, 11, -2, 49.8f, 5, 3, 1)
                .box(DARK_STEEL, -19, -8, 46, 4, 1, 1)
                .box(DARK_STEEL, 15, -8, 46, 4, 1, 1), PartPose.ZERO);
        part(root, "deck", c()
                .box(SAND, -18, -4, -52, 36, 10, 40)
                .box(SAND, -18, -4, -12, 36, 10, 40)
                .box(DARK_OLIVE, -19, 0, -40, 3, 6, 30)
                .box(DARK_OLIVE, 16, 0, -40, 3, 6, 30)
                .box(RED, -16, 0, -52.6f, 4, 2, 0.6f)
                .box(RED, 12, 0, -52.6f, 4, 2, 0.6f), PartPose.ZERO);
        jack(root, 0, -18, -48);
        jack(root, 1, 18, -48);
        PartDefinition turret = part(root, "turret", c()
                .box(SAND, -12, 0, -14, 24, 10, 28)
                .box(DARK_STEEL, -13, 9, -15, 26, 2, 30)
                .box(SAND, -9, -4, 4, 18, 4, 10)
                .box(DARK_STEEL, -7, -3, 13.6f, 14, 6, 1)
                .box(GLASS, -4, -3, 14.2f, 8, 3, 0.5f), PartPose.offset(0, -13.6f, -8.8f));
        PartDefinition radar = part(turret, "radar", c()
                .box(DARK_STEEL, -1, -8, -1, 2, 8, 2), PartPose.offset(0, 0, -8));
        part(radar, "dish", c()
                .box(LIGHT, -11, -18, -1.5f, 22, 10, 2)
                .box(DARK_STEEL, -11, -18, 0.5f, 22, 1, 1)
                .box(DARK_STEEL, -11, -9, 0.5f, 22, 1, 1)
                .box(DARK_STEEL, -1, -17, -3, 2, 8, 2), PartPose.ZERO);
        C pods = c();
        for (int s = -1; s <= 1; s += 2) {
            for (int k = 0; k < 2; k++) {
                for (int j = 0; j < 2; j++) {
                    float x = s * (15 + k * 5.5f) - 2.5f;
                    float y = -1 - j * 5.5f;
                    pods.box(LIGHT, x, y, -10, 5, 5, 38);
                    pods.box(BLACK, x + 0.6f, y + 0.6f, 27.6f, 3.8f, 3.8f, 0.5f);
                }
            }
            pods.box(SAND, s * 13 - 1.5f, -3, -4, 3, 3, 10);
            // 30-мм пушка
            pods.box(DARK_STEEL, s * 11 - 1.5f, -9, -2, 3, 3, 10);
            pods.box(BLACK, s * 11 - 0.6f, -8.1f, 8, 1.2f, 1.2f, 22);
        }
        part(turret, "rack", pods, PartPose.ZERO);
        return create(mesh);
    }

    // ================================================================== пусковая «Шахедов»

    public static LayerDefinition shahedLauncher() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        axle(root, 0, 13, 9.5f, 50, true);
        axle(root, 1, 13, 9.5f, -16, false);
        axle(root, 2, 13, 9.5f, -36, false);
        truckFront(root, SAND);
        part(root, "deck", c()
                .box(SAND, -17, 0, -58, 34, 5, 38)
                .box(SAND, -17, 0, -20, 34, 5, 40)
                .box(DARK_OLIVE, -18, 5, -58, 2, 3, 76)
                .box(DARK_OLIVE, 16, 5, -58, 2, 3, 76)
                .box(RED, -16, 1, -58.6f, 4, 2, 0.6f)
                .box(RED, 12, 1, -58.6f, 4, 2, 0.6f), PartPose.ZERO);
        jack(root, 0, -17, -54);
        jack(root, 1, 17, -54);
        PartDefinition turret = part(root, "turret", c()
                .box(DARK_STEEL, -10, 2, -10, 20, 6, 20)
                .box(SAND, -12, -3, -4, 3, 6, 8)
                .box(SAND, 9, -3, -4, 3, 6, 8), PartPose.offset(0, -7.2f, -25.6f));
        // открытый контейнер-этажерка на 5 дронов
        C rack = c()
                .box(DARK_STEEL, -26, -36, -6, 2, 36, 2)
                .box(DARK_STEEL, 24, -36, -6, 2, 36, 2)
                .box(DARK_STEEL, -26, -36, 50, 2, 36, 2)
                .box(DARK_STEEL, 24, -36, 50, 2, 36, 2)
                .box(DARK_STEEL, -26, -37, -6, 52, 1, 2)
                .box(DARK_STEEL, -26, -37, 50, 52, 1, 2)
                .box(SAND, -24, 0, -6, 48, 1, 58);
        for (int i = 0; i < 5; i++) {
            float y = -3 - i * 7;
            rack.box(DARK_STEEL, -2, y + 1.2f, -6, 4, 1, 58);
        }
        PartDefinition rackPart = part(turret, "rack", rack, PartPose.ZERO);
        for (int i = 0; i < 5; i++) {
            float y = -5 - i * 7;
            PartDefinition d = part(rackPart, "tip" + i, c()
                    .box(LIGHT, -2, y - 2, -4, 4, 4, 44)
                    .box(DARK_STEEL, -1.5f, y - 1.5f, 40, 3, 3, 4), PartPose.ZERO);
            deltaWing(d, y, 0, 0.75f);
        }
        return create(mesh);
    }

    /** Треугольное крыло «Шахеда» лесенкой из пластин. */
    private static void deltaWing(PartDefinition parent, float y, float z0, float scale) {
        C w = c();
        int steps = 7;
        for (int i = 0; i < steps; i++) {
            float span = (46 - i * 6) * scale;
            float z = z0 + i * 4.5f * scale;
            w.box(LIGHT, -span / 2, y - 0.5f, z, span, 1, 4.6f * scale);
        }
        w.box(LIGHT, -23 * scale, y - 5 * scale, z0, 1, 5 * scale, 6 * scale);
        w.box(LIGHT, 22 * scale, y - 5 * scale, z0, 1, 5 * scale, 6 * scale);
        part(parent, "wing", w, PartPose.ZERO);
    }

    // ================================================================== одиночные пусковые и РЭБ

    /** Рампа под один дрон: опорная рама, поворотный стол, рельс с дроном. */
    public static LayerDefinition ramp() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "base", c()
                .box(DARK_STEEL, -12, 21, -26, 24, 3, 52)
                .box(DARK_STEEL, -14, 22, -28, 4, 2, 6)
                .box(DARK_STEEL, 10, 22, -28, 4, 2, 6)
                .box(DARK_STEEL, -14, 22, 22, 4, 2, 6)
                .box(DARK_STEEL, 10, 22, 22, 4, 2, 6)
                .box(SAND, -8, 15, -24, 16, 6, 12), PartPose.ZERO);
        PartDefinition turret = part(root, "turret", c()
                .box(DARK_STEEL, -6, 0, -6, 12, 5, 12), PartPose.offset(0, 14.4f, -19.2f));
        PartDefinition rack = part(turret, "rack", c()
                .box(STEEL, -5, -1, -4, 2, 2, 48)
                .box(STEEL, 3, -1, -4, 2, 2, 48)
                .box(DARK_STEEL, -5, -1, 40, 10, 2, 2)
                .box(DARK_STEEL, -5, -1, 10, 10, 2, 2)
                .box(DARK_STEEL, -1, 1, 20, 2, 12, 2), PartPose.ZERO);
        PartDefinition d = part(rack, "tip0", c()
                .box(LIGHT, -2, -5, 2, 4, 4, 38)
                .box(DARK_STEEL, -1.5f, -4.5f, 0, 3, 3, 2), PartPose.ZERO);
        deltaWing(d, -3, 4, 0.7f);
        return create(mesh);
    }

    /** «Град-П»: тренога с одной трубой и прицелом. */
    public static LayerDefinition gradP() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "legs", c()
                .box(DARK_OLIVE, -1, 8, -1, 2, 16, 2)
                .box(DARK_STEEL, -3, 22, -3, 6, 2, 6), PartPose.ZERO);
        part(root, "leg_l", c().box(DARK_OLIVE, -1, 0, -1, 2, 19, 2), PartPose.offsetAndRotation(0, 7.2f, 0, -0.55f, 0, 0.5f));
        part(root, "leg_r", c().box(DARK_OLIVE, -1, 0, -1, 2, 19, 2), PartPose.offsetAndRotation(0, 7.2f, 0, -0.55f, 0, -0.5f));
        part(root, "leg_b", c().box(DARK_OLIVE, -1, 0, -1, 2, 19, 2), PartPose.offsetAndRotation(0, 7.2f, 0, 0.6f, 0, 0));
        PartDefinition turret = part(root, "turret", c()
                .box(DARK_STEEL, -3, -1, -3, 6, 3, 6), PartPose.offset(0, 7.2f, 0));
        PartDefinition rack = part(turret, "rack", c()
                .box(OLIVE, -2.5f, -5, -12, 5, 5, 44)
                .box(BLACK, -1.6f, -4.1f, 31.8f, 3.2f, 3.2f, 0.4f)
                .box(DARK_OLIVE, -2.8f, -5.3f, 6, 5.6f, 5.6f, 2)
                .box(DARK_STEEL, 3, -8, 2, 2, 3, 5)
                .box(GLASS, 3.2f, -7.5f, 6.8f, 1.6f, 2, 0.4f), PartPose.ZERO);
        part(rack, "tip0", c().box(RED, -1.3f, -3.8f, 28, 2.6f, 2.6f, 3), PartPose.ZERO);
        return create(mesh);
    }

    /** Пост ЗУР: тумба и два контейнера. */
    public static LayerDefinition samPost() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "base", c()
                .box(SAND, -8, 14, -8, 16, 10, 16)
                .box(DARK_STEEL, -10, 22, -10, 20, 2, 20), PartPose.ZERO);
        PartDefinition turret = part(root, "turret", c()
                .box(SAND, -5, 0, -5, 10, 14, 10)
                .box(DARK_STEEL, -2, -3, 3, 4, 3, 3), PartPose.offset(0, 0, 0));
        part(turret, "radar", c().box(LIGHT, -4, -10, -1, 8, 6, 1).box(DARK_STEEL, -0.5f, -4, -0.5f, 1, 4, 1), PartPose.offset(0, -3, -3));
        part(turret, "rack", c()
                .box(LIGHT, -8.6f, -2.5f, -8, 5, 5, 30)
                .box(LIGHT, 3.6f, -2.5f, -8, 5, 5, 30)
                .box(BLACK, -8, -1.9f, 21.6f, 3.8f, 3.8f, 0.5f)
                .box(BLACK, 4.2f, -1.9f, 21.6f, 3.8f, 3.8f, 0.5f)
                .box(SAND, -4, -1, -2, 8, 2, 6), PartPose.ZERO);
        return create(mesh);
    }

    /** Станция РЭБ на «Урале»: кунг, мачта, вращающиеся антенные решётки. */
    public static LayerDefinition ew() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        axle(root, 0, 13, 9.5f, 50, true);
        axle(root, 1, 13, 9.5f, -16, false);
        axle(root, 2, 13, 9.5f, -36, false);
        truckFront(root, OLIVE);
        part(root, "kung", c()
                .box(OLIVE, -18, -20, -60, 36, 25, 40)
                .box(OLIVE, -18, -20, -20, 36, 25, 36)
                .box(DARK_OLIVE, -18.5f, -21, -60, 37, 1, 76)
                .box(DARK_OLIVE, -18.6f, -14, -50, 0.6f, 12, 8)
                .box(DARK_OLIVE, 18, -14, -50, 0.6f, 12, 8)
                .box(GLASS, -18.6f, -14, -30, 0.6f, 5, 8)
                .box(GLASS, 18, -14, -30, 0.6f, 5, 8)
                .box(DARK_STEEL, -16, -24, -56, 6, 3, 6)
                .box(RED, -16, 1, -60.6f, 4, 2, 0.6f)
                .box(RED, 12, 1, -60.6f, 4, 2, 0.6f), PartPose.ZERO);
        jack(root, 0, -17, -54);
        jack(root, 1, 17, -54);
        PartDefinition turret = part(root, "turret", c()
                .box(DARK_STEEL, -3, -40, -3, 6, 40, 6)
                .box(DARK_STEEL, -6, -2, -6, 12, 2, 12), PartPose.offset(0, -21, -24));
        PartDefinition radar = part(turret, "radar", c().box(DARK_STEEL, -1, -6, -1, 2, 6, 2), PartPose.offset(0, -40, 0));
        part(radar, "arrays", c()
                .box(LIGHT, -14, -16, -1, 12, 12, 1)
                .box(LIGHT, 2, -16, -1, 12, 12, 1)
                .box(DARK_STEEL, -14, -10, -2, 28, 1, 1)
                .box(LIGHT, -1, -24, -6, 1, 8, 12), PartPose.ZERO);
        part(turret, "rack", c()
                .box(DARK_STEEL, -12, -30, -1, 24, 1, 1)
                .box(LIGHT, -14, -34, -1, 3, 6, 1)
                .box(LIGHT, 11, -34, -1, 3, 6, 1), PartPose.ZERO);
        return create(mesh);
    }

    /** «Шахед-238»: тёмный корпус, реактивный двигатель сверху сзади, без винта. */
    public static LayerDefinition shahed238() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = part(root, "body", c()
                .box(DARK_STEEL, -2.5f, -2.5f, -26, 5, 5, 40)
                .box(DARK_STEEL, -2, -2, 14, 4, 4, 5)
                .box(BLACK, -1.2f, -1.2f, 19, 2.4f, 2.4f, 3)
                .box(DARK_STEEL, -2.5f, 2.5f, -28, 5, 4.5f, 22)
                .box(BLACK, -2, 3, -29, 4, 3.5f, 1)
                .box(STEEL, -2.8f, 2.2f, -10, 5.6f, 5.2f, 2), PartPose.ZERO);
        C w = c();
        for (int i = 0; i < 7; i++) {
            float span = 46 - i * 6;
            w.box(DARK_STEEL, -span / 2, -0.5f, -22 + i * 4.5f, span, 1, 4.6f);
        }
        w.box(DARK_STEEL, -23, -5, -22, 1, 5, 6);
        w.box(DARK_STEEL, 22, -5, -22, 1, 5, 6);
        part(body, "wing", w, PartPose.ZERO);
        return create(mesh);
    }

    /** «Гербера»: дешёвая ложная цель из пенопласта и фанеры. */
    public static LayerDefinition gerbera() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "body", c()
                .box(SAND, -2.5f, -2.5f, -22, 5, 5, 36)
                .box(SAND, -1.5f, -1.5f, 14, 3, 3, 3)
                .box(WHITE, -20, -0.6f, -16, 40, 1.2f, 10)
                .box(WHITE, -14, -0.6f, -6, 28, 1.2f, 8)
                .box(SAND, -19, -4, -16, 1, 4, 5)
                .box(SAND, 18, -4, -16, 1, 4, 5)
                .box(DARK_STEEL, -1.5f, -1.5f, -24, 3, 3, 2), PartPose.ZERO);
        part(root, "prop", c()
                .box(BLACK, -0.5f, -6, -0.4f, 1, 12, 0.8f)
                .box(DARK_STEEL, -0.8f, -0.8f, -0.6f, 1.6f, 1.6f, 1.2f), PartPose.offset(0, 0, -25));
        return create(mesh);
    }

    // ================================================================== стрелковые установки

    /** РЛС на «Урале»: кунг, мачта и большая вращающаяся решётчатая антенна. */
    public static LayerDefinition radar() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        axle(root, 0, 13, 9.5f, 50, true);
        axle(root, 1, 13, 9.5f, -16, false);
        axle(root, 2, 13, 9.5f, -36, false);
        truckFront(root, OLIVE);
        part(root, "kung", c()
                .box(OLIVE, -18, -16, -60, 36, 21, 40)
                .box(OLIVE, -18, -16, -20, 36, 21, 36)
                .box(DARK_OLIVE, -18.5f, -17, -60, 37, 1, 76)
                .box(GLASS, -18.6f, -10, -40, 0.6f, 5, 10)
                .box(GLASS, 18, -10, -40, 0.6f, 5, 10)
                .box(RED, -16, 1, -60.6f, 4, 2, 0.6f)
                .box(RED, 12, 1, -60.6f, 4, 2, 0.6f), PartPose.ZERO);
        jack(root, 0, -17, -54);
        jack(root, 1, 17, -54);
        PartDefinition turret = part(root, "turret", c()
                .box(DARK_STEEL, -2, -20, -2, 4, 20, 4)
                .box(DARK_STEEL, -6, -2, -6, 12, 2, 12), PartPose.offset(0, -17, -24));
        PartDefinition radar = part(turret, "radar", c().box(DARK_STEEL, -3, -3, -3, 6, 3, 6), PartPose.offset(0, -20, 0));
        C dish = c();
        for (int i = 0; i < 6; i++) {
            dish.box(LIGHT, -28 + i * 9.6f, -16, 0, 8.6f, 14, 1);
        }
        dish.box(DARK_STEEL, -29, -17, 1, 58, 1, 1).box(DARK_STEEL, -29, -2, 1, 58, 1, 1).box(DARK_STEEL, -1, -16, -6, 2, 2, 7);
        part(radar, "dish", dish, PartPose.offsetAndRotation(0, -3, 0, -0.25f, 0, 0));
        return create(mesh);
    }

    /** Пикап: кабина со стёклами, кузов; в кузове — пулемёт на тумбе или спарка ПЗРК. */
    public static LayerDefinition pickup(boolean sam) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        axle(root, 0, 9, 7, 24, true);
        axle(root, 1, 9, 7, -22, false);
        int body = sam ? SAND : OLIVE;
        part(root, "cab", c()
                .box(DARK_STEEL, -10, 12, -36, 20, 4, 72)
                .box(body, -13, 2, 12, 26, 12, 26)
                .box(body, -13, -12, 4, 26, 14, 16)
                .box(DARK_OLIVE, -13.5f, -13, 4, 27, 1, 16)
                .box(GLASS, -11, -11, 19.6f, 22, 8, 0.6f)
                .box(GLASS, -13.6f, -10, 6, 0.6f, 7, 12)
                .box(GLASS, 13, -10, 6, 0.6f, 7, 12)
                .box(GLASS, -11, -11, 3.6f, 22, 7, 0.6f)
                .box(LAMP, -12, 4, 37.6f, 4, 3, 0.6f)
                .box(LAMP, 8, 4, 37.6f, 4, 3, 0.6f)
                .box(BLACK, -13, 8, 37.6f, 26, 4, 1.4f)
                // кузов
                .box(body, -13, 2, -38, 26, 10, 42)
                .box(DARK_OLIVE, -12, 1, -37, 24, 1, 40)
                .box(body, -13, -4, -38, 1, 6, 42)
                .box(body, 12, -4, -38, 1, 6, 42)
                .box(body, -13, -4, -38, 26, 6, 1)
                .box(RED, -12, 4, -38.6f, 4, 2, 0.6f)
                .box(RED, 8, 4, -38.6f, 4, 2, 0.6f), PartPose.ZERO);
        PartDefinition turret = part(root, "turret", c()
                .box(DARK_STEEL, -1.5f, 0, -1.5f, 3, 12, 3)
                .box(DARK_STEEL, -4, 11, -4, 8, 2, 8), PartPose.offset(0, -12, -18));
        if (sam) {
            part(turret, "rack", c()
                    .box(OLIVE, -8, -3, -14, 4, 4, 30)
                    .box(OLIVE, 4, -3, -14, 4, 4, 30)
                    .box(BLACK, -7.5f, -2.5f, 15.6f, 3, 3, 0.5f)
                    .box(BLACK, 4.5f, -2.5f, 15.6f, 3, 3, 0.5f)
                    .box(DARK_STEEL, -4, -2, -2, 8, 2, 4)
                    .box(GLASS, -1, -6, 0, 2, 2, 0.4f), PartPose.ZERO);
        } else {
            part(turret, "rack", c()
                    .box(DARK_STEEL, -2, -3, -8, 4, 5, 14)
                    .box(BLACK, -1, -2, 6, 2, 2, 20)
                    .box(DARK_STEEL, -1.5f, -2.5f, 6, 3, 3, 7)
                    .box(OLIVE, 2, -2, -3, 5, 5, 6)
                    .box(OLIVE, -7, -10, 2, 14, 8, 1), PartPose.ZERO);
        }
        return create(mesh);
    }

    /** БМП: гусеничный корпус с катками, башня с автоматической пушкой. */
    public static LayerDefinition bmp(boolean bradley) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        int col = bradley ? SAND : CAMO;
        C hull = c()
                .box(col, -20, 0, -46, 40, 16, 92)
                .box(col, -20, -4, -20, 40, 4, 62)
                .box(col, -18, -2, 46, 36, 12, bradley ? 6 : 10)
                .box(DARK_OLIVE, -20.5f, -5, -46, 41, 1, 92)
                .box(BLACK, -26, 12, -48, 6, 12, 96)
                .box(BLACK, 20, 12, -48, 6, 12, 96)
                .box(col, -26, 8, -46, 6, 4, 92)
                .box(col, 20, 8, -46, 6, 4, 92)
                .box(LAMP, -16, 2, 52.6f, 4, 3, 0.6f)
                .box(LAMP, 12, 2, 52.6f, 4, 3, 0.6f)
                .box(GLASS, -17, -3, 30, 6, 2, 0.6f);
        for (int i = 0; i < 6; i++) {
            float z = -38 + i * 15.5f;
            hull.box(TIRE, -27, 15, z - 5, 8, 10, 10).box(TIRE, 19, 15, z - 5, 8, 10, 10);
        }
        part(root, "hull", hull, PartPose.ZERO);
        PartDefinition turret = part(root, "turret", c()
                .box(col, -14, -10, -14, 28, 10, 26)
                .box(DARK_OLIVE, -14.5f, -11, -14, 29, 1, 26)
                .box(DARK_STEEL, -3, -16, -6, 6, 6, 6)
                .box(GLASS, -2.5f, -14, 0, 5, 2, 0.6f)
                .box(col, bradley ? 14 : -18, -9, -6, 4, 6, 12), PartPose.offset(0, -4, -4));
        part(turret, "rack", c()
                .box(DARK_STEEL, -3, -3, 8, 6, 6, 8)
                .box(BLACK, -1, -1.5f, 16, 2, 2, bradley ? 26 : 34)
                .box(DARK_STEEL, -1.5f, -2, bradley ? 40 : 48, 3, 3, 3), PartPose.offset(0, -5, 0));
        return create(mesh);
    }

    /** Пулемётное гнездо: тренога, ствол с кожухом, короб с лентой, щиток. */
    public static LayerDefinition mg() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "legs", c()
                .box(DARK_STEEL, -1, 8, -1, 2, 16, 2)
                .box(DARK_STEEL, -9, 22, -9, 18, 2, 2)
                .box(DARK_STEEL, -1, 22, -9, 2, 2, 18)
                .box(DARK_OLIVE, -12, 22, -14, 24, 2, 6), PartPose.ZERO);
        PartDefinition turret = part(root, "turret", c().box(DARK_STEEL, -2, -2, -2, 4, 4, 4), PartPose.offset(0, 7.2f, 0));
        part(turret, "rack", c()
                .box(DARK_STEEL, -2, -3, -10, 4, 5, 16)
                .box(BLACK, -1, -2, 6, 2, 2, 18)
                .box(DARK_STEEL, -1.5f, -2.5f, 6, 3, 3, 8)
                .box(BLACK, -1.2f, -1.7f, 23.6f, 2.4f, 2.4f, 1.2f)
                .box(OLIVE, 2, -2, -4, 5, 5, 6)
                .box(STEEL, 2.5f, -3, -3, 4, 1, 4)
                .box(OLIVE, -8, -9, 4, 16, 8, 1)
                .box(DARK_STEEL, -1.5f, 2, -12, 3, 4, 3)
                .box(BLACK, -0.5f, -5, 18, 1, 2, 1), PartPose.ZERO);
        return create(mesh);
    }

    /** Ручной ЗРК: тумба, сиденье стрелка, два контейнера и прицел. */
    public static LayerDefinition samManual() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "base", c()
                .box(DARK_OLIVE, -6, 16, -6, 12, 8, 12)
                .box(DARK_STEEL, -8, 22, -8, 16, 2, 16), PartPose.ZERO);
        PartDefinition turret = part(root, "turret", c()
                .box(OLIVE, -3, 0, -3, 6, 16, 6)
                .box(DARK_STEEL, -4, 8, -14, 8, 2, 8)
                .box(DARK_STEEL, -3, 2, -14, 6, 6, 2), PartPose.offset(0, 0, 0));
        part(turret, "rack", c()
                .box(OLIVE, -11, -3, -12, 4, 4, 30)
                .box(OLIVE, 7, -3, -12, 4, 4, 30)
                .box(BLACK, -10.5f, -2.5f, 17.6f, 3, 3, 0.5f)
                .box(BLACK, 7.5f, -2.5f, 17.6f, 3, 3, 0.5f)
                .box(DARK_STEEL, -7, -2, -2, 14, 2, 4)
                .box(DARK_STEEL, -1.5f, -7, -4, 3, 4, 6)
                .box(GLASS, -1, -6.5f, 2, 2, 2, 0.4f), PartPose.ZERO);
        return create(mesh);
    }

    // ================================================================== ракеты (Y вверх, нос по +Z)

    public static LayerDefinition rocketGrad() {
        MeshDefinition mesh = new MeshDefinition();
        part(mesh.getRoot(), "body", c()
                .box(OLIVE, -3, -3, -23, 6, 6, 40)
                .box(DARK_OLIVE, -3.2f, -3.2f, -2, 6.4f, 6.4f, 2)
                .box(RED, -3.2f, -3.2f, 14, 6.4f, 6.4f, 2)
                .box(OLIVE, -2.2f, -2.2f, 17, 4.4f, 4.4f, 4)
                .box(DARK_STEEL, -1, -1, 21, 2, 2, 3)
                .box(DARK_STEEL, -0.4f, -7, -23, 0.8f, 14, 6)
                .box(DARK_STEEL, -7, -0.4f, -23, 14, 0.8f, 6), PartPose.ZERO);
        return create(mesh);
    }

    public static LayerDefinition rocketBallistic() {
        MeshDefinition mesh = new MeshDefinition();
        missileBody(mesh.getRoot(), 0, -56);
        return create(mesh);
    }

    public static LayerDefinition rocketSam() {
        MeshDefinition mesh = new MeshDefinition();
        part(mesh.getRoot(), "body", c()
                .box(LIGHT, -3, -3, -24, 6, 6, 40)
                .box(LIGHT, -2, -2, 16, 4, 4, 5)
                .box(BLACK, -1, -1, 21, 2, 2, 3)
                .box(RED, -3.2f, -3.2f, 10, 6.4f, 6.4f, 1)
                .box(STEEL, -0.4f, -6, -24, 0.8f, 12, 7)
                .box(STEEL, -6, -0.4f, -24, 12, 0.8f, 7)
                .box(STEEL, -0.4f, -4.5f, 6, 0.8f, 9, 3)
                .box(STEEL, -4.5f, -0.4f, 6, 9, 0.8f, 3), PartPose.ZERO);
        return create(mesh);
    }

    public static LayerDefinition bomb() {
        MeshDefinition mesh = new MeshDefinition();
        part(mesh.getRoot(), "body", c()
                .box(DARK_OLIVE, -1.5f, -1.5f, -3, 3, 3, 6)
                .box(DARK_STEEL, -2, -0.2f, -4, 4, 0.4f, 2)
                .box(DARK_STEEL, -0.2f, -2, -4, 0.4f, 4, 2), PartPose.ZERO);
        return create(mesh);
    }

    /** Факел двигателя: длина вдоль -Z, рисуется светящимся. */
    public static LayerDefinition flame() {
        MeshDefinition mesh = new MeshDefinition();
        part(mesh.getRoot(), "outer", c().box(FLAME_ORANGE, -3, -3, -16, 6, 6, 16), PartPose.ZERO);
        part(mesh.getRoot(), "core", c().box(FLAME_YELLOW, -1.8f, -1.8f, -10, 3.6f, 3.6f, 10), PartPose.ZERO);
        return create(mesh);
    }

    // ================================================================== дроны (Y вверх, нос по +Z)

    public static LayerDefinition shahed() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition body = part(root, "body", c()
                .box(LIGHT, -2.5f, -2.5f, -26, 5, 5, 40)
                .box(LIGHT, -2, -2, 14, 4, 4, 5)
                .box(LIGHT, -1.2f, -1.2f, 19, 2.4f, 2.4f, 3)
                .box(DARK_STEEL, -1.5f, -3.5f, -26, 3, 1, 10)
                .box(DARK_STEEL, -2, -2, -28, 4, 4, 2), PartPose.ZERO);
        deltaWing(body, 0, -22, 1f);
        part(root, "prop", c()
                .box(BLACK, -0.6f, -8, -0.5f, 1.2f, 16, 1)
                .box(DARK_STEEL, -1, -1, -0.8f, 2, 2, 1.6f), PartPose.offset(0, 0, -29));
        return create(mesh);
    }

    /** FPV: рама X, 4 мотора, винты, аккумулятор и камера. */
    public static LayerDefinition fpv() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "frame", c()
                .box(BLACK, -2.5f, -1, -3, 5, 2, 6)
                .box(RED, -2, 1, -2.5f, 4, 2, 5)
                .box(DARK_STEEL, -1, -0.5f, 3, 2, 2, 1)
                .box(OLIVE, -1.5f, -3.5f, -2, 3, 2.5f, 4), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float a = (float) (Math.PI / 4 + i * Math.PI / 2);
            float x = (float) Math.sin(a) * 6;
            float z = (float) Math.cos(a) * 6;
            part(root, "arm" + i, c().box(BLACK, -0.6f, -0.4f, 0, 1.2f, 0.8f, 6), PartPose.offsetAndRotation(0, 0, 0, 0, a, 0));
            part(root, "motor" + i, c().box(DARK_STEEL, -1, 0, -1, 2, 1.6f, 2), PartPose.offset(x, 0, z));
            part(root, "prop" + i, c().box(LIGHT, -4, 0, -0.5f, 8, 0.2f, 1), PartPose.offset(x, 1.8f, z));
        }
        return create(mesh);
    }

    /** Разведчик: серый корпус, складные лучи, подвес камеры. */
    public static LayerDefinition recon() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "frame", c()
                .box(LIGHT, -3, -1.5f, -5, 6, 3, 10)
                .box(STEEL, -2.5f, 1.5f, -4, 5, 1, 7)
                .box(DARK_STEEL, -2, -2, 4, 4, 1, 1)
                .box(BLACK, -0.8f, 1.5f, 5, 1.6f, 0.8f, 0.4f), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float a = (float) (Math.PI / 4 + i * Math.PI / 2);
            float x = (float) Math.sin(a) * 7.5f;
            float z = (float) Math.cos(a) * 7.5f;
            part(root, "arm" + i, c().box(LIGHT, -0.7f, -0.5f, 0, 1.4f, 1, 7.5f), PartPose.offsetAndRotation(0, 0.5f, 0, 0, a, 0));
            part(root, "motor" + i, c().box(DARK_STEEL, -1.1f, 0, -1.1f, 2.2f, 1.8f, 2.2f), PartPose.offset(x, 0.5f, z));
            part(root, "prop" + i, c().box(BLACK, -5, 0, -0.6f, 10, 0.2f, 1.2f), PartPose.offset(x, 2.4f, z));
        }
        PartDefinition gimbal = part(root, "gimbal", c().box(DARK_STEEL, -0.5f, -1.5f, -0.5f, 1, 1.5f, 1), PartPose.offset(0, -1.5f, 3));
        part(gimbal, "cam", c().box(BLACK, -1.5f, -3.5f, -1.5f, 3, 2.5f, 3).box(GLASS, -0.8f, -3, 1.5f, 1.6f, 1.6f, 0.3f), PartPose.ZERO);
        return create(mesh);
    }

    // ================================================================== взрыв

    /** Скруглённый «шар» радиусом 1 блок (8 единиц при масштабе 2). */
    public static LayerDefinition blob() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        part(root, "a", c().box(WHITE, -7, -7, -7, 14, 14, 14), PartPose.ZERO);
        part(root, "b", c().box(WHITE, -7, -7, -7, 14, 14, 14), PartPose.rotation(0, (float) Math.PI / 4, 0));
        part(root, "c", c().box(WHITE, -7, -7, -7, 14, 14, 14), PartPose.rotation((float) Math.PI / 4, 0, 0));
        part(root, "d", c().box(WHITE, -7, -7, -7, 14, 14, 14), PartPose.rotation(0, 0, (float) Math.PI / 4));
        return create(mesh);
    }

    /** Плоское кольцо радиусом 1 блок из 32 сегментов. */
    public static LayerDefinition ring() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (int i = 0; i < 32; i++) {
            float a = (float) (i * Math.PI * 2 / 32);
            part(root, "s" + i, c().box(WHITE, -1.7f, -0.5f, -0.8f, 3.4f, 1, 1.6f),
                    PartPose.offsetAndRotation((float) Math.sin(a) * 16, 0, (float) Math.cos(a) * 16, 0, a, 0));
        }
        return create(mesh);
    }
}
