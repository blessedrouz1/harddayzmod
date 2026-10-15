package com.harddayz.client;

import java.util.OptionalDouble;

import com.harddayz.item.DesignatorItem;
import com.harddayz.registry.ModItems;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Подсветка блока, отмеченного лазерным целеуказателем: пока он в руке, засечённый блок обведён пульсирующей рамкой
 * и залит полупрозрачным красным, видно сквозь стены. Издали рамка раздувается, чтобы не пропасть.
 */
public final class TargetHighlight {
    private TargetHighlight() {
    }

    /** Типы рендера без проверки глубины — рамку видно через землю и стены. */
    private static final class Types extends RenderType {
        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling, boolean sort, Runnable setup,
                Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }

        static final RenderType LINES = create("harddayz_target_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256,
                false, false, CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(3.0)))
                        .setLayeringState(NO_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setOutputState(ITEM_ENTITY_TARGET)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).createCompositeState(false));

        static final RenderType FILL = create("harddayz_target_fill", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).createCompositeState(false));
    }

    private static BlockPos target(ItemStack s) {
        return s.is(ModItems.DESIGNATOR.get()) ? DesignatorItem.target(s) : null;
    }

    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.level == null) {
            return;
        }
        BlockPos t = target(p.getMainHandItem());
        if (t == null) {
            t = target(p.getOffhandItem());
        }
        if (t == null) {
            return;
        }
        Vec3 cam = e.getCamera().getPosition();
        double dist = cam.distanceTo(Vec3.atCenterOf(t));
        double grow = 0.01 + dist * 0.004;
        AABB box = new AABB(t).inflate(grow).move(-cam.x, -cam.y, -cam.z);
        float pulse = 0.65f + 0.35f * (float) Math.sin((mc.level.getGameTime() + e.getPartialTick()) * 0.25);
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        VertexConsumer fill = buf.getBuffer(Types.FILL);
        fillBox(ps.last().pose(), fill, box, 1f, 0.1f, 0.05f, 0.22f * pulse);
        buf.endBatch(Types.FILL);

        VertexConsumer lines = buf.getBuffer(Types.LINES);
        LevelRenderer.renderLineBox(ps, lines, box, 1f, 0.25f, 0.15f, pulse);
        buf.endBatch(Types.LINES);
    }

    private static void fillBox(Matrix4f m, VertexConsumer vc, AABB b, float r, float g, float bl, float a) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(m, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, a); // низ
        quad(m, vc, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a); // верх
        quad(m, vc, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, bl, a); // север
        quad(m, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, a); // юг
        quad(m, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, a); // запад
        quad(m, vc, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, bl, a); // восток
    }

    private static void quad(Matrix4f m, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz, float r, float g, float b, float a) {
        vc.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
    }
}
