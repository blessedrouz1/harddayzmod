package com.harddayz.client.model;

import com.harddayz.HardDayz;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.IntArrays;
import java.util.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.fml.ModList;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

/** One shared static vertex buffer per model; animated bones are matrices in a small uniform buffer. */
public final class GeoGpu {
    public enum Pass { TRANSLUCENT, CUTOUT, EYES }
    private static final int BONES = 64;
    private static final int MATRIX_FLOATS = BONES * 16;
    private static final int BINDING = 7;
    private static final Map<ModelPart, Node> NODES = new IdentityHashMap<>();
    private static final Map<ModelPart, Mesh> MESHES = new IdentityHashMap<>();
    private static final Map<RenderType, Pass> PASSES = new IdentityHashMap<>();
    private static final Map<Pass, ShaderInstance> SHADERS = new EnumMap<>(Pass.class);
    private static final ArrayDeque<float[]> MATRIX_POOL = new ArrayDeque<>();
    private static BufferBuilder bakerBuffer;
    private static BufferBuilder baker() { return bakerBuffer != null ? bakerBuffer : (bakerBuffer = new BufferBuilder(65536)); }
    private static final java.nio.FloatBuffer MATRICES = BufferUtils.createFloatBuffer(MATRIX_FLOATS * 2);
    private static final boolean SHADER_PACK_MOD = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris");
    /** Set after the first GL failure in the VBO path: every later draw uses the plain vertex path. */
    private static volatile boolean broken;
    private static int boneBuffer;
    private static long uploads;
    private static long draws;

    private GeoGpu() {}

    private static final class Node {
        final ModelPart part;
        final List<ModelPart.Cube> cubes;
        final List<Node> fixed = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
        Node(ModelPart part, List<ModelPart.Cube> cubes) {
            this.part = part;
            this.cubes = List.copyOf(cubes);
        }
        void emit(PoseStack pose, VertexConsumer out) {
            for (var cube : cubes) cube.compile(pose.last(), out, 0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            for (Node child : fixed) {
                pose.pushPose();
                child.part.translateAndRotate(pose);
                child.emit(pose, out);
                pose.popPose();
            }
        }
        void animatedParts(List<ModelPart> out) {
            out.add(part);
            for (Node child : children) child.animatedParts(out);
        }
    }

    /** Writes a bone index into otherwise unused per-vertex overlay coordinates at bake time. */
    private static final class BoneWriter implements VertexConsumer {
        int bone;
        int vertices;
        final List<Integer> quadBones = new ArrayList<>();
        public VertexConsumer vertex(double x, double y, double z) { baker().vertex(x, y, z); return this; }
        public VertexConsumer color(int r, int g, int b, int a) { baker().color(r, g, b, a); return this; }
        public VertexConsumer uv(float u, float v) { baker().uv(u, v); return this; }
        public VertexConsumer overlayCoords(int u, int v) { baker().overlayCoords(bone, 0); return this; }
        public VertexConsumer uv2(int u, int v) { baker().uv2(u, v); return this; }
        public VertexConsumer normal(float x, float y, float z) { baker().normal(x, y, z); return this; }
        public void endVertex() {
            if (vertices++ % 4 == 0) quadBones.add(bone);
            baker().endVertex();
        }
        public void defaultColor(int r, int g, int b, int a) { baker().defaultColor(r, g, b, a); }
        public void unsetDefaultColor() { baker().unsetDefaultColor(); }
    }

