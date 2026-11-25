package com.harddayz.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.harddayz.registry.ModBlocks;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Постройки фронтовой карты. Каждая рисуется целиком, холст сам отсекает чужие чанки. */
public final class FrontBuildings {
    /** Куда рисуем. */
    public interface Canvas {
        void set(int x, int y, int z, BlockState s);
    }

    private interface Painter {
        void paint(Draw d);
    }

    private record Item(int x0, int z0, int x1, int z1, Painter p, long seed, boolean solid) {
        boolean hits(int ax, int az, int bx, int bz) {
            return x0 <= bx && x1 >= ax && z0 <= bz && z1 >= az;
        }
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final FrontMap map;
    private final List<Item> items = new ArrayList<>();
    private final Map<Long, List<Item>> byChunk = new HashMap<>();
    /** Места под важные объекты города (режим «Штаб и город»). */
    public final List<int[]> objects = new ArrayList<>();

    public FrontBuildings(FrontMap map) {
        this.map = map;
        for (FrontMap.Site s : map.sites) {
            Random r = new Random(map.seed ^ s.x() * 73856093L ^ s.z() * 19349663L);
            switch (s.type()) {
                case BASE -> base(s);
                case CITY -> city(s, r);
                case VILLAGE -> village(s, r);
                case POWER -> power(s);
                case OIL -> oil(s);
                case AIRFIELD -> airfield(s);
                case TOWER -> tower(s);
            }
        }
        details(new Random(map.seed * 17 + 3));
        for (Item it : items) {
            for (int x = it.x0 >> 4; x <= it.x1 >> 4; x++) {
                for (int z = it.z0 >> 4; z <= it.z1 >> 4; z++) {
                    byChunk.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> new ArrayList<>()).add(it);
                }
            }
        }
    }

    public void paint(Canvas c, int chunkX, int chunkZ) {
        List<Item> list = byChunk.get(((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL));
        if (list != null) {
            for (Item it : list) {
                it.p.paint(new Draw(c, it.seed));
            }
        }
    }

    /** Занято постройкой (деревья сюда не сажаем). */
    public boolean taken(int x, int z, int margin) {
        List<Item> list = byChunk.get(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL));
        if (list != null) {
            for (Item it : list) {
                if (it.hits(x - margin, z - margin, x + margin, z + margin)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Прямоугольники построек для карты игрока: x0, z0, x1, z1. */
    public List<int[]> footprints() {
        List<int[]> out = new ArrayList<>();
        for (Item it : items) {
            if (it.solid) {
                out.add(new int[] {it.x0, it.z0, it.x1, it.z1});
            }
        }
        return out;
    }

    private boolean solid = true;

    private void add(int x0, int z0, int x1, int z1, Painter p) {
        items.add(new Item(x0, z0, x1, z1, p, map.seed ^ x0 * 341873128712L ^ z0 * 132897987541L, solid));
    }

    // ------------------------------------------------------------------ кисть

    private static final class Draw {
        final Canvas c;
        final Random r;

        Draw(Canvas c, long seed) {
            this.c = c;
            this.r = new Random(seed);
        }

        void set(int x, int y, int z, BlockState s) {
            c.set(x, y, z, s);
        }

        void set(int x, int y, int z, Block b) {
            c.set(x, y, z, b.defaultBlockState());
        }

        void box(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
            BlockState s = b.defaultBlockState();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        c.set(x, y, z, s);
                    }
                }
            }
        }

        /** Только стены коробки. */
        void walls(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
            box(x0, y0, z0, x1, y1, z0, b);
            box(x0, y0, z1, x1, y1, z1, b);
            box(x0, y0, z0, x0, y1, z1, b);
            box(x1, y0, z0, x1, y1, z1, b);
        }

        void disc(int cx, int y, int cz, double rad, Block b) {
            BlockState s = b.defaultBlockState();
            int n = Mth.ceil(rad);
            for (int x = -n; x <= n; x++) {
                for (int z = -n; z <= n; z++) {
                    if (x * x + z * z <= rad * rad) {
                        c.set(cx + x, y, cz + z, s);
                    }
                }
            }
        }

        void ring(int cx, int y, int cz, double rad, Block b) {
            BlockState s = b.defaultBlockState();
            int n = Mth.ceil(rad) + 1;
            for (int x = -n; x <= n; x++) {
                for (int z = -n; z <= n; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= rad + 0.5 && d > rad - 0.7) {
                        c.set(cx + x, y, cz + z, s);
                    }
                }
            }
        }

        /** Пролом: выбитый шар и щебень под ним. */
        void hole(int cx, int cy, int cz, double rad, int floor) {
            int n = Mth.ceil(rad);
            for (int x = -n; x <= n; x++) {
                for (int y = -n; y <= n; y++) {
                    for (int z = -n; z <= n; z++) {
                        double d = Math.sqrt(x * x + y * y + z * z) + r.nextDouble() * 0.9;
                        if (d <= rad && cy + y > floor) {
                            c.set(cx + x, cy + y, cz + z, AIR);
                        }
                    }
                }
            }
        }

        void rubble(int cx, int y, int cz, int rad) {
            for (int i = 0; i < rad * 6; i++) {
                int x = cx + (int) (r.nextGaussian() * rad * 0.5), z = cz + (int) (r.nextGaussian() * rad * 0.5);
                set(x, y, z, r.nextInt(3) == 0 ? Blocks.COBBLESTONE : r.nextInt(2) == 0 ? Blocks.ANDESITE : Blocks.GRAVEL);
            }
        }
    }

    private static <T> T pick(Random r, T[] a) {
        return a[r.nextInt(a.length)];
    }

    // ------------------------------------------------------------------ город

    private record Lot(int x, int z, int w, int d, FrontMap.Zone zone, boolean whole) {
        int area() {
            return w * d;
        }
    }

    /** Главные места города: мэрия, площадь. */
    public int[] hall = {0, 0, 0};
    public int[] plaza = {0, 0, 0};

    /** Участки между изогнутыми улицами, внутри неровной границы. */
    private List<Lot> lots(FrontMap.Site s) {
        List<Lot> out = new ArrayList<>();
        for (int k = 0; k + 1 < map.streetsV(); k++) {
            for (int j = 0; j + 1 < map.streetsH(); j++) {
                double cx = (map.streetX(k, 0) + map.streetX(k + 1, 0)) / 2;
                double zA = map.streetZ(j, cx), zB = map.streetZ(j + 1, cx);
                double x0 = -1.0E9, x1 = 1.0E9, z0 = -1.0E9, z1 = 1.0E9;
                for (double z = zA; z <= zB; z += 3) {
                    x0 = Math.max(x0, map.streetX(k, z));
                    x1 = Math.min(x1, map.streetX(k + 1, z));
                }
                for (double x = x0; x <= x1; x += 3) {
                    z0 = Math.max(z0, map.streetZ(j, x));
                    z1 = Math.min(z1, map.streetZ(j + 1, x));
                }
                int lx = Mth.ceil(x0) + 7, lz = Mth.ceil(z0) + 7, w = Mth.floor(x1) - 7 - lx, d = Mth.floor(z1) - 7 - lz;
                if (w < 16 || d < 16 || map.river(lx + w / 2.0, lz + d / 2.0) < FrontMap.RIVER_HALF + 12) {
                    continue;
                }
                boolean all = true;
                for (int[] c : new int[][] {{0, 0}, {w, 0}, {0, d}, {w, d}}) {
                    all &= s.dist(lx + c[0], lz + c[1]) < s.radius() - 4;
                }
                if (!all && s.dist(lx + w / 2.0, lz + d / 2.0) > s.radius() - 6) {
                    continue;
                }
                out.add(new Lot(lx, lz, w, d, map.district(lx + w / 2.0, lz + d / 2.0).zone(), all));
            }
        }
        return out;
    }

    private static Lot take(List<Lot> lots, java.util.function.ToDoubleFunction<Lot> worth) {
        Lot best = null;
        for (Lot l : lots) {
            if (l.whole && l.w >= 28 && l.d >= 24 && (best == null || worth.applyAsDouble(l) > worth.applyAsDouble(best))) {
                best = l;
            }
        }
        lots.remove(best);
        return best;
    }

    private void landmark(Lot l, int y, int floors, Block wall, Random r) {
        if (l == null) {
            return;
        }
        int w = Math.min(38, l.w - 2), d = Math.min(26, l.d - 4);
        int x = l.x + (l.w - w) / 2, z = l.z + (l.d - d) / 2;
        office(x, z, w, d, y, floors, wall, r);
        objects.add(new int[] {x + w / 4, y + 1, z + d / 4});
    }

