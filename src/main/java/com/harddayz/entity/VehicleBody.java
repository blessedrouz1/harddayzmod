package com.harddayz.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Повёрнутый «кузов» машины в плане: длина и ширина по модели, поворачивается вместе с машиной.
 * Ванильная коробка сущности всегда квадратная и не крутится — длинная фура с ней проезжает мордой сквозь стены.
 */
public record VehicleBody(double front, double back, double half) {
    public static final VehicleBody DEFAULT = new VehicleBody(1.5, 1.5, 1.5);

    public double length() {
        return front + back;
    }

    /** Масса условная: 1 — средний грузовик, длинные тяжелее. */
    public double mass() {
        return Math.max(0.5, Math.min(2.2, length() / 6.5));
    }

    private static Vec3 fwd(float yaw) {
        double y = Math.toRadians(yaw);
        return new Vec3(-Math.sin(y), 0, Math.cos(y));
    }

    private static Vec3 right(float yaw) {
        double y = Math.toRadians(yaw);
        return new Vec3(-Math.cos(y), 0, -Math.sin(y));
    }

    /** Точки по периметру кузова (шаг ~0.7 блока). */
    public List<Vec3> perimeter(Vec3 pos, float yaw) {
        Vec3 f = fwd(yaw), r = right(yaw);
        List<Vec3> out = new ArrayList<>();
        int nl = Math.max(2, (int) Math.ceil(length() / 0.7));
        int nw = Math.max(2, (int) Math.ceil(half * 2 / 0.7));
        for (int i = 0; i <= nl; i++) {
            double a = -back + length() * i / nl;
            out.add(pos.add(f.scale(a)).add(r.scale(half)));
            out.add(pos.add(f.scale(a)).add(r.scale(-half)));
        }
        for (int i = 1; i < nw; i++) {
            double b = -half + half * 2 * i / nw;
            out.add(pos.add(f.scale(front)).add(r.scale(b)));
            out.add(pos.add(f.scale(-back)).add(r.scale(b)));
        }
        return out;
    }

    /** Мягкое препятствие: техника его сносит, а не упирается. */
    public static boolean soft(BlockState s) {
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FENCES) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.SAPLINGS)
                || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS) || s.is(net.minecraftforge.common.Tags.Blocks.GLASS)
                || s.is(net.minecraftforge.common.Tags.Blocks.GLASS_PANES) || s.is(net.minecraft.world.level.block.Blocks.BAMBOO)
                || s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) || s.is(net.minecraft.world.level.block.Blocks.CACTUS);
    }

    /**
     * Сколько точек кузова упирается в твёрдое выше ступеньки. Мягкие блоки складываются в {@code soft} и не считаются.
     */
    public int blocked(Level level, Vec3 pos, float yaw, double step, List<BlockPos> soft) {
        int n = 0;
        for (Vec3 p : perimeter(pos, yaw)) {
            for (double h : new double[] {step + 0.15, 1.7}) {
                BlockPos bp = BlockPos.containing(p.x, pos.y + h, p.z);
                BlockState s = level.getBlockState(bp);
                if (s.isAir()) {
                    continue;
                }
                var shape = s.getCollisionShape(level, bp);
                if (shape.isEmpty() || bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) <= pos.y + step) {
                    continue;
                }
                if (soft(s)) {
                    if (soft != null && !soft.contains(bp)) {
                        soft.add(bp);
                    }
                    continue;
                }
                n++;
            }
        }
        return n;
    }

    /** Пересекаются ли два повёрнутых кузова (теорема о разделяющей оси, в плане). */
    public static boolean overlap(Vec3 pa, float ya, VehicleBody a, Vec3 pb, float yb, VehicleBody b) {
        Vec3[] axes = {fwd(ya), right(ya), fwd(yb), right(yb)};
        for (Vec3 ax : axes) {
            double[] ra = project(pa, ya, a, ax), rb = project(pb, yb, b, ax);
            if (ra[1] < rb[0] || rb[1] < ra[0]) {
                return false;
            }
        }
        return true;
    }

    private static double[] project(Vec3 p, float yaw, VehicleBody body, Vec3 ax) {
        Vec3 f = fwd(yaw), r = right(yaw);
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double a : new double[] {body.front, -body.back}) {
            for (double b : new double[] {body.half, -body.half}) {
                double d = p.add(f.scale(a)).add(r.scale(b)).dot(ax);
                lo = Math.min(lo, d);
                hi = Math.max(hi, d);
            }
        }
        return new double[] {lo, hi};
    }

    /** Точка внутри кузова (с запасом)? */
    public boolean contains(Vec3 pos, float yaw, Vec3 point, double margin) {
        Vec3 d = point.subtract(pos);
        double a = d.dot(fwd(yaw)), b = d.dot(right(yaw));
        return a <= front + margin && a >= -back - margin && Math.abs(b) <= half + margin;
    }
}
