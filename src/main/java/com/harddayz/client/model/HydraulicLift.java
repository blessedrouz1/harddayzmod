package com.harddayz.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Fixed barrel and two sliding stages, pinned to the chassis and the moving cradle. */
public final class HydraulicLift {
    private final GeoModel geo;
    private final String parent;
    private final String mount;
    private final ModelPart barrel;
    private final ModelPart stage;
    private final ModelPart rod;
    private final float restLength;

    private HydraulicLift(GeoModel geo, String name) {
        this.geo = geo;
        parent = geo.parents.get(name);
        mount = name + "_mount";
        barrel = geo.parts.get(name);
        stage = geo.parts.get(name + "_stage");
        rod = geo.parts.get(name + "_rod");
        restLength = new Vector3f(geo.pivots.get(mount)).sub(new Vector3f(geo.pivots.get(name))).length();
    }

    public static HydraulicLift[] create(GeoModel geo) {
        return geo.parts.keySet().stream().filter(n -> n.endsWith("_mount"))
                .map(n -> n.substring(0, n.length() - "_mount".length()))
                .filter(n -> geo.parts.containsKey(n + "_stage") && geo.parts.containsKey(n + "_rod"))
                .map(n -> new HydraulicLift(geo, n)).toArray(HydraulicLift[]::new);
    }

    private Vector3f displacement() {
        PoseStack target = new PoseStack();
        geo.transformTo(target, mount);
        PoseStack base = new PoseStack();
        if (parent != null) geo.transformTo(base, parent);
        return new Matrix4f(base.last().pose()).invert().mul(target.last().pose())
                .transformPosition(new Vector3f()).mul(16).sub(barrel.x, barrel.y, barrel.z);
    }

    public void animate() {
        Vector3f delta = displacement();
        float length = delta.length();
        if (length < 0.001f) return;
        barrel.xRot = (float) Math.asin(Math.max(-1f, Math.min(1f, delta.y / length)));
        barrel.yRot = (float) Math.atan2(-delta.x, -delta.z);
        // Equal strokes preserve overlap at both seals; no geometry is stretched.
        float stroke = (length - restLength) * 0.5f;
        stage.z -= stroke;
        rod.z -= stroke;
    }
}
