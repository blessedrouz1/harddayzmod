package com.harddayz.entity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/** Server-safe launch geometry from the same bundled bones used by the renderer. */
public final class LauncherGeometry {
    private record Bone(String parent, Vec3 pivot, Vec3 rotation, Vec3 min, Vec3 max) {}
    private static final Map<Launcher.Kind, Map<String, Bone>> RIGS = new java.util.EnumMap<>(Launcher.Kind.class);

    private LauncherGeometry() {}

    public static String model(Launcher.Kind kind) {
        return switch (kind) {
            case SHAHED -> "shahed_launcher";
            case RAMP -> "shahed_ground";
            case PICKUP_SAM -> "pickup_sam";
            default -> kind.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static Vec3 vector(JsonObject json, String field) {
        if (!json.has(field)) return Vec3.ZERO;
        var a = json.getAsJsonArray(field);
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static Map<String, Bone> rig(Launcher.Kind kind) {
        return RIGS.computeIfAbsent(kind, k -> {
            String path = "/assets/harddayz/geo/" + model(k) + ".geo.json";
            try (var stream = LauncherGeometry.class.getResourceAsStream(path)) {
                if (stream == null) throw new IllegalStateException("Missing launcher model " + path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                var bones = json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
                Map<String, Bone> out = new HashMap<>();
                for (var element : bones) {
                    var b = element.getAsJsonObject();
                    Vec3 min = new Vec3(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
                    Vec3 max = min.scale(-1);
                    if (b.has("cubes")) for (var cube : b.getAsJsonArray("cubes")) {
                        var c = cube.getAsJsonObject();
                        Vec3 start = vector(c, "origin"), end = start.add(vector(c, "size"));
                        min = new Vec3(Math.min(min.x, start.x), Math.min(min.y, start.y), Math.min(min.z, start.z));
                        max = new Vec3(Math.max(max.x, end.x), Math.max(max.y, end.y), Math.max(max.z, end.z));
                    }
                    out.put(b.get("name").getAsString(), new Bone(b.has("parent") ? b.get("parent").getAsString() : null,
                            vector(b, "pivot"), vector(b, "rotation"), min, max));
                }
                return out;
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot read " + path, e);
            }
        });
    }

    private static Vec3 mirror(Vec3 p) { return new Vec3(-p.x, p.y, p.z); }

    private static Vec3 rotate(Vec3 p, double x, double y, double z) {
        double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
        p = new Vec3(p.x, p.y * cx - p.z * sx, p.y * sx + p.z * cx);
        p = new Vec3(p.x * cy + p.z * sy, p.y, -p.x * sy + p.z * cy);
        return new Vec3(p.x * cz - p.y * sz, p.x * sz + p.y * cz, p.z);
    }

    /** Matches GeoModel's mirrored X, ModelPart's Z/Y/X rotations and body rotation. */
    public static Vec3 transform(Launcher.Kind kind, String bone, Vec3 point, float bodyYaw, float turret, float elevation, float erect) {
        var bones = rig(kind);
        Vec3 p = mirror(point);
        for (String name = bone; name != null;) {
            Bone b = bones.get(name);
            if (b == null) throw new IllegalArgumentException("Missing bone " + name + " in " + kind);
            double x = -b.rotation.x, y = -b.rotation.y, z = b.rotation.z;
            if (name.equals("turret")) y -= turret;
            if (name.equals("rack")) x += elevation;
            if (name.equals("erector")) x += erect * 90;
            Vec3 pivot = mirror(b.pivot);
            p = rotate(p.subtract(pivot), Math.toRadians(x), Math.toRadians(y), Math.toRadians(z)).add(pivot);
            name = b.parent;
        }
        return rotate(p.scale(1.0 / 16), 0, Math.toRadians(180 - bodyYaw), 0);
    }

    public static Vec3 pivot(Launcher.Kind kind, float bodyYaw, float turret, float elevation, float erect) {
        String bone = kind == Launcher.Kind.ISKANDER || kind == Launcher.Kind.TOCHKA ? "erector" : "rack";
        return transform(kind, bone, rig(kind).get(bone).pivot, bodyYaw, turret, elevation, erect);
    }

    /** Last visible payload leaves first, matching the renderer's i < ammo visibility. */
    public static Vec3 muzzle(Launcher.Kind kind, int ammo, float bodyYaw, float turret, float elevation, float erect) {
        var bones = rig(kind);
        String bone = "rack";
        Vec3 point;
        if (kind == Launcher.Kind.ISKANDER || kind == Launcher.Kind.TOCHKA) {
            bone = "missile";
            Bone b = bones.get(bone);
            point = b.min.add(b.max).scale(0.5);
        } else if (bones.containsKey("tip0")) {
            int index = Math.max(0, ammo - 1);
            bone = bones.containsKey("tip" + index) ? "tip" + index : "tip0";
            Bone b = bones.get(bone);
            point = b.min.add(b.max).scale(0.5);
            if (!kind.dronePad()) point = new Vec3(point.x, point.y, b.min.z);
        } else {
            int i = Math.floorMod(ammo - 1, Math.max(1, kind.capacity));
            point = switch (kind) {
                case S400 -> new Vec3((i % 2 == 0 ? 1 : -1) * 6.5, 36.5 + (i / 2) * 13, -25);
                case PATRIOT -> new Vec3(i % 2 == 0 ? 8 : -7.5, 40.5 + (i / 2) * 14.5, -20);
                case NASAMS -> new Vec3(-8 + (i % 3) * 8, 38.5 + (i / 3) * 9, -30);
                case HIMARS -> new Vec3(-9 + (i % 3) * 9, 39 + (i / 3) * 10, -6);
                case KALIBR -> new Vec3(i % 2 == 0 ? -9 : 9, 43 + (i / 2) * 18, -28);
                case SAM -> new Vec3(i % 2 == 0 ? -6 : 6, 45, -29);
                case PICKUP_SAM -> new Vec3(i % 2 == 0 ? -5 : 5, 42.5, -9);
                case MORTAR -> new Vec3(0, 8, -23);
                default -> throw new IllegalArgumentException("No muzzle for " + kind);
            };
        }
        return transform(kind, bone, point, bodyYaw, turret, elevation, erect);
    }

    public static Vec3 direction(Launcher.Kind kind, float bodyYaw, float turret, float elevation, float erect) {
        String bone = kind == Launcher.Kind.ISKANDER || kind == Launcher.Kind.TOCHKA ? "erector" : "rack";
        Vec3 start = transform(kind, bone, Vec3.ZERO, bodyYaw, turret, elevation, erect);
        return transform(kind, bone, new Vec3(0, 0, -16), bodyYaw, turret, elevation, erect).subtract(start).normalize();
    }
}