    private void city(FrontMap.Site s, Random r) {
        int y = s.y();
        List<Lot> lots = lots(s);
        Lot h = take(lots, l -> (l.zone == FrontMap.Zone.CENTER ? 3000 : 0) + Math.min(l.area(), 1600));
        landmark(h, y, 4, Blocks.QUARTZ_BRICKS, r); // мэрия
        if (h != null) {
            int w = Math.min(38, h.w - 2), d = Math.min(26, h.d - 4);
            hall = new int[] {h.x + (h.w - w) / 2 + w * 3 / 4, y + 1, h.z + (h.d - d) / 2 + d / 4};
            Lot sq = take(lots, l -> -Math.hypot(l.x + l.w / 2.0 - h.x - h.w / 2.0, l.z + l.d / 2.0 - h.z - h.d / 2.0));
            if (sq != null) {
                square(sq.x, sq.z, sq.w, sq.d, y);
                plaza = new int[] {sq.x + sq.w / 2, y + 1, sq.z + sq.d / 2};
            }
        }
        landmark(take(lots, l -> (l.zone == FrontMap.Zone.STATION ? 3000 : 0) - Math.hypot(l.x - 80, l.z + 290)), y, 2, Blocks.CUT_SANDSTONE, r); // вокзал
        landmark(take(lots, l -> (l.zone == FrontMap.Zone.SLEEP ? 3000 : 0) + l.area()), y, 5, Blocks.WHITE_CONCRETE, r); // больница
        landmark(take(lots, l -> (l.zone == FrontMap.Zone.OLD || l.zone == FrontMap.Zone.CENTER ? 3000 : 0) + l.area()), y, 3, Blocks.LIGHT_GRAY_CONCRETE, r); // узел связи
        Lot f = take(lots, l -> (l.zone == FrontMap.Zone.INDUSTRY ? 6000 : 0) + l.area());
        if (f != null) {
            works(f, y, r);
            objects.add(new int[] {f.x + f.w / 2, y + 1, f.z + f.d / 2 - 1});
        }
        for (Lot l : lots) {
            FrontMap.Zone zone = l.whole ? l.zone : FrontMap.Zone.PRIVATE; // окраина — частные дома до самой границы
            int t = r.nextInt(100);
            switch (zone) {
                case CENTER -> {
                    if (t < 15) {
                        park(l.x, l.z, l.w, l.d, y);
                    } else {
                        blockOf(l, y, 3, 6, new Block[] {Blocks.SMOOTH_SANDSTONE, Blocks.QUARTZ_BRICKS, Blocks.BRICKS, Blocks.WHITE_TERRACOTTA}, r);
                    }
                }
                case OLD -> blockOf(l, y, 2, 3, new Block[] {Blocks.BRICKS, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.TERRACOTTA, Blocks.PINK_TERRACOTTA}, r);
                case SLEEP, RIVERSIDE -> {
                    if (t < 12) {
                        park(l.x, l.z, l.w, l.d, y);
                    } else if (t < 22) {
                        shops(l, y, r);
                    } else {
                        panels(l, y, r);
                    }
                }
                case INDUSTRY -> works(l, y, r);
                case STATION -> {
                    if (t < 35) {
                        works(l, y, r);
                    } else if (t < 50) {
                        shops(l, y, r);
                    } else {
                        blockOf(l, y, 2, 4, new Block[] {Blocks.BRICKS, Blocks.LIGHT_GRAY_CONCRETE}, r);
                    }
                }
                default -> homes(l, s, y, r);
            }
        }
    }

    /** Квартал домов по периметру с двором внутри. */
    private void blockOf(Lot l, int y, int f0, int f1, Block[] walls, Random r) {
        for (int side = 0; side < (l.d >= 30 ? 2 : 1); side++) {
            int x = l.x;
            while (x + 12 <= l.x + l.w) {
                int w = Math.min(12 + r.nextInt(9), l.x + l.w - x);
                if (l.x + l.w - x - w < 12) {
                    w = l.x + l.w - x;
                }
                w = Math.min(w, 30);
                if (r.nextInt(8) > 0) {
                    int d = Math.min(12, l.d - 2);
                    office(x, side == 0 ? l.z : l.z + l.d - d, w, d, y, f0 + r.nextInt(f1 - f0 + 1), pick(r, walls), r);
                }
                x += w + 1 + (r.nextInt(5) == 0 ? 4 : 0);
            }
        }
        if (l.d >= 34) { // деревья во дворе
            solid = false;
            for (int i = 0; i < l.w / 9; i++) {
                int tx = l.x + 4 + r.nextInt(Math.max(1, l.w - 8)), tz = l.z + 15 + r.nextInt(Math.max(1, l.d - 30));
                add(tx - 4, tz - 4, tx + 4, tz + 4, d -> tree(d.c, tx, y + 1, tz, d.r.nextInt(2), d.r));
            }
            solid = true;
        }
    }

    private void homes(Lot l, FrontMap.Site s, int y, Random r) {
        for (int x = l.x; x + 15 <= l.x + l.w; x += 18) {
            for (int z = l.z; z + 16 <= l.z + l.d; z += 19) {
                int cx = x + 8, cz = z + 8;
                if (s.dist(cx, cz) > s.radius() - 10 || r.nextInt(9) == 0) {
                    continue;
                }
                boolean swap = r.nextBoolean();
                int len = 10 + r.nextInt(2);
                house(cx - (swap ? 4 : len / 2), cz - (swap ? len / 2 : 4), swap, 8, len, y, r, r.nextBoolean());
                if (r.nextInt(4) > 0) {
                    yard(cx, cz, 8, 8, y, r);
                }
            }
        }
    }

    private void works(Lot l, int y, Random r) {
        if (l.w >= 36 && l.d >= 34) {
            factory(l.x, l.z, Math.min(42, l.w), Math.min(30, l.d - 8), y, r);
        } else if (l.w >= 28 && l.d >= 14) {
            barn(l.x + 1, l.z + 2, y, r);
            if (l.d >= 28) {
                barn(l.x + 1, l.z + 16, y, r);
            }
        } else {
            hangar(l.x, l.z, Math.min(16, l.d), Math.min(20, l.w), y, r.nextBoolean());
        }
    }

    private void panels(Lot l, int y, Random r) {
        Block wall = pick(r, new Block[] {Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.SMOOTH_STONE, Blocks.WHITE_TERRACOTTA});
        int floors = 4 + r.nextInt(6);
        boolean swap = l.d > l.w;
        int along = swap ? l.d : l.w, across = swap ? l.w : l.d;
        int len = along >= 37 ? 36 : 18;
        int off = (along - len) / 2;
        panel(swap ? l.x : l.x + off, swap ? l.z + off : l.z, swap, len, y, floors, wall, r);
        if (across >= 34) {
            int v = across - 15;
            panel(swap ? l.x + v : l.x + off, swap ? l.z + off : l.z + v, swap, r.nextBoolean() ? len : 18, y, Math.max(3, floors - r.nextInt(3)), wall, r);
        }
    }

