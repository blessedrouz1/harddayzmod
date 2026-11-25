package com.harddayz.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Ударная волна и свечение взрыва. Волна — тонкая прозрачная оболочка, растёт со скоростью звука (её край доходит
 * до игрока вместе со звуком), по земле бежит белёсая стенка. Свечение складывается с картинкой и видно издалека.
 */
final class BlastRender {
    private static final int SEG = 40;
    private static final int RINGS = 18;

    private BlastRender() {
    }

    private static final class Types extends RenderType {
        private Types(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean c, boolean t, Runnable a, Runnable b) {
            super(n, f, m, s, c, t, a, b);
        }

        static final RenderType SHELL = create("harddayz_shockwave", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, false,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));
        static final RenderType GLOW = create("harddayz_blast_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 16384, false, false,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).createCompositeState(false));
    }

    static void onRender(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || ClientBlasts.ACTIVE.isEmpty()) {
            return;
        }
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        double far = mc.options.getEffectiveRenderDistance() * 16;

        VertexConsumer shell = buf.getBuffer(Types.SHELL);
        for (ClientBlasts.Active a : ClientBlasts.ACTIVE) {
            if (a.fx && !a.minor && a.power >= 1) {
                shell(mc.level, m, shell, a, a.age + e.getPartialTick(), cam);
            }
        }
        buf.endBatch(Types.SHELL);

