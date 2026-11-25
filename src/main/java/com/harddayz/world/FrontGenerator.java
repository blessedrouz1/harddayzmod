package com.harddayz.world;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.harddayz.HardDayz;
import com.harddayz.registry.ModBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraftforge.registries.DeferredRegister;

/** Генератор «HardDayz: фронт»: поля, лесополосы, река, дороги, город, сёла, базы. Всё считается из сида мира. */
public class FrontGenerator extends ChunkGenerator {
    public static final Codec<FrontGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, FrontGenerator::new));
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> REGISTER = DeferredRegister.create(Registries.CHUNK_GENERATOR, HardDayz.MODID);

    static {
        REGISTER.register("front", () -> CODEC);
    }

    private static final int FLOOR = 16;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private volatile long seed;
    private volatile FrontMap map;
    private volatile FrontBuildings buildings;

    public FrontGenerator(BiomeSource source) {
        super(source);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> sets, RandomState rs, long worldSeed) {
        seed(worldSeed);
        return super.createState(sets, rs, worldSeed);
    }

    /** Сид мира приходит при создании уровня, до первого чанка. */
    public synchronized void seed(long s) {
        seed = s;
        map = null;
        buildings = null;
    }

    public long seed() {
        return seed;
    }

    public FrontMap map() {
        if (map == null) {
            synchronized (this) {
                if (map == null) {
                    FrontMap m = new FrontMap(seed);
                    buildings = new FrontBuildings(m);
                    map = m;
                }
            }
        }
        return map;
    }

    public FrontBuildings buildings() {
        map();
        return buildings;
    }

    // ------------------------------------------------------------------ чанк

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState rs, StructureManager sm, ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            int cx = chunk.getPos().x, cz = chunk.getPos().z;
            int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            build((x, y, z, s) -> {
                if (x >> 4 == cx && z >> 4 == cz && y >= chunk.getMinBuildHeight() && y < chunk.getMaxBuildHeight()) {
                    p.set(x - bx, y, z - bz);
                    chunk.setBlockState(p, s, false);
                    if (s.is(Blocks.IRON_BARS) || s.getBlock() instanceof net.minecraft.world.level.block.FenceBlock || s.is(ModBlocks.SANDBAG_WALL.get()) || s.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                            || s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
                        chunk.markPosForPostprocessing(p.immutable()); // соединения с соседями
                    }
                }
            }, cx, cz);
            Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
            return chunk;
        }, executor);
    }

    /** Нарисовать чанк на холсте. */
    public void build(FrontBuildings.Canvas c, int cx, int cz) {
        FrontMap m = map();
        FrontBuildings b = buildings();
        int bx = cx << 4, bz = cz << 4;
        for (int x = bx; x < bx + 16; x++) {
            for (int z = bz; z < bz + 16; z++) {
                column(c, m, b, x, z);
            }
        }
        // деревья: ячейки 5x5 с запасом на крону
        for (int gx = Mth.floorDiv(bx - 4, 5); gx <= Mth.floorDiv(bx + 19, 5); gx++) {
            for (int gz = Mth.floorDiv(bz - 4, 5); gz <= Mth.floorDiv(bz + 19, 5); gz++) {
                plant(c, m, b, gx, gz);
            }
        }
        b.paint(c, cx, cz);
    }

    private static int top(double h) {
        return (int) Math.round(h);
    }

    private void column(FrontBuildings.Canvas c, FrontMap m, FrontBuildings b, int x, int z) {
        FrontMap.RoadHit road = m.road(x + 0.5, z + 0.5);
        double river = m.river(x + 0.5, z + 0.5);
        boolean inCity = m.inCity(x + 0.5, z + 0.5);
        int h = top(m.height(x + 0.5, z + 0.5, road));
        FrontMap.Site site = m.siteAt(x, z, 4);
        FrontMap.RoadHit rail = m.rail(x + 0.5, z + 0.5);
        boolean wild = site == null && road.d > 9 && rail.d > 8 && river > FrontMap.RIVER_HALF + 4;

        // воронки
        boolean crater = false;
        if (wild && Math.abs(x) < 1000) {
            int dent = 0;
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    int gx = Mth.floorDiv(x, 36) + i, gz = Mth.floorDiv(z, 36) + j;
                    if (m.hash(gx, gz, 40) < 0.28) {
                        double px = (gx + m.hash(gx, gz, 41)) * 36, pz = (gz + m.hash(gx, gz, 42)) * 36;
                        double r = 1.8 + m.hash(gx, gz, 43) * m.hash(gx, gz, 44) * 4.5, d = Math.hypot(x - px, z - pz);
                        if (d < r) {
                            dent = Math.max(dent, (int) Math.round(r * 0.55 * (1 - d * d / (r * r))));
                            crater = true;
                        }
                    }
                }
            }
            h -= dent;
        }
        // окопы
        double trench = wild && river > 40 ? m.trench(x + 0.5, z + 0.5) : 1.0E9;
        int surface = h;
        if (trench < 1.0) {
            h -= 2;
        }

        FrontMap.Field f = m.field(x, z);
        int kind = field(m, f, x, z);
        boolean hedge = hedge(m, f);
        boolean farm = wild && !crater && trench > 4 && river > FrontMap.RIVER_HALF + 34;
        BlockState top = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState plant = null, plant2 = null;
        double rnd = m.hash(x, z, 50);
        if (h < FrontMap.WATER) {
            top = (river < 8 ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState();
        } else if (h == FrontMap.WATER && river < FrontMap.RIVER_HALF + 8) {
            top = (rnd < 0.6 ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState();
        } else if (trench < 1.0) {
            top = (rnd < 0.5 ? Blocks.OAK_PLANKS : Blocks.COARSE_DIRT).defaultBlockState();
        } else if (crater) {
            top = (rnd < 0.7 ? Blocks.COARSE_DIRT : Blocks.GRAVEL).defaultBlockState();
        } else if (farm && f.edge > 4) {
            switch (kind) {
                case 0 -> {
                    top = Blocks.FARMLAND.defaultBlockState();
                    plant = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, rnd < 0.2 ? 6 : 7);
                }
                case 1 -> top = (((int) (x * Math.cos(f.cx) + z * Math.sin(f.cx)) & 3) == 0 ? Blocks.DIRT : Blocks.COARSE_DIRT).defaultBlockState();
                case 2 -> {
                    if (rnd < 0.45) {
                        plant = Blocks.SUNFLOWER.defaultBlockState();
                        plant2 = Blocks.SUNFLOWER.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
                    }
                }
                case 3 -> {
                    if (Mth.floorDiv(x, 14) * 31 + Mth.floorDiv(z, 14) * 17 % 5 != 0 && (x % 14 + 14) % 14 < 2 && (z % 14 + 14) % 14 < 2
                            && m.hash(Mth.floorDiv(x, 14), Mth.floorDiv(z, 14), 51) < 0.6) {
                        plant = Blocks.HAY_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.X);
                        plant2 = plant;
                    }
                }
                default -> plant = grass(rnd);
            }
        } else if (farm && !hedge && f.edge < 1.6) {
            top = (rnd < 0.5 ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT).defaultBlockState(); // просёлок по меже
        } else if (!inCity && road.d > 6) {
            plant = grass(rnd);
        }

        // дорога и улицы
        BlockState deck = null;
        int deckY = h;
        boolean rail2 = false, pillar = false;
        if (inCity) {
            FrontMap.StreetHit st = m.street(x + 0.5, z + 0.5);
            boolean overRiver = river < FrontMap.RIVER_HALF + 1.5;
            plant = null;
            plant2 = null;
            if (overRiver && st.h <= 5.5 && m.bridge(st.hi)) {
                deckY = m.city.y();
                deck = st.h < 0.5 && Math.floorMod(x, 6) < 3 ? Blocks.WHITE_CONCRETE.defaultBlockState() : st.h <= 3.6 ? asphalt(rnd) : Blocks.SMOOTH_STONE.defaultBlockState();
                rail2 = st.h > 4.6;
                pillar = st.h <= 4 && Math.floorMod(x, 12) == 6;
            } else if (!overRiver) {
                double l = Math.min(st.v, st.h);
                if (l <= 5.5) {
                    boolean line = l < 0.55 && Math.max(st.v, st.h) > 6 && Math.floorMod(st.v < st.h ? z : x, 6) < 3;
                    top = l > 3.6 ? Blocks.SMOOTH_STONE.defaultBlockState() : line ? Blocks.WHITE_CONCRETE.defaultBlockState() : asphalt(rnd);
                } else if (river < FrontMap.RIVER_HALF + 10) {
                    top = (rnd < 0.15 ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS).defaultBlockState(); // набережная
                } else {
                    top = (rnd < 0.15 ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).defaultBlockState();
                    plant = rnd > 0.9 ? Blocks.GRASS.defaultBlockState() : null;
                }
            }
        } else if (road.d < 5.2) {
            int ry = top(road.y);
            boolean dash = road.d < 0.6 && ((int) road.along) % 8 < 4;
            BlockState mat = road.d > 3.6 ? Blocks.GRAVEL.defaultBlockState() : dash ? Blocks.WHITE_CONCRETE.defaultBlockState() : asphalt(rnd);
            if (ry - h > 3 || river < FrontMap.RIVER_HALF + 2) { // мост
                deckY = ry;
                deck = road.d > 3.6 ? Blocks.SMOOTH_STONE.defaultBlockState() : mat;
                rail2 = road.d > 4.2;
                pillar = road.d < 3 && ((int) road.along) % 16 < 2;
            } else {
                h = Math.max(h, ry);
                surface = h;
                top = mat;
                plant = null;
                plant2 = null;
            }
            if (crater) {
                crater = false;
            }
        }

        // железная дорога: насыпь, шпалы, рельсы; через реку — мост
        BlockState track = null;
        if (deck == null && rail.d < 3.2 && road.d >= 5.2 && !inCity) {
            int ry = top(rail.y);
            boolean sleeper = Math.floorMod(x, 2) == 0;
            BlockState mat = (rail.d < 1.6 && sleeper ? Blocks.DARK_OAK_PLANKS : Blocks.GRAVEL).defaultBlockState();
            if (ry - h > 3 || river < FrontMap.RIVER_HALF + 2) {
                deckY = ry;
                deck = mat;
                rail2 = rail.d > 2.2;
                pillar = rail.d < 2 && Math.floorMod(x, 14) < 2;
            } else {
                h = ry;
                surface = h;
                top = mat;
            }
            plant = null;
            plant2 = null;
            double ahead = m.railZ(x + 0.5);
            if (z == Mth.floor(ahead)) {
                track = Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                        net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST);
            }
        }

        // столб
        for (int y = FLOOR; y <= h; y++) {
            BlockState s = y == FLOOR ? Blocks.BEDROCK.defaultBlockState() : y == h ? top : y > h - 4 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
            c.set(x, y, z, s);
        }
        boolean wall = inCity && river >= FrontMap.RIVER_HALF - 1 && river < FrontMap.RIVER_HALF + 3;
        if (wall) { // каменный берег
            for (int y = 57; y <= h; y++) {
                c.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int y = h + 1; y <= FrontMap.WATER; y++) {
            c.set(x, y, z, Blocks.WATER.defaultBlockState());
        }
        if (deck != null) {
            c.set(x, deckY, z, deck);
            c.set(x, deckY - 1, z, Blocks.STONE_BRICKS.defaultBlockState());
            if (track != null) {
                c.set(x, deckY + 1, z, track);
            }
            if (rail2) {
                c.set(x, deckY + 1, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            }
            if (pillar) {
                for (int y = h + 1; y < deckY - 1; y++) {
                    c.set(x, y, z, Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
            return;
        }
        if (track != null) {
            c.set(x, h + 1, z, track);
            return;
        }
        if (wild && !crater && river > 40 && trench > 4 && m.teeth(x, z)) { // противотанковые надолбы
            c.set(x, h + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
            c.set(x, h + 2, z, Blocks.STONE_BRICK_WALL.defaultBlockState());
            return;
        }
        if (trench >= 1.0 && trench < 2.0) {
            c.set(x, surface + 1, z, ModBlocks.SANDBAG_WALL.get().defaultBlockState());
            return;
        }
        if (plant != null && h >= FrontMap.WATER && !b.taken(x, z, 0)) {
            c.set(x, h + 1, z, plant);
            if (plant2 != null) {
                c.set(x, h + 2, z, plant2);
            }
        }
    }

    private static BlockState asphalt(double rnd) {
        return (rnd < 0.05 ? Blocks.ANDESITE : rnd < 0.09 ? Blocks.COBBLESTONE : rnd < 0.14 ? Blocks.GRAVEL : Blocks.GRAY_CONCRETE).defaultBlockState();
    }

    private static BlockState grass(double rnd) {
        return rnd < 0.16 ? Blocks.GRASS.defaultBlockState() : rnd < 0.172 ? Blocks.DANDELION.defaultBlockState()
                : rnd < 0.18 ? Blocks.CORNFLOWER.defaultBlockState() : rnd < 0.188 ? Blocks.POPPY.defaultBlockState() : null;
    }

    /** 0 пшеница, 1 пашня, 2 подсолнух, 3 луг со стогами, 4 луг, 5 лес. */
    public static int field(FrontMap m, FrontMap.Field f, int x, int z) {
        double t = m.hash(f.cx, f.cz, 60);
        if (m.noise(f.cx * 115, f.cz * 115, 420, 61) > 0.62) {
            return t < 0.75 ? 5 : 4; // большие леса
        }
        return t < 0.24 ? 0 : t < 0.38 ? 1 : t < 0.46 ? 2 : t < 0.62 ? 3 : t < 0.9 ? 4 : 5;
    }

    public static boolean hedge(FrontMap m, FrontMap.Field f) {
        return m.hash(f.cx + f.nx, f.cz + f.nz, 62) < 0.55;
    }

    private void plant(FrontBuildings.Canvas c, FrontMap m, FrontBuildings b, int gx, int gz) {
        int x = gx * 5 + (int) (m.hash(gx, gz, 70) * 4), z = gz * 5 + (int) (m.hash(gx, gz, 71) * 4);
        FrontMap.RoadHit road = m.road(x, z);
        double river = m.river(x, z);
        if (road.d < 9 || m.rail(x, z).d < 7 || river < FrontMap.RIVER_HALF + 3 || b.taken(x, z, 4)) {
            return;
        }
        FrontMap.Site site = m.siteAt(x, z, 6);
        double roll = m.hash(gx, gz, 72);
        FrontMap.Field f = m.field(x, z);
        int kind = field(m, f, x, z);
        int tree = -1;
        if (site != null) {
            if (site.type() == FrontMap.Type.VILLAGE && roll < 0.06) {
                tree = roll < 0.03 ? 0 : 1;
            }
        } else if (m.trench(x, z) < 7) {
            return;
        } else if (river < FrontMap.RIVER_HALF + 22) {
            tree = roll < 0.22 ? (roll < 0.1 ? 1 : 0) : -1; // приречные
        } else if (kind == 5 && f.edge > 2) {
            double sp = m.noise(x, z, 150, 73);
            tree = roll < 0.62 ? (sp > 0.6 ? 2 : sp > 0.4 ? 0 : 1) : -1;
        } else if (hedge(m, f) && f.edge < 4.5) {
            tree = roll < 0.8 ? (m.hash(f.cx + f.nx, f.cz + f.nz, 74) < 0.5 ? 3 : roll < 0.4 ? 0 : 1) : -1; // лесополоса
        } else if (kind >= 3 && roll < 0.012) {
            tree = 0;
        }
        boolean cover = site == null && (kind == 5 && f.edge > 2 || hedge(m, f) && f.edge < 6 || river < FrontMap.RIVER_HALF + 22);
        if (tree < 0 && !(cover && roll > 0.82 || kind == 4 && roll > 0.985)) {
            return;
        }
        int h = top(m.height(x + 0.5, z + 0.5, road));
        if (h <= FrontMap.WATER) {
            return;
        }
        if (tree < 0) { // подлесок: куст, бревно, валун
            if (roll > 0.975) {
                boolean ns = m.hash(gx, gz, 75) < 0.5;
                for (int i = 0; i < 4; i++) {
                    c.set(ns ? x : x + i, h + 1, ns ? z + i : z, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                            ns ? net.minecraft.core.Direction.Axis.Z : net.minecraft.core.Direction.Axis.X));
                }
            } else if (roll > 0.955) {
                c.set(x, h + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                c.set(x + 1, h + 1, z, Blocks.COBBLESTONE.defaultBlockState());
                c.set(x, h + 1, z + 1, Blocks.COBBLESTONE.defaultBlockState());
                c.set(x, h + 2, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            } else {
                BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
                c.set(x, h + 1, z, leaf);
                c.set(x + 1, h + 1, z, leaf);
                c.set(x, h + 1, z + 1, leaf);
                if (roll > 0.9) {
                    c.set(x, h + 2, z, leaf);
                }
            }
            return;
        }
        FrontBuildings.tree(c, x, h + 1, z, tree, new Random(m.seed ^ gx * 91815541L ^ gz * 6542989L));
    }

    // ------------------------------------------------------------------ остальное ванильному движку не нужно

    @Override
    public void applyCarvers(WorldGenRegion region, long s, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager sm, RandomState rs, ChunkAccess chunk) {
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager sm) {
    }

    @Override
    public void createStructures(net.minecraft.core.RegistryAccess ra, ChunkGeneratorStructureState state, StructureManager sm, ChunkAccess chunk,
            net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager tm) {
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager sm, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return FrontMap.WATER;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState rs) {
        FrontMap m = map();
        return Math.max(top(m.height(x + 0.5, z + 0.5, m.road(x, z))), FrontMap.WATER) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState rs) {
        BlockState[] col = new BlockState[Math.max(1, getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE, level, rs) - level.getMinBuildHeight())];
        java.util.Arrays.fill(col, Blocks.STONE.defaultBlockState());
        for (int i = 0; i < Math.min(col.length, FLOOR - level.getMinBuildHeight()); i++) {
            col[i] = AIR;
        }
        return new NoiseColumn(level.getMinBuildHeight(), col);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState rs, BlockPos pos) {
        FrontMap.Site s = map().siteAt(pos.getX(), pos.getZ(), 0);
        lines.add("HardDayz: " + (s == null ? "поле" : s.name()));
    }
}