    private static final class Mesh {
        final Node root;
        final List<Node> bones = new ArrayList<>();
        VertexBuffer buffer;
        BufferBuilder.SortState sorting;
        int[] quadBones;
        int[] order;
        float[] distances;
        boolean baked;
        Mesh(Node root) { this.root = root; list(root); }
        void list(Node node) { bones.add(node); for (Node child : node.children) list(child); }
        void bake() {
            if (baked) return;
            baker().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            BoneWriter writer = new BoneWriter();
            for (int i = 0; i < bones.size(); i++) {
                writer.bone = i;
                bones.get(i).emit(new PoseStack(), writer);
            }
            baker().setQuadSorting(VertexSorting.DISTANCE_TO_ORIGIN);
            sorting = baker().getSortState();
            var data = baker().endOrDiscardIfEmpty();
            if (data != null) {
                quadBones = writer.quadBones.stream().mapToInt(Integer::intValue).toArray();
                order = new int[quadBones.length];
                distances = new float[quadBones.length];
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                try { buffer.upload(data); } finally { VertexBuffer.unbind(); }
                uploads++;
            }
            baked = true;
        }
        void snapshot(Node node, PoseStack pose, float[] matrices, boolean parentVisible, int[] index) {
            int bone = index[0]++;
            boolean visible = parentVisible && node.part.visible;
            pose.pushPose();
            node.part.translateAndRotate(pose);
            if (visible && !node.part.skipDraw) {
                pose.last().pose().get(matrices, bone * 16);
                var n = pose.last().normal();
                int offset = MATRIX_FLOATS + bone * 16;
                matrices[offset] = n.m00(); matrices[offset+1] = n.m01(); matrices[offset+2] = n.m02();
                matrices[offset+4] = n.m10(); matrices[offset+5] = n.m11(); matrices[offset+6] = n.m12();
                matrices[offset+8] = n.m20(); matrices[offset+9] = n.m21(); matrices[offset+10] = n.m22();
                matrices[offset+15] = 1;
            }
            for (Node child : node.children) snapshot(child, pose, matrices, visible, index);
            pose.popPose();
        }
        void sort(float[] matrices) {
            // Only indices change: transparent faces still sort back-to-front after animation.
            baker().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
            baker().restoreSortState(sorting);
            baker().setQuadSorting(points -> {
                for (int i = 0; i < points.length; i++) {
                    var p = points[i];
                    int m = quadBones[i] * 16;
                    float x = matrices[m] * p.x + matrices[m+4] * p.y + matrices[m+8] * p.z + matrices[m+12];
                    float y = matrices[m+1] * p.x + matrices[m+5] * p.y + matrices[m+9] * p.z + matrices[m+13];
                    float z = matrices[m+2] * p.x + matrices[m+6] * p.y + matrices[m+10] * p.z + matrices[m+14];
                    distances[i] = x*x + y*y + z*z;
                    order[i] = i;
                }
                IntArrays.mergeSort(order, (a, b) -> Float.compare(distances[b], distances[a]));
                return order;
            });
            buffer.upload(baker().end());
        }
    }

    /** # children are fixed cube rotations, folded into the nearest animated bone. */
    public static void register(ModelPart part, List<ModelPart.Cube> cubes, Map<String, ModelPart> children) {
        Node node = new Node(part, cubes);
        children.forEach((name, child) -> {
            Node n = NODES.get(child);
            if (n == null) throw new IllegalStateException("Unregistered geometry child " + name);
            (name.contains("#") ? node.fixed : node.children).add(n);
        });
        NODES.put(part, node);
    }
    public static ModelPart[] animatedParts(ModelPart root) {
        Node node = NODES.get(root);
        if (node == null) return root.getAllParts().toArray(ModelPart[]::new);
        List<ModelPart> out = new ArrayList<>();
        node.animatedParts(out);
        return out.toArray(ModelPart[]::new);
    }
    public static RenderType pass(RenderType type, Pass pass) { PASSES.put(type, pass); return type; }

    /** Oculus replaces ModelPart.render and loses FaceCube's custom UV/geometry. Traverse our own tree. */
    public static void renderVertices(ModelPart root, PoseStack pose, VertexConsumer out, int light, int overlay) {
        Node node = NODES.get(root);
        if (node == null) {
            root.render(pose, out, light, overlay);
            return;
        }
        renderVertices(node, pose, out, light, overlay);
    }

    private static void renderVertices(Node node, PoseStack pose, VertexConsumer out, int light, int overlay) {
        if (!node.part.visible) return;
        pose.pushPose();
        node.part.translateAndRotate(pose);
        if (!node.part.skipDraw) {
            for (var cube : node.cubes) cube.compile(pose.last(), out, light, overlay, 1, 1, 1, 1);
        }
        for (Node child : node.fixed) renderVertices(child, pose, out, light, overlay);
        for (Node child : node.children) renderVertices(child, pose, out, light, overlay);
        pose.popPose();
    }

