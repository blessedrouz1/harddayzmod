package com.harddayz.client.model;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

/**
 * Модели друга из Blockbench (формат GeckoLib .geo.json) без самой GeckoLib: кости превращаются в обычные
 * {@link ModelPart} с теми же именами, кубы — с UV на каждую грань. Пространство как у GeckoLib: Y вверх,
 * модель смотрит в -Z, 16 px = 1 блок.
 */
public final class GeoModel {
    public final ModelPart root;
    public final Map<String, ModelPart> parts = new HashMap<>();
    /** Габариты кости в пространстве модели (px): minX, minY, minZ, maxX, maxY, maxZ. */
    public final Map<String, float[]> boxes = new HashMap<>();
    /** Самый большой куб кости (для знаков на кабине). */
    public final Map<String, float[]> mainBoxes = new HashMap<>();
    /** Абсолютный пивот кости (px). */
    public final Map<String, float[]> pivots = new HashMap<>();
    public final Map<String, String> parents = new HashMap<>();
    /** Опознавательные знаки фракций (по одному набору на фракцию), или null. */
    public ModelPart[] marks;
    private final float texW;
    private final float texH;

    private record Bone(String name, String parent, float[] pivot, float[] rot, JsonArray cubes) {
    }

    private GeoModel(JsonObject geo) {
        JsonObject desc = geo.getAsJsonObject("description");
        texW = desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 64;
        texH = desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 64;
        Map<String, Bone> bones = new LinkedHashMap<>();
        for (JsonElement e : geo.getAsJsonArray("bones")) {
            JsonObject b = e.getAsJsonObject();
            float[] p = vec(b, "pivot");
            float[] r = vec(b, "rotation");
            bones.put(b.get("name").getAsString(), new Bone(b.get("name").getAsString(), b.has("parent") ? b.get("parent").getAsString() : null,
                    new float[] {-p[0], p[1], p[2]}, rad(r), b.has("cubes") ? b.getAsJsonArray("cubes") : new JsonArray()));
        }
        Map<String, ModelPart> top = new LinkedHashMap<>();
        for (Bone b : bones.values()) {
            if (b.parent == null || !bones.containsKey(b.parent)) {
                top.put(b.name, build(b, bones, new float[3]));
            }
        }
        root = new ModelPart(List.of(), top);
    }