    /** Панельный дом: коридор, квартиры по сторонам, лестницы с подвала до крыши. Короткая сторона 14. */
    private void panel(int x0, int z0, boolean swap, int len, int y, int floors, Block wall, Random pr) {
        int w = 14;
        boolean basement = pr.nextInt(2) == 0;
        boolean hit = pr.nextInt(100) < 45;
        int holes = hit ? 1 + pr.nextInt(4) : 0;
        add(x0 - 1, z0 - 1, x0 + (swap ? w : len), z0 + (swap ? len : w), d -> {
            // u вдоль дома, v поперёк
            Block floorB = Blocks.SMOOTH_STONE;
            int top = y + floors * 4;
            int low = basement ? -1 : 0;
            for (int f = low; f < floors; f++) {
                int y0 = y + f * 4;
                for (int u = 0; u < len; u++) {
                    for (int v = 0; v < w; v++) {
                        boolean ext = u == 0 || v == 0 || u == len - 1 || v == w - 1;
                        uv(d, x0, z0, swap, u, y0, v, f == low ? Blocks.STONE_BRICKS.defaultBlockState() : floorB.defaultBlockState());
                        boolean corridor = v == 6 || v == 7;
                        boolean part = !ext && (v == 5 || v == 8 || u % 6 == 0 && !corridor);
                        boolean door = part && (v == 5 || v == 8) && u % 6 == 3;
                        for (int h = 1; h <= 3; h++) {
                            BlockState b = AIR;
                            if (ext) {
                                boolean win = f >= 0 && h >= 2 && (v == 0 || v == w - 1 ? u % 3 == 1 : v == 3 || v == 10 || v == 6 || v == 7);
                                b = f < 0 ? Blocks.STONE_BRICKS.defaultBlockState() : win ? (d.r.nextInt(3) == 0 ? AIR : Blocks.GLASS.defaultBlockState()) : wall.defaultBlockState();
                            } else if (part && !(door && h < 3)) {
                                b = (f < 0 ? Blocks.STONE_BRICKS : Blocks.WHITE_TERRACOTTA).defaultBlockState();
                            }
                            uv(d, x0, z0, swap, u, y0 + h, v, b);
                        }
                    }
                }
            }
            for (int u = 0; u < len; u++) {
                for (int v = 0; v < w; v++) {
                    boolean ext = u == 0 || v == 0 || u == len - 1 || v == w - 1;
                    uv(d, x0, z0, swap, u, top, v, Blocks.SMOOTH_STONE.defaultBlockState());
                    uv(d, x0, z0, swap, u, top + 1, v, ext ? wall.defaultBlockState() : AIR);
                }
            }
            // подъезды: лестница вдоль фасада, дверь на улицу
            for (int c = 6; c + 6 <= len; c += 18) {
                for (int f = low; f < floors; f++) {
                    int y0 = y + f * 4;
                    for (int u = c + 1; u <= c + 6; u++) { // шахта без перегородок
                        for (int v = 8; v <= w - 2; v++) {
                            for (int h = 1; h <= 3; h++) {
                                uv(d, x0, z0, swap, u, y0 + h, v, AIR);
                            }
                        }
                    }
                    for (int v = 8; v <= w - 2; v++) {
                        for (int h = 1; h <= 3; h++) {
                            uv(d, x0, z0, swap, c, y0 + h, v, Blocks.WHITE_TERRACOTTA.defaultBlockState());
                            uv(d, x0, z0, swap, c + 7, y0 + h, v, Blocks.WHITE_TERRACOTTA.defaultBlockState());
                        }
                    }
                }
                for (int f = low; f < floors; f++) {
                    int y0 = y + f * 4;
                    boolean even = ((f - low) & 1) == 0;
                    int v = even ? 10 : 11;
                    for (int k = 0; k < 4; k++) {
                        int u = even ? c + 2 + k : c + 5 - k;
                        Direction up = swap ? (even ? Direction.SOUTH : Direction.NORTH) : (even ? Direction.EAST : Direction.WEST);
                        uv(d, x0, z0, swap, u, y0 + 1 + k, v, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up));
                        if (k == 1 || k == 2) {
                            uv(d, x0, z0, swap, u, y0 + 4, v, AIR);
                        }
                        uv(d, x0, z0, swap, u, y0 + 2 + k, v, AIR);
                        uv(d, x0, z0, swap, u, y0 + 3 + k, v, AIR);
                    }
                }
                // дверь подъезда и выход на крышу
                for (int h = 1; h <= 2; h++) {
                    uv(d, x0, z0, swap, c + 4, y + h, w - 1, AIR);
                    uv(d, x0, z0, swap, c + 3, y + h, w - 1, AIR);
                }
            }
            for (int i = 0; i < holes; i++) {
                int u = d.r.nextInt(len), v = d.r.nextBoolean() ? 0 : w - 1;
                int hx = x0 + (swap ? v : u), hz = z0 + (swap ? u : v);
                double rad = 2.5 + d.r.nextDouble() * 3.5;
                d.hole(hx, y + 3 + d.r.nextInt(Math.max(1, floors * 4 - 2)), hz, rad, y);
                d.rubble(hx, y + 1, hz, 5);
            }
        });
    }

    private static void uv(Draw d, int x0, int z0, boolean swap, int u, int y, int v, BlockState s) {
        d.set(x0 + (swap ? v : u), y, z0 + (swap ? u : v), s);
    }

    /** Общественное здание: большие залы, окна лентой, лестница у стены. */
    private void office(int x0, int z0, int w, int dp, int y, int floors, Block wall, Random pr) {
        int holes = pr.nextInt(100) < 35 ? 1 + pr.nextInt(2) : 0;
        add(x0 - 2, z0 - 2, x0 + w + 1, z0 + dp + 1, d -> {
            int x1 = x0 + w - 1, z1 = z0 + dp - 1;
            for (int f = 0; f < floors; f++) {
                int y0 = y + f * 4;
                d.box(x0, y0, z0, x1, y0, z1, Blocks.POLISHED_ANDESITE);
                d.box(x0, y0 + 1, z0, x1, y0 + 3, z1, Blocks.AIR);
                d.walls(x0, y0 + 1, z0, x1, y0 + 3, z1, wall);
                for (int x = x0 + 2; x < x1 - 1; x++) {
                    if ((x - x0) % 4 != 0) {
                        for (int z : new int[] {z0, z1}) {
                            d.set(x, y0 + 2, z, d.r.nextInt(4) == 0 ? Blocks.AIR : Blocks.LIGHT_GRAY_STAINED_GLASS);
                            d.set(x, y0 + 3, z, Blocks.LIGHT_GRAY_STAINED_GLASS);
                        }
                    }
                }
                for (int z = z0 + 2; z < z1 - 1; z++) {
                    if ((z - z0) % 4 != 0) {
                        d.set(x0, y0 + 2, z, Blocks.LIGHT_GRAY_STAINED_GLASS);
                        d.set(x1, y0 + 2, z, Blocks.LIGHT_GRAY_STAINED_GLASS);
                    }
                }
                // перегородки залов с проходами
                int mx = x0 + w / 2, mz = z0 + dp / 2;
                for (int z = z0 + 1; z < z1; z++) {
                    if (Math.abs(z - mz) > 2) {
                        d.box(mx, y0 + 1, z, mx, y0 + 3, z, Blocks.WHITE_TERRACOTTA);
                    }
                }
                for (int x = mx + 1; x < x1; x++) {
                    if ((x - mx) % 6 > 1) {
                        d.box(x, y0 + 1, mz, x, y0 + 3, mz, Blocks.WHITE_TERRACOTTA);
                    }
                }
            }
            int top = y + floors * 4;
            d.box(x0, top, z0, x1, top, z1, Blocks.SMOOTH_STONE);
            d.walls(x0, top + 1, z0, x1, top + 1, z1, wall);
            for (int f = 0; f < floors; f++) {
                int y0 = y + f * 4;
                boolean even = (f & 1) == 0;
                int z = even ? z1 - 2 : z1 - 3;
                for (int k = 0; k < 4; k++) {
                    int x = even ? x0 + 4 + k : x0 + 7 - k;
                    d.set(x, y0 + 1 + k, z, Blocks.STONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, even ? Direction.EAST : Direction.WEST));
                    if (k == 1 || k == 2) {
                        d.set(x, y0 + 4, z, Blocks.AIR);
                    }
                    d.set(x, y0 + 2 + k, z, Blocks.AIR);
                    d.set(x, y0 + 3 + k, z, Blocks.AIR);
                }
            }
            // вход с крыльцом
            int mx = x0 + w / 4;
            d.box(mx, y + 1, z0, mx + 3, y + 3, z0, Blocks.AIR);
            d.box(mx, y + 1, z1, mx + 1, y + 2, z1, Blocks.AIR);
            d.box(mx - 1, y, z0 - 2, mx + 4, y, z0 - 1, Blocks.POLISHED_ANDESITE);
            for (int i = 0; i < holes; i++) {
                int hx = x0 + d.r.nextInt(w), hz = d.r.nextBoolean() ? z0 : z1;
                d.hole(hx, y + 4 + d.r.nextInt(floors * 4 - 2), hz, 3 + d.r.nextDouble() * 3, y);
                d.rubble(hx, y + 1, hz, 5);
            }
        });
    }

    private void square(int x0, int z0, int w, int dp, int y) {
        add(x0, z0, x0 + w, z0 + dp, d -> {
            for (int x = 0; x <= w; x++) {
                for (int z = 0; z <= dp; z++) {
                    d.set(x0 + x, y, z0 + z, (x + z) % 8 == 0 || Math.floorMod(x - z, 8) == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE);
                }
            }
            for (int[] k : new int[][] {{5, 5}, {w - 5, 5}, {5, dp - 5}, {w - 5, dp - 5}}) {
                d.box(x0 + k[0] - 2, y + 1, z0 + k[1] - 2, x0 + k[0] + 2, y + 1, z0 + k[1] + 2, Blocks.STONE_BRICKS);
                d.box(x0 + k[0] - 1, y + 1, z0 + k[1] - 1, x0 + k[0] + 1, y + 1, z0 + k[1] + 1, Blocks.GRASS_BLOCK);
                d.set(x0 + k[0], y + 2, z0 + k[1], Blocks.AZALEA);
            }
            for (int a = 0; a < 4; a++) { // баррикады
                int bx = x0 + w / 4 + a * 5;
                d.box(bx, y + 1, z0 + 8 + a % 2 * (dp - 16), bx + 3, y + 1, z0 + 8 + a % 2 * (dp - 16), ModBlocks.SANDBAGS.get());
            }
        });
    }

    private void park(int x0, int z0, int w, int dp, int y) {
        solid = false;
        add(x0, z0, x0 + w, z0 + dp, d -> {
            d.box(x0, y, z0, x0 + w, y, z0 + dp, Blocks.GRASS_BLOCK);
            for (int i = 0; i <= 100; i++) {
                int px = x0 + w * i / 100, pz = z0 + dp * i / 100;
                d.box(px, y, pz, px + 1, y, pz, Blocks.DIRT_PATH);
                d.box(x0 + w - w * i / 100 - 1, y, pz, x0 + w - w * i / 100, y, pz, Blocks.DIRT_PATH);
            }
            int cx = x0 + w / 2, cz = z0 + dp / 2;
            d.disc(cx, y, cz, 5, Blocks.STONE_BRICKS);
            d.disc(cx, y, cz, 3.4, Blocks.WATER);
            for (int i = 0; i < w * dp / 110; i++) {
                int tx = x0 + 4 + d.r.nextInt(Math.max(1, w - 7)), tz = z0 + 4 + d.r.nextInt(Math.max(1, dp - 7));
                if (Math.hypot(tx - cx, tz - cz) > 9) {
                    tree(d.c, tx, y + 1, tz, d.r.nextInt(2), d.r);
                }
            }
        });
        solid = true;
    }

    private void shops(Lot l, int y, Random r) {
        office(l.x + 1, l.z + 1, Math.min(34, l.w - 2), 12, y, 1, Blocks.BRICKS, r);
        int z0 = l.z + 16, z1 = l.z + l.d;
        if (z1 - z0 < 8) {
            return;
        }
        solid = false;
        add(l.x, z0, l.x + l.w, z1, d -> {
            d.box(l.x, y, z0, l.x + l.w, y, z1, Blocks.GRAY_CONCRETE);
            for (int i = 3; i < l.w - 2; i += 5) {
                d.box(l.x + i, y, z0 + 2, l.x + i, y, z0 + 7, Blocks.WHITE_CONCRETE);
                if (d.r.nextInt(3) == 0) {
                    car(d, l.x + i + 1, y + 1, z0 + 3);
                }
            }
        });
        solid = true;
    }

    /** Брошенная машина. */
    private static void car(Draw d, int x, int y, int z) {
        Block b = pick(d.r, new Block[] {Blocks.RED_TERRACOTTA, Blocks.BLUE_TERRACOTTA, Blocks.BLACK_CONCRETE, Blocks.WHITE_TERRACOTTA, Blocks.BROWN_TERRACOTTA});
        d.box(x, y, z, x + 1, y, z + 3, b);
        d.box(x, y + 1, z + 1, x + 1, y + 1, z + 2, d.r.nextInt(2) == 0 ? Blocks.GRAY_STAINED_GLASS : b);
    }

    private void factory(int x0, int z0, int w, int dp, int y, Random pr) {
        int holes = pr.nextInt(4);
        add(x0 - 1, z0 - 1, x0 + w + 1, z0 + dp + 6, d -> {
            int x1 = x0 + w - 1, z1 = z0 + dp - 1;
            d.box(x0, y, z0, x1, y, z1, Blocks.SMOOTH_STONE);
            d.box(x0, y + 1, z0, x1, y + 10, z1, Blocks.AIR);
            d.walls(x0, y + 1, z0, x1, y + 10, z1, Blocks.BRICKS);
            for (int x = x0 + 2; x < x1; x++) {
                if ((x - x0) % 6 > 1) {
                    for (int h = 4; h <= 7; h++) {
                        d.set(x, y + h, z0, d.r.nextInt(4) == 0 ? Blocks.AIR : Blocks.GRAY_STAINED_GLASS);
                        d.set(x, y + h, z1, d.r.nextInt(4) == 0 ? Blocks.AIR : Blocks.GRAY_STAINED_GLASS);
                    }
                }
            }
            for (int z = z0; z <= z1; z++) { // пилообразная крыша
                int h = (z - z0) % 8;
                d.box(x0, y + 11 + h / 2, z, x1, y + 11 + h / 2, z, Blocks.GRAY_CONCRETE);
                d.box(x0, y + 11, z, x0, y + 11 + h / 2, z, Blocks.BRICKS);
                d.box(x1, y + 11, z, x1, y + 11 + h / 2, z, Blocks.BRICKS);
                if (h == 7) {
                    d.box(x0 + 1, y + 11, z, x1 - 1, y + 13, z, Blocks.GRAY_STAINED_GLASS);
                }
            }
            d.box(x0, y + 1, z0 + 11, x0, y + 5, z0 + 17, Blocks.AIR); // ворота
            d.box(x1, y + 1, z0 + 11, x1, y + 5, z0 + 17, Blocks.AIR);
            d.box(x0 + 4, y + 1, z0, x0 + 5, y + 2, z0, Blocks.AIR);
            for (int i = 0; i < 4; i++) { // станки
                int mx = x0 + 7 + i * 8;
                d.box(mx, y + 1, z0 + 5, mx + 2, y + 2, z0 + 7, Blocks.IRON_BLOCK);
                d.box(mx, y + 1, z1 - 7, mx + 2, y + 3, z1 - 5, Blocks.POLISHED_DEEPSLATE);
            }
            for (int cx : new int[] {x0 + 6, x0 + w - 8}) { // трубы
                for (int h = 1; h <= 26; h++) {
                    d.ring(cx, y + h, z1 + 4, 2, h > 22 ? Blocks.RED_CONCRETE : Blocks.BRICKS);
                }
            }
            for (int i = 0; i < holes; i++) {
                d.hole(x0 + d.r.nextInt(w), y + 8 + d.r.nextInt(5), z0 + d.r.nextInt(dp), 3 + d.r.nextDouble() * 3, y);
            }
        });
    }

    // ------------------------------------------------------------------ село

    private void village(FrontMap.Site s, Random r) {
        List<int[]> used = new ArrayList<>();
        if (r.nextInt(2) == 0) {
            int[] p = spot(s, r, used, 9, 13, 18, 45);
            if (p != null) {
                church(p[0], p[1], s.y());
            }
        }
        if (r.nextInt(3) > 0) {
            int[] p = spot(s, r, used, 5, 5, 25, 75);
            if (p != null) {
                waterTower(p[0], p[1], s.y());
            }
        }
        for (int k = 0; k < 2; k++) { // ферма на краю
            int[] p = spot(s, r, used, 16, 7, 45, 72);
            if (p != null) {
                barn(p[0] - 13, p[1] - 5, s.y(), r);
            }
        }
        for (int tries = 0; tries < 600 && used.size() < 20; tries++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 10 + r.nextDouble() * (s.radius() - 18);
            int cx = s.x() + (int) (Math.cos(a) * rad), cz = s.z() + (int) (Math.sin(a) * rad);
            FrontMap.RoadHit h = map.road(cx, cz);
            if (h.d < 13 || h.d > 22 && tries < 420) {
                continue; // дома стоят улицами вдоль дорог
            }
            // входом к дороге
            double e = map.road(cx + 4, cz).d, w0 = map.road(cx - 4, cz).d, so = map.road(cx, cz + 4).d, n = map.road(cx, cz - 4).d;
            boolean swap = Math.abs(so - n) > Math.abs(e - w0);
            boolean flip = swap ? so < n : e < w0;
            int w = 8 + r.nextInt(3), dp = 10 + r.nextInt(4);
            int hx = (swap ? w : dp) / 2 + 1, hz = (swap ? dp : w) / 2 + 1;
            if (!fits(used, cx, cz, hx + 2, hz + 2)) {
                continue;
            }
            used.add(new int[] {cx, cz, hx + 2, hz + 2});
            house(cx - hx + 1, cz - hz + 1, swap, w, dp, s.y(), r, flip);
            if (r.nextInt(3) > 0) {
                yard(cx, cz, hx + 4, hz + 4, s.y(), r);
            }
        }
    }

    private static boolean fits(List<int[]> used, int cx, int cz, int hx, int hz) {
        for (int[] u : used) {
            if (Math.abs(u[0] - cx) < u[2] + hx + 4 && Math.abs(u[1] - cz) < u[3] + hz + 4) {
                return false;
            }
        }
        return true;
    }

    private int[] spot(FrontMap.Site s, Random r, List<int[]> used, int hx, int hz, int r0, int r1) {
        for (int i = 0; i < 120; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = r0 + r.nextDouble() * (r1 - r0);
            int cx = s.x() + (int) (Math.cos(a) * rad), cz = s.z() + (int) (Math.sin(a) * rad);
            if (map.road(cx, cz).d > Math.max(hx, hz) + 8 && fits(used, cx, cz, hx, hz)) {
                used.add(new int[] {cx, cz, hx, hz});
                return new int[] {cx, cz};
            }
        }
        return null;
    }

    /** Двор: забор с проёмами, огород, иногда колодец и стог. */
    private void yard(int cx, int cz, int hx, int hz, int y, Random pr) {
        int extra = pr.nextInt(4);
        boolean keep = solid;
        solid = false;
        add(cx - hx - 1, cz - hz - 1, cx + hx + 1, cz + hz + 1, d -> {
            for (int x = -hx; x <= hx; x++) {
                for (int z = -hz; z <= hz; z++) {
                    boolean edge = Math.abs(x) == hx || Math.abs(z) == hz;
                    if (edge && Math.abs(x) > 1 && Math.abs(z) > 1 && d.r.nextInt(12) > 0 && map.road(cx + x, cz + z).d > 6.5) {
                        d.set(cx + x, y + 1, cz + z, Blocks.OAK_FENCE);
                    }
                }
            }
            if (extra == 0) {
                d.box(cx + hx - 2, y + 1, cz - hz + 1, cx + hx - 1, y + 2, cz - hz + 2, Blocks.HAY_BLOCK);
            } else if (extra == 1) { // колодец
                int wx = cx - hx + 2, wz = cz - hz + 2;
                d.walls(wx - 1, y + 1, wz - 1, wx + 1, y + 1, wz + 1, Blocks.COBBLESTONE);
                d.box(wx, y - 3, wz, wx, y, wz, Blocks.WATER);
                d.set(wx - 1, y + 2, wz, Blocks.OAK_FENCE);
                d.set(wx + 1, y + 2, wz, Blocks.OAK_FENCE);
                d.box(wx - 1, y + 3, wz, wx + 1, y + 3, wz, Blocks.SPRUCE_SLAB);
            } else if (extra == 2) {
                for (int x = cx - hx + 1; x <= cx - hx + 2; x++) {
                    for (int z = cz - 3; z <= cz + 3; z++) {
                        d.set(x, y, z, Blocks.FARMLAND);
                        d.set(x, y + 1, z, Blocks.POTATOES.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
                    }
                }
            }
        });
        solid = keep;
    }

    private void church(int cx, int cz, int y) {
        add(cx - 7, cz - 12, cx + 7, cz + 12, d -> {
            d.box(cx - 6, y, cz - 8, cx + 6, y, cz + 11, Blocks.POLISHED_ANDESITE);
            d.box(cx - 5, y + 1, cz - 7, cx + 5, y + 8, cz + 10, Blocks.AIR);
            d.walls(cx - 6, y + 1, cz - 8, cx + 6, y + 7, cz + 11, Blocks.WHITE_CONCRETE);
            for (int z = cz - 5; z < cz + 10; z += 3) {
                d.box(cx - 6, y + 3, z, cx - 6, y + 5, z, Blocks.YELLOW_STAINED_GLASS);
                d.box(cx + 6, y + 3, z, cx + 6, y + 5, z, Blocks.YELLOW_STAINED_GLASS);
            }
            for (int k = 0; k <= 6; k++) {
                d.box(cx - 6 + k, y + 8 + k / 2, cz - 8, cx - 6 + k, y + 8 + k / 2, cz + 11, Blocks.GREEN_TERRACOTTA);
                d.box(cx + 6 - k, y + 8 + k / 2, cz - 8, cx + 6 - k, y + 8 + k / 2, cz + 11, Blocks.GREEN_TERRACOTTA);
            }
            // колокольня и купол
            d.walls(cx - 3, y + 1, cz - 11, cx + 3, y + 14, cz - 6, Blocks.WHITE_CONCRETE);
            d.box(cx - 2, y + 1, cz - 10, cx + 2, y + 13, cz - 7, Blocks.AIR);
            for (int[] o : new int[][] {{-3, -9}, {3, -9}, {0, -11}, {0, -6}}) {
                d.box(cx + o[0], y + 10, cz + o[1], cx + o[0], y + 12, cz + o[1], Blocks.AIR);
            }
            for (int k = 0; k < 4; k++) {
                d.disc(cx, y + 15 + k, cz - 9, 3.4 - k * 0.9, Blocks.GOLD_BLOCK);
            }
            d.box(cx, y + 19, cz - 9, cx, y + 21, cz - 9, Blocks.IRON_BARS);
            d.box(cx - 1, y + 1, cz - 11, cx + 1, y + 3, cz - 11, Blocks.AIR);
            d.box(cx - 1, y + 1, cz - 8, cx + 1, y + 3, cz - 6, Blocks.AIR);
            for (int z = cz - 3; z < cz + 8; z += 2) {
                d.box(cx - 4, y + 1, z, cx - 2, y + 1, z, Blocks.SPRUCE_SLAB);
                d.box(cx + 2, y + 1, z, cx + 4, y + 1, z, Blocks.SPRUCE_SLAB);
            }
        });
    }

    private void waterTower(int cx, int cz, int y) {
        add(cx - 4, cz - 4, cx + 4, cz + 4, d -> {
            for (int h = 1; h <= 13; h++) {
                d.ring(cx, y + h, cz, 1.6, h % 4 == 0 ? Blocks.RED_TERRACOTTA : Blocks.BRICKS);
            }
            for (int h = 14; h <= 18; h++) {
                d.disc(cx, y + h, cz, h == 18 ? 2.2 : 3.2, Blocks.LIGHT_GRAY_CONCRETE);
            }
        });
    }

    private void barn(int x0, int z0, int y, Random pr) {
        boolean hit = pr.nextInt(3) == 0;
        add(x0 - 1, z0 - 1, x0 + 27, z0 + 11, d -> {
            d.box(x0, y, z0, x0 + 25, y, z0 + 9, Blocks.COARSE_DIRT);
            d.box(x0 + 1, y + 1, z0 + 1, x0 + 24, y + 6, z0 + 8, Blocks.AIR);
            d.walls(x0, y + 1, z0, x0 + 25, y + 4, z0 + 9, Blocks.BRICKS);
            for (int x = x0 + 3; x < x0 + 24; x += 4) {
                d.set(x, y + 3, z0, Blocks.AIR);
                d.set(x, y + 3, z0 + 9, Blocks.AIR);
            }
            for (int k = 0; k < 5; k++) {
                d.box(x0, y + 5 + k / 2, z0 + k, x0 + 25, y + 5 + k / 2, z0 + k, Blocks.LIGHT_GRAY_TERRACOTTA);
                d.box(x0, y + 5 + k / 2, z0 + 9 - k, x0 + 25, y + 5 + k / 2, z0 + 9 - k, Blocks.LIGHT_GRAY_TERRACOTTA);
            }
            d.box(x0, y + 1, z0 + 3, x0, y + 4, z0 + 6, Blocks.AIR);
            d.box(x0 + 25, y + 1, z0 + 3, x0 + 25, y + 4, z0 + 6, Blocks.AIR);
            for (int x = x0 + 4; x < x0 + 22; x += 3) {
                d.set(x, y + 1, z0 + 1 + d.r.nextInt(2), Blocks.HAY_BLOCK);
            }
            if (hit) {
                d.hole(x0 + 12, y + 5, z0 + 4, 4, y);
            }
        });
    }

    /** Дом: v — поперёк (фронтон), u — вдоль конька. */
    private void house(int x0, int z0, boolean swap, int w, int len, int y, Random pr) {
        house(x0, z0, swap, w, len, y, pr, false);
    }

    /** flip — дверь с дальнего торца. */
    private void house(int x0, int z0, boolean swap, int w, int len, int y, Random pr, boolean flip) {
        Block wall = pick(pr, new Block[] {Blocks.OAK_PLANKS, Blocks.BRICKS, Blocks.WHITE_TERRACOTTA, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS});
        Block roof = pick(pr, new Block[] {Blocks.DARK_OAK_STAIRS, Blocks.BRICK_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.SPRUCE_STAIRS});
        FrontMap.Site at = map.siteAt(x0, z0, 0);
        boolean ruin = pr.nextInt(100) < 25 && (at == null || at.type() == FrontMap.Type.VILLAGE);
        boolean cellar = pr.nextInt(100) < 40;
        add(x0 - 5, z0 - 5, x0 + (swap ? w : len) + 4, z0 + (swap ? len : w) + 4, d -> {
            for (int u = 0; u < len; u++) {
                for (int v = 0; v < w; v++) {
                    boolean ext = u == 0 || v == 0 || u == len - 1 || v == w - 1;
                    uv(d, x0, z0, swap, u, y, v, (ext ? Blocks.COBBLESTONE : Blocks.SPRUCE_PLANKS).defaultBlockState());
                    for (int h = 1; h <= 4; h++) {
                        boolean corner = (u == 0 || u == len - 1) && (v == 0 || v == w - 1);
                        BlockState b = AIR;
                        if (corner) {
                            b = Blocks.STRIPPED_OAK_LOG.defaultBlockState();
                        } else if (ext) {
                            boolean win = h == 2 && ((v == 0 || v == w - 1) && u % 3 == 2 || (u == 0 || u == len - 1) && (v == 2 || v == w - 3));
                            b = win ? Blocks.GLASS.defaultBlockState() : wall.defaultBlockState();
                        } else if (u == len / 2 && v != w / 2 && h < 4) {
                            b = wall.defaultBlockState();
                        }
                        if (ruin && h > 1 && d.r.nextInt(6) < h) {
                            b = AIR;
                        }
                        uv(d, x0, z0, swap, u, y + h, v, b);
                    }
                }
            }
            int du = flip ? len - 1 : 0, out = flip ? 1 : -1;
            uv(d, x0, z0, swap, du, y + 1, w / 2, AIR);
            uv(d, x0, z0, swap, du, y + 2, w / 2, AIR);
            for (int k = 1; k <= 4; k++) { // дорожка к улице
                uv(d, x0, z0, swap, du + out * k, y, w / 2, (k == 1 ? Blocks.COBBLESTONE : Blocks.DIRT_PATH).defaultBlockState());
            }
            if (!ruin) {
                // двускатная крыша
                for (int k = 0; k <= w / 2; k++) {
                    int a = -1 + k, b = w - k;
                    if (a > b) {
                        break;
                    }
                    for (int u = -1; u <= len; u++) {
                        Direction in = swap ? Direction.EAST : Direction.SOUTH;
                        if (a == b) {
                            uv(d, x0, z0, swap, u, y + 4 + k, a, wall.defaultBlockState());
                        } else {
                            uv(d, x0, z0, swap, u, y + 4 + k, a, roof.defaultBlockState().setValue(StairBlock.FACING, in));
                            uv(d, x0, z0, swap, u, y + 4 + k, b, roof.defaultBlockState().setValue(StairBlock.FACING, in.getOpposite()));
                        }
                        if (u == 0 || u == len - 1) { // фронтон
                            for (int v = a + 1; v < b; v++) {
                                uv(d, x0, z0, swap, u, y + 4 + k, v, wall.defaultBlockState());
                            }
                        } else if (u > 0 && u < len - 1) {
                            for (int v = a + 1; v < b; v++) {
                                uv(d, x0, z0, swap, u, y + 4 + k, v, AIR);
                            }
                        }
                    }
                }
                for (int h = 1; h < 4 + w / 2 + 2; h++) {
                    uv(d, x0, z0, swap, len - 2, y + h, 1, Blocks.BRICKS.defaultBlockState());
                }
                uv(d, x0, z0, swap, len - 3, y + 1, w - 2, Blocks.CRAFTING_TABLE.defaultBlockState());
                uv(d, x0, z0, swap, 2, y + 1, 1, Blocks.BOOKSHELF.defaultBlockState());
            } else {
                d.rubble(x0 + (swap ? w : len) / 2, y + 1, z0 + (swap ? len : w) / 2, 4);
            }
            if (cellar) { // подвал с лестницей
                for (int u = 1; u < len - 1; u++) {
                    for (int v = 1; v < w - 1; v++) {
                        boolean ext = u == 1 || v == 1 || u == len - 2 || v == w - 2;
                        uv(d, x0, z0, swap, u, y - 4, v, Blocks.COBBLESTONE.defaultBlockState());
                        for (int h = 1; h <= 3; h++) {
                            uv(d, x0, z0, swap, u, y - 4 + h, v, ext ? Blocks.COBBLESTONE.defaultBlockState() : AIR);
                        }
                    }
                }
                Direction f = swap ? Direction.EAST : Direction.SOUTH; // спиной к стене v=1
                for (int h = -3; h <= 0; h++) {
                    uv(d, x0, z0, swap, 2, y + h, 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f));
                }
            }
        });
    }

    // ------------------------------------------------------------------ точки

    private void hangar(int x0, int z0, int w, int len, int y, boolean openWest) {
        add(x0 - 1, z0 - 1, x0 + len, z0 + w, d -> {
            // арка поперёк z, вдоль x
            double rad = w / 2.0;
            for (int x = 0; x < len; x++) {
                for (int z = 0; z < w; z++) {
                    d.set(x0 + x, y, z0 + z, Blocks.SMOOTH_STONE);
                    double t = (z + 0.5 - rad) / rad;
                    int h = (int) Math.round(Math.sqrt(Math.max(0, 1 - t * t)) * (rad * 0.75)) + 1;
                    double t2 = (Math.abs(z + 0.5 - rad) + 1) / rad;
                    int h2 = t2 >= 1 ? 0 : (int) Math.round(Math.sqrt(1 - t2 * t2) * (rad * 0.75)) + 1;
                    boolean back = x == (openWest ? len - 1 : 0);
                    for (int k = 1; k <= h; k++) {
                        d.set(x0 + x, y + k, z0 + z, k >= Math.min(h, h2 + 1) || back ? Blocks.GREEN_TERRACOTTA : Blocks.AIR);
                    }
                }
            }
        });
    }

    private void base(FrontMap.Site s) {
        int x = s.x(), z = s.z(), y = s.y();
        int f = x < 0 ? 1 : -1; // куда смотрит фронт
        add(x - 60, z - 60, x + 60, z + 60, d -> {
            d.box(x - 46, y, z - 46, x + 46, y, z + 46, Blocks.LIGHT_GRAY_CONCRETE);
            for (int i = -46; i <= 46; i += 8) {
                d.box(x + i, y, z - 46, x + i, y, z + 46, Blocks.GRAY_CONCRETE);
                d.box(x - 46, y, z + i, x + 46, y, z + i, Blocks.GRAY_CONCRETE);
            }
            d.box(x - 46, y, z - 4, x + 60, y, z + 4, Blocks.GRAY_CONCRETE);
            d.box(x - 60, y, z - 4, x + 46, y, z + 4, Blocks.GRAY_CONCRETE);
            // бетонный забор с воротами на фронт и в тыл
            for (int i = -58; i <= 58; i++) {
                if (Math.abs(i) > 6) {
                    d.box(x - 58, y + 1, z + i, x - 58, y + 3, z + i, ModBlocks.CONCRETE_FORT.get());
                    d.box(x + 58, y + 1, z + i, x + 58, y + 3, z + i, ModBlocks.CONCRETE_FORT.get());
                }
                d.box(x + i, y + 1, z - 58, x + i, y + 3, z - 58, ModBlocks.CONCRETE_FORT.get());
                d.box(x + i, y + 1, z + 58, x + i, y + 3, z + 58, ModBlocks.CONCRETE_FORT.get());
            }
            // вышки
            for (int sx : new int[] {-54, 54}) {
                for (int sz : new int[] {-54, 54}) {
                    int tx = x + sx, tz = z + sz;
                    for (int h = 1; h <= 7; h++) {
                        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                            d.set(tx + c[0], y + h, tz + c[1], Blocks.SPRUCE_LOG);
                        }
                        d.set(tx, y + h, tz + 1, Blocks.SPRUCE_LOG);
                        d.set(tx, y + h, tz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
                    }
                    d.box(tx - 2, y + 8, tz - 2, tx + 2, y + 8, tz + 2, Blocks.SPRUCE_PLANKS);
                    d.set(tx, y + 8, tz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
                    d.set(tx, y + 8, tz + 1, Blocks.SPRUCE_LOG);
                    d.walls(tx - 2, y + 9, tz - 2, tx + 2, y + 9, tz + 2, ModBlocks.SANDBAGS.get());
                    for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                        d.box(tx + c[0], y + 10, tz + c[1], tx + c[0], y + 11, tz + c[1], Blocks.SPRUCE_LOG);
                    }
                    d.box(tx - 2, y + 12, tz - 2, tx + 2, y + 12, tz + 2, Blocks.SPRUCE_PLANKS);
                }
            }
        });
        // бункер штаба в глубине базы
        int bx = x - f * 30;
        add(bx - 7, z - 33, bx + 7, z - 19, d -> {
            int cz = z - 26;
            d.box(bx - 6, y, cz - 6, bx + 6, y + 5, cz + 6, ModBlocks.BUNKER_CONCRETE.get());
            d.box(bx - 4, y + 1, cz - 4, bx + 4, y + 3, cz + 4, Blocks.AIR);
            d.box(bx - 1, y + 1, cz + 4, bx + 1, y + 2, cz + 6, Blocks.AIR); // вход
            d.box(bx - 3, y + 1, cz + 7, bx + 3, y + 1, cz + 7, Blocks.AIR);
            d.set(bx, y + 4, cz, Blocks.SEA_LANTERN);
        });
        hangar(x - 24, z + 16, 17, 22, y, f < 0);
        hangar(x + 4, z + 16, 17, 22, y, f < 0);
        hangar(x - 24, z + 37, 17, 22, y, f < 0);
        Random r = new Random(map.seed + x);
        office(x + 4, z - 40, 26, 12, y, 2, Blocks.GREEN_TERRACOTTA, new Random(1)); // казарма
        house(x - 22 * f - 6, z - 12, false, 8, 10, y, new Random(r.nextLong()));
    }

    /** Куда ставить штаб и склад базы, где появляться. */
    public int[] hq(FrontMap.Site s) {
        return new int[] {s.x() - (s.x() < 0 ? 30 : -30), s.y() + 1, s.z() - 28};
    }

    public int[] warehouse(FrontMap.Site s) {
        return new int[] {s.x() - 14, s.y() + 1, s.z() + 24};
    }

    public int[] spawn(FrontMap.Site s) {
        return new int[] {s.x(), s.y() + 1, s.z()};
    }

    private void power(FrontMap.Site s) {
        int x = s.x(), z = s.z(), y = s.y();
        add(x - 50, z - 50, x + 50, z + 50, d -> {
            d.box(x - 46, y, z - 46, x + 46, y, z + 46, Blocks.GRAVEL);
            for (int cx : new int[] {x - 26, x + 8}) { // градирни
                for (int h = 0; h <= 30; h++) {
                    double rad = 12 - 4.5 * Math.sin(Math.PI * Math.min(1, h / 40.0)) ;
                    d.ring(cx, y + 1 + h, z - 26, rad, h > 27 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE);
                    d.ring(cx, y + 1 + h, z - 26, rad - 1, h > 27 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE);
                }
                d.box(cx - 1, y + 1, z - 16, cx + 1, y + 3, z - 12, Blocks.AIR);
            }
            for (int i = 0; i < 4; i++) { // трансформаторы
                int tx = x + 24 + i % 2 * 10, tz = z + 8 + i / 2 * 12;
                d.box(tx, y + 1, tz, tx + 4, y + 3, tz + 4, Blocks.GRAY_TERRACOTTA);
                d.box(tx + 1, y + 4, tz + 1, tx + 1, y + 6, tz + 1, Blocks.IRON_BARS);
                d.box(tx + 3, y + 4, tz + 3, tx + 3, y + 6, tz + 3, Blocks.IRON_BARS);
            }
            for (int i = -48; i <= 48; i++) {
                if (Math.abs(i) > 5) {
                    d.box(x + i, y + 1, z - 48, x + i, y + 2, z - 48, Blocks.IRON_BARS);
                    d.box(x + i, y + 1, z + 48, x + i, y + 2, z + 48, Blocks.IRON_BARS);
                    d.box(x - 48, y + 1, z + i, x - 48, y + 2, z + i, Blocks.IRON_BARS);
                    d.box(x + 48, y + 1, z + i, x + 48, y + 2, z + i, Blocks.IRON_BARS);
                }
            }
        });
        factory(x - 42, z + 6, 38, 30, y, new Random(3));
    }

    private void oil(FrontMap.Site s) {
        int x = s.x(), z = s.z(), y = s.y();
        add(x - 42, z - 42, x + 42, z + 42, d -> {
            d.box(x - 36, y, z - 36, x + 36, y, z + 36, Blocks.GRAVEL);
            for (int[] c : new int[][] {{-22, -22}, {0, -22}, {22, -22}, {-22, 22}, {22, 22}}) {
                for (int h = 1; h <= 9; h++) {
                    d.disc(x + c[0], y + h, z + c[1], 7, Blocks.WHITE_CONCRETE);
                    if (h < 9) {
                        d.disc(x + c[0], y + h, z + c[1], 6, h < 5 ? Blocks.BLACK_CONCRETE : Blocks.AIR);
                    }
                }
                d.ring(x + c[0], y + 1, z + c[1], 9, ModBlocks.SANDBAG_WALL.get());
                d.box(x + c[0] - 9, y + 1, z + c[1], x + c[0] - 8, y + 1, z + c[1], Blocks.AIR);
            }
            d.box(x - 30, y + 1, z - 10, x + 30, y + 1, z - 10, Blocks.IRON_BLOCK); // трубопровод
            d.box(x + 8, y + 1, z + 8, x + 8, y + 1, z + 30, Blocks.IRON_BLOCK);
            d.box(x - 4, y + 5, z + 18, x + 4, y + 5, z + 28, Blocks.RED_CONCRETE); // навес заправки
            for (int[] c : new int[][] {{-4, 18}, {4, 18}, {-4, 28}, {4, 28}}) {
                d.box(x + c[0], y + 1, z + c[1], x + c[0], y + 4, z + c[1], Blocks.IRON_BLOCK);
            }
        });
    }

    private void airfield(FrontMap.Site s) {
        int x = s.x(), z = s.z(), y = s.y();
        int in = z < 0 ? 1 : -1;
        add(x - 136, z - 70, x + 136, z + 70, d -> {
            int rz = z - in * 40;
            d.box(x - 132, y, rz - 10, x + 132, y, rz + 10, Blocks.GRAY_CONCRETE); // полоса
            for (int i = -124; i <= 124; i += 12) {
                d.box(x + i, y, rz, x + i + 5, y, rz, Blocks.WHITE_CONCRETE);
            }
            for (int e : new int[] {-130, 126}) {
                for (int i = -8; i <= 8; i += 2) {
                    d.box(x + e, y, rz + i, x + e + 4, y, rz + i, Blocks.WHITE_CONCRETE);
                }
            }
            d.box(x - 80, y, z - 22, x + 80, y, z + 60, Blocks.LIGHT_GRAY_CONCRETE); // стоянка
            for (int e : new int[] {-100, 92}) {
                d.box(x + e, y, Math.min(rz, z), x + e + 8, y, Math.max(rz, z) + 8, Blocks.GRAY_CONCRETE);
                d.box(x + Math.min(e, 0), y, z, x + Math.max(e, 0), y, z + 8, Blocks.GRAY_CONCRETE);
            }
            for (int i = 0; i < 5; i++) { // воронки в полосе
                int hx = x - 110 + d.r.nextInt(220), hz = rz - 6 + d.r.nextInt(12);
                d.disc(hx, y, hz, 2.5, Blocks.COARSE_DIRT);
                d.disc(hx, y, hz, 1.4, Blocks.AIR);
            }
            // диспетчерская
            int tx = x + 60, tz = z + in * 30;
            d.box(tx - 4, y, tz - 4, tx + 4, y + 14, tz + 4, Blocks.WHITE_CONCRETE);
            d.box(tx - 3, y + 1, tz - 3, tx + 3, y + 14, tz + 3, Blocks.AIR);
            d.box(tx - 5, y + 15, tz - 5, tx + 5, y + 15, tz + 5, Blocks.SMOOTH_STONE);
            d.walls(tx - 5, y + 16, tz - 5, tx + 5, y + 18, tz + 5, Blocks.LIGHT_BLUE_STAINED_GLASS);
            d.box(tx - 5, y + 19, tz - 5, tx + 5, y + 19, tz + 5, Blocks.RED_CONCRETE);
            d.box(tx, y + 1, tz - 4, tx, y + 2, tz - 4, Blocks.AIR);
            for (int h = 1; h <= 15; h++) {
                d.set(tx + 3, y + h, tz, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
            }
        });
        hangar(x - 70, z + in * 18 - 9, 19, 26, y, true);
        hangar(x - 30, z + in * 18 - 9, 19, 26, y, true);
        hangar(x + 10, z + in * 18 - 9, 19, 26, y, true);
    }

    private void tower(FrontMap.Site s) {
        int x = s.x(), z = s.z(), y = s.y();
        int tx = x + 8;
        add(x - 18, z - 18, x + 18, z + 18, d -> {
            d.box(x - 14, y, z - 14, x + 16, y, z + 14, Blocks.GRAVEL);
            for (int h = 1; h <= 46; h++) {
                int w = h < 16 ? 3 : h < 32 ? 2 : 1;
                for (int sx : new int[] {-w, w}) {
                    for (int sz : new int[] {-w, w}) {
                        d.set(tx + sx, y + h, z + sz, Blocks.IRON_BLOCK);
                    }
                }
                if (h % 5 == 0) {
                    d.walls(tx - w, y + h, z - w, tx + w, y + h, z + w, h % 10 == 0 ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE);
                }
                d.set(tx, y + h, z + 1, Blocks.IRON_BLOCK);
                d.set(tx, y + h, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
            }
            d.box(tx - 3, y + 40, z - 3, tx + 3, y + 40, z + 3, Blocks.IRON_BLOCK);
            d.set(tx, y + 40, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
            d.box(tx, y + 47, z, tx, y + 52, z, Blocks.IRON_BARS);
            d.set(tx, y + 53, z, Blocks.REDSTONE_BLOCK);
        });
        house(x - 13, z - 4, false, 8, 10, y, new Random(5));
    }

    // ------------------------------------------------------------------ мелочи по всей карте

    private void details(Random r) {
        solid = false;
        FrontMap.Site c = map.city;
        int y = c.y();
        for (int k = 0; k < map.streetsV() + map.streetsH(); k++) {
            boolean v = k < map.streetsV();
            for (int t = -300; t <= 300; t += 13) {
                double sx = v ? map.streetX(k, t) : t, sz = v ? t : map.streetZ(k - map.streetsV(), t);
                if (c.dist(sx, sz) > c.radius() - 6 || map.river(sx, sz) < FrontMap.RIVER_HALF + 8) {
                    continue;
                }
                FrontMap.StreetHit st = map.street(sx, sz);
                if (Math.max(st.v, st.h) < 10) {
                    continue; // перекрёсток
                }
                double side = r.nextBoolean() ? 4.6 : -4.6;
                lamp(Mth.floor(v ? sx + side : sx), y, Mth.floor(v ? sz : sz + side), r.nextInt(3) > 0);
                if (r.nextInt(6) == 0) {
                    wreck(Mth.floor(v ? sx + (r.nextBoolean() ? 1 : -3) : sx), y, Mth.floor(v ? sz + 3 : sz + (r.nextBoolean() ? 1 : -3)), r.nextInt(12) == 0);
                }
            }
        }
        for (FrontMap.Road rd : map.roads) { // баррикады на въездах
            for (int e : new int[] {0, rd.x.length - 1}) {
                int n = e == 0 ? 1 : e - 1;
                if (Math.abs(c.dist(rd.x[e], rd.z[e]) - c.radius()) < 12) {
                    barricade((int) rd.x[e], y, (int) rd.z[e], Math.abs(rd.x[n] - rd.x[e]) > Math.abs(rd.z[n] - rd.z[e]));
                }
            }
        }
        int gas = 0;
        for (FrontMap.Road rd : map.roads) {
            boolean post = false;
            for (int i = 1; i + 1 < rd.x.length; i++) {
                double dx = rd.x[i + 1] - rd.x[i], dz = rd.z[i + 1] - rd.z[i], l = Math.hypot(dx, dz);
                if (l < 1 || map.siteAt(rd.x[i], rd.z[i], 14) != null || map.river(rd.x[i], rd.z[i]) < 34 || map.rail(rd.x[i], rd.z[i]).d < 14) {
                    continue;
                }
                double nx = -dz / l, nz = dx / l;
                if (i % 2 == 0) {
                    pole((int) (rd.x[i] + nx * 8), (int) (rd.z[i] + nz * 8), Math.abs(dx) > Math.abs(dz));
                }
                int ry = (int) Math.round(rd.y[i]);
                if (r.nextInt(9) == 0) {
                    wreck((int) (rd.x[i] + nx * (r.nextInt(7) - 3)), ry, (int) (rd.z[i] + nz * (r.nextInt(7) - 3)), Math.abs(rd.x[i]) < 800 && r.nextInt(2) == 0);
                }
                if (!post && Math.abs(rd.x[i]) > 230 && Math.abs(rd.x[i]) < 700 && r.nextInt(3) == 0) {
                    post = true;
                    checkpoint((int) rd.x[i], ry, (int) rd.z[i], Math.abs(dx) > Math.abs(dz));
                } else if (gas < 4 && Math.abs(rd.x[i]) > 500 && r.nextInt(14) == 0 && Math.max(Math.abs(nx), Math.abs(nz)) > 0.85) {
                    gas++;
                    gasStation((int) (rd.x[i] + nx * 19), ry, (int) (rd.z[i] + nz * 19));
                }
            }
        }
        // доты у окопов
        for (int line = 0; line < 2; line++) {
            for (int z = -860; z <= 860; z += 24) {
                int sign = line == 0 ? -1 : 1;
                for (int x = sign * 430 - 90; x <= sign * 430 + 90; x += 2) {
                    if (map.trench(x, z) < 1 && Math.floorMod(z / 24 * 7 + line, 4) == 0) {
                        int px = x - sign * 7;
                        if (map.siteAt(px, z, 12) == null && map.road(px, z).d > 14 && map.rail(px, z).d > 12 && map.river(px, z) > 45) {
                            pillbox(px, z, -sign);
                        }
                        break;
                    }
                }
            }
        }
        // ЛЭП от станции к городу
        for (FrontMap.Site s : map.sites) {
            if (s.type() == FrontMap.Type.POWER) {
                double l = Math.hypot(s.x(), s.z());
                for (double t = 90; t < l - 240; t += 62) {
                    int px = (int) (s.x() * (1 - t / l)), pz = (int) (s.z() * (1 - t / l));
                    if (map.siteAt(px, pz, 8) == null && map.road(px, pz).d > 8 && map.river(px, pz) > 18 && map.rail(px, pz).d > 8) {
                        pylon(px, pz);
                    }
                }
            }
        }
        // станция: платформа и брошенный состав
        solid = true;
        int rz = Mth.floor(map.railZ(80));
        add(52, rz - 2, 130, rz + 8, d -> {
            d.box(54, y + 1, rz + 3, 110, y + 1, rz + 6, Blocks.SMOOTH_STONE);
            for (int x = 58; x < 108; x += 10) {
                d.box(x, y + 2, rz + 5, x, y + 4, rz + 5, Blocks.IRON_BLOCK);
            }
            d.box(56, y + 5, rz + 3, 108, y + 5, rz + 6, Blocks.GREEN_TERRACOTTA);
            for (int w = 0; w < 4; w++) {
                int x = 60 + w * 15;
                Block b = w % 2 == 0 ? Blocks.BROWN_TERRACOTTA : Blocks.GRAY_TERRACOTTA;
                d.box(x, y + 1, rz - 1, x + 12, y + 1, rz + 1, Blocks.POLISHED_BLACKSTONE);
                d.walls(x, y + 2, rz - 1, x + 12, y + 4, rz + 1, b);
                d.box(x + 1, y + 2, rz, x + 11, y + 4, rz, Blocks.AIR);
                d.box(x, y + 5, rz - 1, x + 12, y + 5, rz + 1, b);
                d.box(x + 5, y + 2, rz + 1, x + 7, y + 4, rz + 1, Blocks.AIR);
                if (w == 2) {
                    d.hole(x + 6, y + 4, rz, 3, y + 1);
                }
            }
        });
    }

    private int groundY(int x, int z) {
        return (int) Math.round(map.height(x + 0.5, z + 0.5, map.road(x + 0.5, z + 0.5)));
    }

    private void lamp(int x, int y, int z, boolean lit) {
        add(x, z, x, z, d -> {
            d.box(x, y + 1, z, x, y + 5, z, Blocks.IRON_BARS);
            d.set(x, y + 6, z, lit ? Blocks.SEA_LANTERN : Blocks.GRAY_STAINED_GLASS);
        });
    }

    private void pole(int x, int z, boolean alongX) {
        if (map.road(x, z).d < 6 || map.siteAt(x, z, 2) != null) {
            return;
        }
        int y = groundY(x, z);
        add(x - 1, z - 1, x + 1, z + 1, d -> {
            boolean broken = d.r.nextInt(7) == 0;
            d.box(x, y + 1, z, x, y + (broken ? 3 : 8), z, Blocks.SPRUCE_LOG);
            if (!broken) {
                d.box(alongX ? x : x - 1, y + 8, alongX ? z - 1 : z, alongX ? x : x + 1, y + 8, alongX ? z + 1 : z, Blocks.SPRUCE_FENCE);
                d.set(x, y + 8, z, Blocks.SPRUCE_LOG);
            }
        });
    }

    /** Брошенная или сгоревшая техника: легковушка или танк. */
    private void wreck(int x, int y, int z, boolean tank) {
        add(x - 3, z - 4, x + 4, z + 5, d -> {
            if (!tank) {
                if (d.r.nextInt(3) == 0) {
                    d.box(x, y + 1, z, x + 1, y + 1, z + 3, Blocks.BLACK_CONCRETE);
                    d.box(x, y + 2, z + 1, x + 1, y + 2, z + 2, Blocks.COAL_BLOCK);
                } else {
                    car(d, x, y + 1, z);
                }
                return;
            }
            d.box(x - 2, y + 1, z - 3, x - 2, y + 1, z + 3, Blocks.POLISHED_BLACKSTONE);
            d.box(x + 2, y + 1, z - 3, x + 2, y + 1, z + 3, Blocks.POLISHED_BLACKSTONE);
            d.box(x - 1, y + 1, z - 3, x + 1, y + 2, z + 3, Blocks.BROWN_TERRACOTTA);
            d.box(x - 1, y + 3, z - 1, x + 1, y + 3, z + 1, Blocks.COAL_BLOCK);
            d.box(x, y + 3, z + 2, x, y + 3, z + 5, Blocks.IRON_BARS);
        });
    }

    private void barricade(int x, int y, int z, boolean acrossZ) {
        add(x - 8, z - 8, x + 8, z + 8, d -> {
            for (int i = -5; i <= 5; i++) {
                if (i >= -1 && i <= 1) {
                    continue; // проезд змейкой
                }
                int o = i < 0 ? 0 : 5;
                d.box(acrossZ ? x + o : x + i, y + 1, acrossZ ? z + i : z + o, acrossZ ? x + o : x + i, y + 2, acrossZ ? z + i : z + o, ModBlocks.SANDBAGS.get());
            }
            hedgehog(d, acrossZ ? x - 4 : x + 2, y + 1, acrossZ ? z + 2 : z - 4);
            hedgehog(d, acrossZ ? x - 4 : x - 2, y + 1, acrossZ ? z - 2 : z - 4);
        });
    }

    private static void hedgehog(Draw d, int x, int y, int z) {
        d.set(x, y, z, Blocks.IRON_BARS);
        d.set(x + 1, y, z, Blocks.IRON_BARS);
        d.set(x - 1, y, z, Blocks.IRON_BARS);
        d.set(x, y, z + 1, Blocks.IRON_BARS);
        d.set(x, y, z - 1, Blocks.IRON_BARS);
        d.set(x, y + 1, z, Blocks.IRON_BARS);
    }

    private void checkpoint(int x, int y, int z, boolean alongX) {
        add(x - 9, z - 9, x + 9, z + 9, d -> {
            for (int s : new int[] {-1, 1}) {
                for (int i = -4; i <= 4; i++) { // бетон и мешки вдоль обочин
                    int px = alongX ? x + i : x + s * 6, pz = alongX ? z + s * 6 : z + i;
                    d.box(px, y - 3, pz, px, y, pz, Blocks.DIRT);
                    d.box(px, y + 1, pz, px, y + 2, pz, Math.abs(i) < 3 ? ModBlocks.SANDBAGS.get() : ModBlocks.CONCRETE_FORT.get());
                }
                int bx = alongX ? x + s * 4 : x + s * 2, bz = alongX ? z + s * 2 : z + s * 4;
                d.box(bx, y + 1, bz, bx, y + 1, bz, ModBlocks.CONCRETE_FORT.get());
            }
            int hx = alongX ? x - 1 : x + 7, hz = alongX ? z + 7 : z - 1; // будка
            d.box(hx, y - 3, hz, hx + 3, y, hz + 3, Blocks.COBBLESTONE);
            d.walls(hx, y + 1, hz, hx + 3, y + 3, hz + 3, Blocks.SPRUCE_PLANKS);
            d.box(hx + 1, y + 1, hz + 1, hx + 2, y + 3, hz + 2, Blocks.AIR);
            d.box(hx, y + 4, hz, hx + 3, y + 4, hz + 3, Blocks.SPRUCE_SLAB);
            d.box(alongX ? hx + 1 : hx, y + 1, alongX ? hz : hz + 1, alongX ? hx + 1 : hx, y + 2, alongX ? hz : hz + 1, Blocks.AIR);
        });
    }

    private void gasStation(int x, int y, int z) {
        if (map.siteAt(x, z, 20) != null || map.road(x, z).d < 14 || map.river(x, z) < 45) {
            return;
        }
        solid = true;
        add(x - 11, z - 11, x + 11, z + 11, d -> {
            d.box(x - 10, y - 6, z - 10, x + 10, y - 1, z + 10, Blocks.DIRT);
            d.box(x - 10, y, z - 10, x + 10, y, z + 10, Blocks.GRAY_CONCRETE);
            d.box(x - 10, y + 1, z - 10, x + 10, y + 10, z + 10, Blocks.AIR);
            d.box(x - 6, y + 5, z - 4, x + 6, y + 5, z + 4, Blocks.RED_CONCRETE);
            for (int[] o : new int[][] {{-5, -3}, {5, -3}, {-5, 3}, {5, 3}}) {
                d.box(x + o[0], y + 1, z + o[1], x + o[0], y + 4, z + o[1], Blocks.WHITE_CONCRETE);
            }
            for (int px : new int[] {x - 2, x + 2}) {
                d.box(px, y + 1, z, px, y + 2, z, Blocks.RED_TERRACOTTA);
            }
            d.walls(x - 5, y + 1, z + 6, x + 5, y + 3, z + 10, Blocks.WHITE_CONCRETE);
            d.box(x - 4, y + 2, z + 6, x + 4, y + 3, z + 6, Blocks.GLASS);
            d.box(x, y + 1, z + 6, x, y + 2, z + 6, Blocks.AIR);
            d.box(x - 5, y + 4, z + 6, x + 5, y + 4, z + 10, Blocks.RED_CONCRETE);
        });
        solid = false;
    }

    /** Дот: бетонная коробка с бойницами на нейтральную полосу (f — её сторона по x). */
    private void pillbox(int x, int z, int f) {
        int y = groundY(x, z) - 1;
        add(x - 4, z - 4, x + 4, z + 4, d -> {
            d.box(x - 3, y - 3, z - 3, x + 3, y + 4, z + 3, ModBlocks.BUNKER_CONCRETE.get());
            d.box(x - 2, y + 1, z - 2, x + 2, y + 3, z + 2, Blocks.AIR);
            d.box(x + f * 3, y + 2, z - 1, x + f * 3, y + 2, z + 1, Blocks.AIR);
            d.set(x, y + 2, z + 3, Blocks.AIR);
            d.set(x, y + 2, z - 3, Blocks.AIR);
            d.box(x - f * 3, y + 1, z, x - f * 3, y + 2, z, Blocks.AIR);
            d.box(x - f * 4, y + 1, z, x - f * 4, y + 3, z, Blocks.AIR);
            d.box(x - f * 4, y, z, x - f * 4, y, z, Blocks.COBBLESTONE);
        });
    }

    private void pylon(int x, int z) {
        int y = groundY(x, z);
        add(x - 6, z - 6, x + 6, z + 6, d -> {
            for (int h = 1; h <= 22; h++) {
                int w = h < 8 ? 2 : 1;
                for (int[] o : new int[][] {{-w, -w}, {w, -w}, {-w, w}, {w, w}}) {
                    if (h < 14 || w == 1) {
                        d.set(x + o[0], y + h, z + o[1], Blocks.IRON_BARS);
                    }
                }
                if (h > 13) {
                    d.set(x, y + h, z, Blocks.IRON_BLOCK);
                }
            }
            for (int h : new int[] {15, 19}) {
                d.box(x - 5, y + h, z, x + 5, y + h, z, Blocks.IRON_BARS);
                d.set(x - 5, y + h - 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
                d.set(x + 5, y + h - 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
            }
        });
    }

    // ------------------------------------------------------------------ деревья

    /** kind: 0 дуб, 1 берёза, 2 ель, 3 тополь. */
    public static void tree(Canvas c, int x, int y, int z, int kind, Random r) {
        Block log = kind == 1 ? Blocks.BIRCH_LOG : kind == 2 ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG;
        BlockState leaf = (kind == 1 ? Blocks.BIRCH_LEAVES : kind == 2 ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        int h = switch (kind) {
            case 2 -> 8 + r.nextInt(4);
            case 3 -> 9 + r.nextInt(4);
            case 1 -> 6 + r.nextInt(3);
            default -> 5 + r.nextInt(3);
        };
        for (int dy = 1; dy <= h + 1; dy++) {
            double rad = switch (kind) {
                case 2 -> dy < 3 ? 0 : (h + 1.5 - dy) * 0.36 * (dy % 2 == 0 ? 1 : 0.65) + 0.5;
                case 3 -> dy < 2 ? 0 : 0.6 + 1.2 * Math.sin(Math.PI * dy / (h + 2.0));
                case 1 -> dy < h - 3 ? 0 : 1.9 - Math.abs(dy - (h - 1)) * 0.5;
                default -> dy < h - 2 ? 0 : 2.6 - Math.abs(dy - (h - 0.5)) * 0.7;
            };
            int n = Mth.ceil(rad);
            for (int dx = -n; dx <= n && rad > 0; dx++) {
                for (int dz = -n; dz <= n; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= rad + r.nextDouble() * 0.5) {
                        c.set(x + dx, y + dy, z + dz, leaf);
                    }
                }
            }
        }
        for (int dy = 0; dy < h; dy++) {
            c.set(x, y + dy, z, log.defaultBlockState());
        }
    }
}
