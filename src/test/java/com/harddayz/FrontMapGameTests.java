package com.harddayz;

import java.awt.image.BufferedImage;
import java.io.File;

import com.harddayz.world.FrontBuildings;
import com.harddayz.world.FrontGenerator;
import com.harddayz.world.FrontMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HardDayz.MODID)
@PrefixGameTestTemplate(false)
public class FrontMapGameTests {
    private static FrontGenerator generator(GameTestHelper h, long seed) {
        var biome = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        FrontGenerator g = new FrontGenerator(new FixedBiomeSource(biome));
        g.seed(seed);
        return g;
    }

    /** На любом сиде есть обе базы, все точки, и чанки собираются без ошибок. */
    @GameTest(template = "empty", timeoutTicks = 2000)
    public static void frontMapPlansEverySeed(GameTestHelper h) throws Exception {
        for (long seed = 1; seed <= 40; seed++) {
            FrontMap m = new FrontMap(seed * 7919);
            for (FrontMap.Type t : FrontMap.Type.values()) {
                long want = t == FrontMap.Type.CITY || t == FrontMap.Type.AIRFIELD || t == FrontMap.Type.POWER ? 1 : t == FrontMap.Type.VILLAGE ? 4 : 2;
                long n = m.sites.stream().filter(s -> s.type() == t).count();
                h.assertTrue(n >= want, "seed " + seed + ": " + t + " " + n);
            }
            for (FrontMap.Site s : m.sites) {
                for (FrontMap.Site o : m.sites) {
                    h.assertTrue(s == o || Math.hypot(s.x() - o.x(), s.z() - o.z()) > s.radius() + o.radius(), "seed " + seed + " overlap " + s + o);
                }
            }
        }
        for (long seed : new long[] {5, 99, 31337, 424242}) {
            FrontBuildings b = new FrontBuildings(new FrontMap(seed));
            h.assertTrue(b.objects.size() >= 4 && b.plaza[1] > 0 && b.hall[1] > 0, "city landmarks on seed " + seed + ": " + b.objects.size());
        }
        FrontGenerator g = generator(h, 12345);
        FrontMap m = g.map();
        int[] hq = g.buildings().hq(m.west);
        boolean[] solid = new boolean[1];
        g.build((x, y, z, s) -> {
            if (x == hq[0] && y == hq[1] && z == hq[2]) {
                solid[0] = !s.isAir();
            }
        }, hq[0] >> 4, hq[2] >> 4);
        h.assertTrue(!solid[0], "HQ place must be free");
        String dump = System.getProperty("harddayz.mapdump", System.getenv("HARDDAYZ_MAPDUMP"));
        if (dump != null) {
            long[] seeds = {12345, 777};
            for (long seed : seeds) {
                overview(generator(h, seed), new File(dump, "map_" + seed + ".png"), 4);
            }
            detail(g, 0, -20, 300, new File(dump, "city.png"));
            detail(g, m.west.x(), m.west.z(), 90, new File(dump, "base.png"));
            for (FrontMap.Site s : m.sites) {
                if (s.type() != FrontMap.Type.BASE && s.type() != FrontMap.Type.CITY) {
                    detail(g, s.x(), s.z(), s.radius() + 20, new File(dump, s.type() + "_" + (s.x() < 0 ? "w" : "e") + ".png"));
                }
            }
            detail(g, -450, 200, 120, new File(dump, "trench.png"));
        }
        h.succeed();
    }

    private static int color(BlockState s) {
        return s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
    }

    private static void render(FrontGenerator g, int x0, int z0, int x1, int z1, int step, int[][] col, int[][] top) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                int fcx = cx, fcz = cz;
                java.util.HashMap<Long, java.util.TreeMap<Integer, BlockState>> cols = new java.util.HashMap<>();
                FrontBuildings.Canvas c = (x, y, z, s) -> {
                    if (x >> 4 == fcx && z >> 4 == fcz && y > 50) {
                        cols.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> new java.util.TreeMap<>()).put(y, s);
                    }
                };
                g.build(c, cx, cz);
                for (var e : cols.entrySet()) {
                    int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
                    if (x < x0 || x >= x1 || z < z0 || z >= z1) {
                        continue;
                    }
                    for (var b : e.getValue().descendingMap().entrySet()) {
                        int k = b.getValue().isAir() ? 0 : color(b.getValue());
                        if (k != 0) {
                            col[(x - x0) / step][(z - z0) / step] = k;
                            top[(x - x0) / step][(z - z0) / step] = b.getKey();
                            break;
                        }
                    }
                }
            }
        }
    }

    private static void save(int[][] col, int[][] top, int scale, File f) throws Exception {
        int w = col.length, d = col[0].length;
        BufferedImage img = new BufferedImage(w * scale, d * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                int dh = top[x][z] - top[Math.max(0, x - 1)][Math.max(0, z - 1)];
                double k = Math.max(0.5, Math.min(1.25, 0.95 + dh * 0.05));
                int c = col[x][z];
                int rgb = ((int) Math.min(255, (c >> 16 & 255) * k) << 16) | ((int) Math.min(255, (c >> 8 & 255) * k) << 8) | (int) Math.min(255, (c & 255) * k);
                for (int i = 0; i < scale * scale; i++) {
                    img.setRGB(x * scale + i % scale, z * scale + i / scale, rgb);
                }
            }
        }
        f.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(img, "png", f);
    }

    private static void overview(FrontGenerator g, File f, int step) throws Exception {
        int n = FrontMap.HALF * 2 / step;
        int[][] col = new int[n][n], top = new int[n][n];
        render(g, -FrontMap.HALF, -FrontMap.HALF, FrontMap.HALF, FrontMap.HALF, step, col, top);
        save(col, top, 1, f);
    }

    private static void detail(FrontGenerator g, int x, int z, int r, File f) throws Exception {
        int[][] col = new int[r * 2][r * 2], top = new int[r * 2][r * 2];
        render(g, x - r, z - r, x + r, z + r, 1, col, top);
        save(col, top, Math.max(1, 900 / (r * 2)), f);
    }
}