    private ModelPart build(Bone b, Map<String, Bone> bones, float[] parentPivot) {
        List<ModelPart.Cube> cubes = new ArrayList<>();
        Map<String, ModelPart> children = new LinkedHashMap<>();
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] main = null;
        float mainVol = 0;
        pivots.put(b.name, b.pivot);
        if (b.parent != null) {
            parents.put(b.name, b.parent);
        }
        int i = 0;
        for (JsonElement e : b.cubes) {
            JsonObject c = e.getAsJsonObject();
            float[] o = vec(c, "origin");
            float[] s = vec(c, "size");
            float g = c.has("inflate") ? c.get("inflate").getAsFloat() : 0;
            float[] min = {-(o[0] + s[0]), o[1], o[2]};
            for (int k = 0; k < 3; k++) {
                box[k] = Math.min(box[k], min[k]);
                box[k + 3] = Math.max(box[k + 3], min[k] + s[k]);
            }
            Map<Direction, float[]> uv = faces(c.getAsJsonObject("uv"));
            float[] r = rad(vec(c, "rotation"));
            boolean rotated = r[0] != 0 || r[1] != 0 || r[2] != 0;
            if (!rotated && s[0] * s[1] * s[2] > mainVol) {
                mainVol = s[0] * s[1] * s[2];
                main = new float[] {min[0], min[1], min[2], min[0] + s[0], min[1] + s[1], min[2] + s[2]};
            }
            if (rotated) {
                float[] cp = vec(c, "pivot");
                cp[0] = -cp[0];
                ModelPart part = new ModelPart(List.of(cube(min[0] - cp[0], min[1] - cp[1], min[2] - cp[2], s[0], s[1], s[2], g, uv)), Map.of());
                part.setPos(cp[0] - b.pivot[0], cp[1] - b.pivot[1], cp[2] - b.pivot[2]);
                part.setRotation(r[0], r[1], r[2]);
                part.setInitialPose(part.storePose());
                children.put(b.name + "#" + i, part);
            } else {
                cubes.add(cube(min[0] - b.pivot[0], min[1] - b.pivot[1], min[2] - b.pivot[2], s[0], s[1], s[2], g, uv));
            }
            i++;
        }
        for (Bone child : bones.values()) {
            if (b.name.equals(child.parent)) {
                children.put(child.name, build(child, bones, b.pivot));
            }
        }
        ModelPart part = new ModelPart(cubes, children);
        part.setPos(b.pivot[0] - parentPivot[0], b.pivot[1] - parentPivot[1], b.pivot[2] - parentPivot[2]);
        part.setRotation(b.rot[0], b.rot[1], b.rot[2]);
        part.setInitialPose(part.storePose());
        parts.put(b.name, part);
        if (box[0] != Float.MAX_VALUE) {
            boxes.put(b.name, box);
        }
        if (main != null) {
            mainBoxes.put(b.name, main);
        }
        return part;
    }

    /** UV граней. Blockbench при экспорте переворачивает верх/низ — возвращаем как в редакторе. */
    private static Map<Direction, float[]> faces(JsonObject uv) {
        Map<Direction, float[]> out = new EnumMap<>(Direction.class);
        if (uv == null) {
            return out;
        }
        for (Direction d : Direction.values()) {
            JsonObject f = uv.getAsJsonObject(d.getSerializedName());
            if (f == null || !f.has("uv")) {
                continue;
            }
            JsonArray a = f.getAsJsonArray("uv");
            JsonArray sz = f.has("uv_size") ? f.getAsJsonArray("uv_size") : null;
            float u0 = a.get(0).getAsFloat();
            float v0 = a.get(1).getAsFloat();
            float u1 = u0 + (sz != null ? sz.get(0).getAsFloat() : 0);
            float v1 = v0 + (sz != null ? sz.get(1).getAsFloat() : 0);
            if (d.getAxis() == Direction.Axis.Y) {
                out.put(d, new float[] {u1, v1, u0, v0});
            } else {
                out.put(d, new float[] {u0, v0, u1, v1});
            }
        }
        return out;
    }

    /** Куб с UV на каждую грань: {u0, v0, u1, v1} — левый верх и правый низ картинки грани. */
    private ModelPart.Cube cube(float x, float y, float z, float w, float h, float d, float g, Map<Direction, float[]> uv) {
        return cube(x, y, z, w, h, d, g, uv, texW, texH);
    }

    static ModelPart.Cube cube(float x, float y, float z, float w, float h, float d, float g, Map<Direction, float[]> uv, float tw, float th) {
        ModelPart.Cube c = new ModelPart.Cube(0, 0, x, y, z, w, h, d, g, g, g, false, tw, th, uv.keySet());
        float x0 = x - g;
        float y0 = y - g;
        float z0 = z - g;
        float x1 = x + w + g;
        float y1 = y + h + g;
        float z1 = z + d + g;
        int n = 0;
        // порядок граней как в ModelPart.Cube: низ, верх, запад, север, восток, юг
        for (Direction dir : new Direction[] {Direction.DOWN, Direction.UP, Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH}) {
            float[] t = uv.get(dir);
            if (t == null) {
                continue;
            }
            float u0 = t[0] / tw;
            float v0 = t[1] / th;
            float u1 = t[2] / tw;
            float v1 = t[3] / th;
            // вершины: левый верх, левый низ, правый низ, правый верх (если смотреть на грань снаружи)
            float[][] q = switch (dir) {
                case NORTH -> new float[][] {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                case SOUTH -> new float[][] {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                case EAST -> new float[][] {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
                case WEST -> new float[][] {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                case DOWN -> new float[][] {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
            };
            ModelPart.Vertex[] v = {
                    new ModelPart.Vertex(q[0][0], q[0][1], q[0][2], u0, v0),
                    new ModelPart.Vertex(q[1][0], q[1][1], q[1][2], u0, v1),
                    new ModelPart.Vertex(q[2][0], q[2][1], q[2][2], u1, v1),
                    new ModelPart.Vertex(q[3][0], q[3][1], q[3][2], u1, v0)};
            Vector3f normal = new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            c.polygons[n++] = new ModelPart.Polygon(v, normal);
        }
        return c;
    }

    private static float[] vec(JsonObject o, String key) {
        if (!o.has(key)) {
            return new float[3];
        }
        JsonArray a = o.getAsJsonArray(key);
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static float[] rad(float[] deg) {
        return new float[] {(float) Math.toRadians(-deg[0]), (float) Math.toRadians(-deg[1]), (float) Math.toRadians(deg[2])};
    }

    // ------------------------------------------------------------------ загрузка

    private static final Map<String, GeoModel> CACHE = new HashMap<>();

    public static GeoModel get(String name, Marks marks) {
        return CACHE.computeIfAbsent(name, n -> {
            try (InputStream in = GeoModel.class.getResourceAsStream("/assets/harddayz/geo/" + n + ".geo.json")) {
                JsonObject geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                        .getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                GeoModel m = new GeoModel(geo);
                m.buildMarks(marks);
                return m;
            } catch (Exception e) {
                throw new IllegalStateException("HardDayz: не удалось загрузить модель " + n, e);
            }
        });
    }

    // ------------------------------------------------------------------ опознавательные знаки

    /** Где рисовать знаки фракции: кость и способ. */
    public record Marks(String bone, Mode mode) {
    }

    public enum Mode {
        /** Борта и крыша (кабина машины). */
        SIDES_TOP,
        /** Только борта. */
        SIDES,
        /** Сверху и снизу на концах крыльев. */
        WINGS,
        /** Сверху. */
        TOP
    }

    public static final int FACTIONS = 4;

    private void buildMarks(Marks spec) {
        float[] b = spec == null ? null
                : (spec.mode() == Mode.SIDES_TOP || spec.mode() == Mode.SIDES ? mainBoxes : boxes).get(spec.bone());
        if (b == null) {
            return;
        }
        marks = new ModelPart[FACTIONS];
        for (int f = 0; f < FACTIONS; f++) {
            float[] tile = {f * 16, 0, f * 16 + 16, 16};
            List<ModelPart.Cube> cubes = new ArrayList<>();
            float cx = (b[0] + b[3]) / 2;
            float cz = (b[2] + b[5]) / 2;
            float w = b[3] - b[0];
            float h = b[4] - b[1];
            float d = b[5] - b[2];
            switch (spec.mode()) {
                case SIDES_TOP, SIDES -> {
                    float s = Math.min(h, d) * 0.4f;
                    float y = b[1] + h * 0.55f - s / 2;
                    cubes.add(plate(b[3] + 0.06f, y, cz - s / 2, 0.02f, s, s, Direction.EAST, tile));
                    cubes.add(plate(b[0] - 0.08f, y, cz - s / 2, 0.02f, s, s, Direction.WEST, tile));
                    if (spec.mode() == Mode.SIDES_TOP) {
                        float t = Math.min(w, d) * 0.4f;
                        cubes.add(plate(cx - t / 2, b[4] + 0.06f, cz - t / 2, t, 0.02f, t, Direction.UP, tile));
                    }
                }
                case WINGS -> {
                    float s = Math.min(d * 0.8f, w * 0.18f);
                    for (float side : new float[] {-1, 1}) {
                        float x = cx + side * w * 0.32f - s / 2;
                        cubes.add(plate(x, b[4] + 0.05f, cz - s / 2, s, 0.02f, s, Direction.UP, tile));
                        cubes.add(plate(x, b[1] - 0.07f, cz - s / 2, s, 0.02f, s, Direction.DOWN, tile));
                    }
                }
                case TOP -> {
                    float t = Math.min(w, d) * 0.45f;
                    cubes.add(plate(cx - t / 2, b[4] + 0.05f, cz - t / 2, t, 0.02f, t, Direction.UP, tile));
                }
            }
            ModelPart p = new ModelPart(cubes, Map.of());
            p.setInitialPose(p.storePose());
            marks[f] = new ModelPart(List.of(), Map.of("mark", p));
        }
    }

    /** Габариты кости вместе со всеми дочерними (px). */
    public float[] subtreeBox(String bone) {
        float[] r = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (var e : boxes.entrySet()) {
            String n = e.getKey();
            while (n != null && !n.equals(bone)) {
                n = parents.get(n);
            }
            if (n != null) {
                for (int k = 0; k < 3; k++) {
                    r[k] = Math.min(r[k], e.getValue()[k]);
                    r[k + 3] = Math.max(r[k + 3], e.getValue()[k + 3]);
                }
            }
        }
        return r;
    }

    /** Габариты всей модели (px). */
    public float[] bounds() {
        float[] r = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float[] b : boxes.values()) {
            for (int k = 0; k < 3; k++) {
                r[k] = Math.min(r[k], b[k]);
                r[k + 3] = Math.max(r[k + 3], b[k + 3]);
            }
        }
        return r;
    }

    /** Применить к позе цепочку костей от корня до {@code bone} включительно. */
    public void transformTo(com.mojang.blaze3d.vertex.PoseStack pose, String bone) {
        java.util.ArrayDeque<String> chain = new java.util.ArrayDeque<>();
        for (String n = bone; n != null; n = parents.get(n)) {
            chain.push(n);
        }
        root.translateAndRotate(pose);
        for (String n : chain) {
            parts.get(n).translateAndRotate(pose);
        }
    }

    private static ModelPart.Cube plate(float x, float y, float z, float w, float h, float d, Direction face, float[] tile) {
        Map<Direction, float[]> uv = new EnumMap<>(Direction.class);
        uv.put(face, tile);
        return cube(x, y, z, w, h, d, 0, uv, 64, 16);
    }
}