    /**
     * Draws the model right away. Draws are not queued for BufferSource.endBatch: ImmediatelyFast replaces the entity
     * BufferSource with a subclass whose endBatch never calls super, so a queue flushed from a mixin there never ran
     * and the models stayed invisible (only the shadow was drawn).
     */
    public static boolean render(ModelPart root, PoseStack pose, MultiBufferSource buffers, RenderType type, int light, int overlay) {
        Node node = NODES.get(root);
        Pass pass = PASSES.get(type);
        if (SHADER_PACK_MOD || broken || !com.harddayz.ClientConfig.gpuModels() || node == null || pass == null || !SHADERS.containsKey(pass)
                || !(buffers instanceof MultiBufferSource.BufferSource)) return false;
        Mesh mesh = MESHES.computeIfAbsent(root, k -> new Mesh(node));
        if (mesh.bones.size() > BONES) return false; // Future oversized models retain the vanilla path.
        float[] matrices = MATRIX_POOL.pollFirst();
        if (matrices == null) matrices = new float[MATRIX_FLOATS * 2];
        else Arrays.fill(matrices, 0);
        mesh.snapshot(node, pose, matrices, true, new int[1]);
        try {
            draw(mesh, matrices, type, pass, light, overlay);
        } finally {
            if (MATRIX_POOL.size() < 256) MATRIX_POOL.addFirst(matrices);
        }
        return !broken;
    }

    private static void draw(Mesh mesh, float[] matrices, RenderType type, Pass pass, int light, int overlay) {
        ShaderInstance shader = SHADERS.get(pass);
        int previous = GL30.glGetIntegeri(GL31.GL_UNIFORM_BUFFER_BINDING, BINDING);
        int previousBuffer = GL11.glGetInteger(GL31.GL_UNIFORM_BUFFER_BINDING);
        if (boneBuffer == 0) boneBuffer = GL15.glGenBuffers();
        type.setupRenderState();
        while (GL11.glGetError() != GL11.GL_NO_ERROR) { /* drop errors left by other code */ }
        try {
            mesh.bake();
            if (mesh.buffer == null) return;
            mesh.buffer.bind();
            if (pass == Pass.TRANSLUCENT) mesh.sort(matrices);
            MATRICES.clear(); MATRICES.put(matrices).flip();
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, boneBuffer);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, MATRICES, GL15.GL_STREAM_DRAW);
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, boneBuffer);
            shader.safeGetUniform("PackedLight").set(light & 65535, light >>> 16);
            shader.safeGetUniform("OverlayUv").set(overlay & 65535, overlay >>> 16);
            mesh.buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            draws++;
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) fail("GL error 0x" + Integer.toHexString(error), null);
        } catch (RuntimeException e) {
            fail(e.toString(), e);
        } finally {
            VertexBuffer.unbind();
            GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, BINDING, previous);
            GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, previousBuffer);
            type.clearRenderState();
        }
    }

    private static void fail(String reason, Throwable cause) {
        broken = true;
        com.mojang.logging.LogUtils.getLogger().error("HardDayz: VBO model rendering failed ({}), falling back to the plain renderer", reason, cause);
    }
    public static void invalidate() {
        RenderSystem.assertOnRenderThread();
        MATRIX_POOL.clear();
        for (Mesh mesh : MESHES.values()) if (mesh.buffer != null) mesh.buffer.close();
        MESHES.clear();
        if (boneBuffer != 0) GL15.glDeleteBuffers(boneBuffer);
        boneBuffer = 0;
    }
    public static void registerShaders(RegisterShadersEvent event) {
        invalidate(); SHADERS.clear();
        try {
            for (Pass pass : Pass.values()) {
                String name = "geo_" + pass.name().toLowerCase(Locale.ROOT);
                event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(HardDayz.MODID, name),
                        DefaultVertexFormat.NEW_ENTITY), shader -> {
                    int block = GL31.glGetUniformBlockIndex(shader.getId(), "BoneTransforms");
                    if (block == GL31.GL_INVALID_INDEX) throw new IllegalStateException("Missing model bone block");
                    GL31.glUniformBlockBinding(shader.getId(), block, BINDING);
                    SHADERS.put(pass, shader);
                });
            }
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load HardDayz model shaders", e); }
    }
    public static boolean shaderPackMod() { return SHADER_PACK_MOD; }
    public static boolean ready() { return SHADERS.size() == Pass.values().length; }
    public static long uploads() { return uploads; }
    public static long draws() { return draws; }
}