        if (com.harddayz.ClientConfig.flash()) {
            VertexConsumer glow = buf.getBuffer(Types.GLOW);
            for (ClientBlasts.Active a : ClientBlasts.ACTIVE) {
                glow(m, glow, a, a.age + e.getPartialTick(), cam, far);
            }
            buf.endBatch(Types.GLOW);
        }
    }

    /** Докуда волна видна глазом. */
    static double reach(float p) {
        return 16 + p * 7;
    }

    private static void shell(ClientLevel level, Matrix4f m, VertexConsumer vc, ClientBlasts.Active a, float t, Vec3 cam) {
        double r = t * ClientBlasts.SOUND_SPEED;
        double max = reach(a.power);
        if (r > max || r < 0.5) {
            return;
        }
        float k = (float) (1 - r / max);
        float strength = Mth.clamp(0.35f + a.power * 0.05f, 0, 0.85f) * k * (0.4f + 0.6f * k);
        Vec3 c = a.pos.subtract(cam);
        sphere(m, vc, c, r, strength, 0.94f, 0.97f, 1f);
        sphere(m, vc, c, r * 0.965, strength * 0.35f, 0.1f, 0.12f, 0.15f); // за фронтом воздух разрежён — тёмная кромка

        // стенка пыли и пара по земле
        double hgt = a.pos.y - a.ground;
        if (a.air && hgt > r) {
            return;
        }
        double gr = Math.sqrt(Math.max(0, r * r - hgt * hgt));
        float top = 0.8f + a.power * 0.16f;
        double in = Math.max(0, gr - 2 - a.power * 0.2);
        double[] h = new double[SEG + 1];
        for (int i = 0; i <= SEG; i++) {
            double ang = (i % SEG) * Math.PI * 2 / SEG;
            int x = Mth.floor(a.pos.x + Math.cos(ang) * gr), z = Mth.floor(a.pos.z + Math.sin(ang) * gr);
            double y = ClientBlasts.loaded(level, x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : a.ground;
            h[i] = Math.abs(y - a.ground) > 24 ? a.ground : y;
        }
        for (int i = 0; i < SEG; i++) {
            double a0 = i * Math.PI * 2 / SEG, a1 = (i + 1) * Math.PI * 2 / SEG;
            float x0 = (float) (c.x + Math.cos(a0) * gr), z0 = (float) (c.z + Math.sin(a0) * gr);
            float x1 = (float) (c.x + Math.cos(a1) * gr), z1 = (float) (c.z + Math.sin(a1) * gr);
            float y0 = (float) (h[i] - cam.y), y1 = (float) (h[i + 1] - cam.y);
            float al = strength * 0.6f;
            v(m, vc, x0, y0, z0, 0.9f, 0.88f, 0.84f, al);
            v(m, vc, x1, y1, z1, 0.9f, 0.88f, 0.84f, al);
            v(m, vc, x1, y1 + top, z1, 0.95f, 0.95f, 0.95f, 0);
            v(m, vc, x0, y0 + top, z0, 0.95f, 0.95f, 0.95f, 0);
            // шлейф за фронтом по земле
            float ix0 = (float) (c.x + Math.cos(a0) * in), iz0 = (float) (c.z + Math.sin(a0) * in);
            float ix1 = (float) (c.x + Math.cos(a1) * in), iz1 = (float) (c.z + Math.sin(a1) * in);
            v(m, vc, x0, y0 + 0.15f, z0, 0.9f, 0.88f, 0.84f, al * 0.8f);
            v(m, vc, x1, y1 + 0.15f, z1, 0.9f, 0.88f, 0.84f, al * 0.8f);
            v(m, vc, ix1, y1 + 0.15f, iz1, 0.9f, 0.88f, 0.84f, 0);
            v(m, vc, ix0, y0 + 0.15f, iz0, 0.9f, 0.88f, 0.84f, 0);
        }
    }

    /** Оболочка видна только у края, где взгляд идёт по касательной, как у настоящей волны. */
    private static void sphere(Matrix4f m, VertexConsumer vc, Vec3 c, double r, float alpha, float cr, float cg, float cb) {
        float[][] px = new float[RINGS + 1][SEG + 1];
        float[][] py = new float[RINGS + 1][SEG + 1];
        float[][] pz = new float[RINGS + 1][SEG + 1];
        float[][] pa = new float[RINGS + 1][SEG + 1];
        for (int i = 0; i <= RINGS; i++) {
            double lat = -Math.PI / 2 + Math.PI * i / RINGS;
            double cl = Math.cos(lat), sl = Math.sin(lat);
            for (int j = 0; j <= SEG; j++) {
                double lon = j * Math.PI * 2 / SEG;
                double nx = cl * Math.cos(lon), ny = sl, nz = cl * Math.sin(lon);
                double x = c.x + nx * r, y = c.y + ny * r, z = c.z + nz * r;
                double len = Math.sqrt(x * x + y * y + z * z);
                double dot = len < 1.0E-4 ? 1 : Math.abs((x * nx + y * ny + z * nz) / len);
                double f = 1 - dot;
                px[i][j] = (float) x;
                py[i][j] = (float) y;
                pz[i][j] = (float) z;
                pa[i][j] = alpha * (float) (0.03 + 0.97 * f * f * f);
            }
        }
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEG; j++) {
                v(m, vc, px[i][j], py[i][j], pz[i][j], cr, cg, cb, pa[i][j]);
                v(m, vc, px[i][j + 1], py[i][j + 1], pz[i][j + 1], cr, cg, cb, pa[i][j + 1]);
                v(m, vc, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], cr, cg, cb, pa[i + 1][j + 1]);
                v(m, vc, px[i + 1][j], py[i + 1][j], pz[i + 1][j], cr, cg, cb, pa[i + 1][j]);
            }
        }
    }

    private static void glow(Matrix4f m, VertexConsumer vc, ClientBlasts.Active a, float t, Vec3 cam, double far) {
        float life = 7 + a.power * 1.2f;
        if (t > life) {
            return;
        }
        float k = 1 - t / life;
        float i = k * k * (a.minor ? 0.5f : 1f);
        Vec3 c = new Vec3(a.pos.x, Math.max(a.pos.y, a.ground) + a.power * 0.25 + t * 0.06 * a.power, a.pos.z).subtract(cam);
        double d = c.length();
        if (d < 0.5) {
            return;
        }
        double size = 3 + a.power * 1.7;
        // за дальностью прорисовки рисуем ту же вспышку ближе и мельче — на горизонте её видно
        double lim = far * 0.9;
        double scale = d > lim ? lim / d : 1;
        // чуть к камере, чтобы земля не срезала половину ореола
        double near = Math.max(0.3, d * scale - Math.min(size * 0.5, d * 0.5) * scale);
        Vec3 dir = c.scale(1 / d);
        Vec3 p = dir.scale(near);
        size *= near / d;
        Vec3 right = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : dir.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = right.cross(dir);
        disc(m, vc, p, right, up, size, 1f, 0.55f, 0.22f, 0.55f * i);
        disc(m, vc, p, right, up, size * 0.4, 1f, 0.9f, 0.7f, 0.9f * i * k);
    }

    private static void disc(Matrix4f m, VertexConsumer vc, Vec3 p, Vec3 right, Vec3 up, double size, float r, float g, float b, float alpha) {
        int n = 24;
        for (int ring = 0; ring < 4; ring++) {
            double f0 = ring / 4.0, f1 = (ring + 1) / 4.0;
            float al0 = alpha * (float) ((1 - f0) * (1 - f0)), al1 = alpha * (float) ((1 - f1) * (1 - f1));
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                point(m, vc, p, right, up, size * f0, a0, r, g, b, al0);
                point(m, vc, p, right, up, size * f1, a0, r, g, b, al1);
                point(m, vc, p, right, up, size * f1, a1, r, g, b, al1);
                point(m, vc, p, right, up, size * f0, a1, r, g, b, al0);
            }
        }
    }

    private static void point(Matrix4f m, VertexConsumer vc, Vec3 p, Vec3 right, Vec3 up, double rad, double ang, float r, float g, float b, float a) {
        double cx = Math.cos(ang) * rad, sy = Math.sin(ang) * rad;
        v(m, vc, (float) (p.x + right.x * cx + up.x * sy), (float) (p.y + right.y * cx + up.y * sy), (float) (p.z + right.z * cx + up.z * sy), r, g, b, a);
    }

    private static void v(Matrix4f m, VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
        vc.vertex(m, x, y, z).color(r, g, b, Mth.clamp(a, 0, 1)).endVertex();
    }
}
