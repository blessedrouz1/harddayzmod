package com.harddayz.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.harddayz.HardDayz;
import com.harddayz.network.Net;
import com.harddayz.registry.ModParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Взрывы на клиенте. Порядок как в жизни: сразу свет и огненный шар, дым, по земле и воздуху со скоростью звука
 * (17 блоков за тик) бежит ударная волна (BlastRender), и вместе с ней до камеры доходят звук и толчок.
 */
public final class ClientBlasts {
    static final double SOUND_SPEED = 17.0;
    static final List<Active> ACTIVE = new ArrayList<>();
    private static final RandomSource R = RandomSource.create();

    private static float flash;
    private static float glow;
    private static float shake;
    private static int shakeTicks;
    private static int shakeDuration;

    private static ClientLevel world;
    /** Сколько разрывов только что прозвучало: в кассетном залпе второстепенные пропускаем. */
    private static int soundsNow;

    private ClientBlasts() {
    }

    static final class Active {
        final Vec3 pos;
        final float power;
        /** Тип звука (Net.BlastFx.sound). */
        int sound;
        final double dist;
        /** Подрыв в воздухе (решает сервер). */
        boolean air;
        /** Цвет пыли от грунта под взрывом. */
        float dustR = 0.55f;
        float dustG = 0.47f;
        float dustB = 0.36f;
        final double ground;
        final BlockState groundBlock;
        int age;
        boolean heard;
        /** Рядом уже идёт свежий взрыв: рисуем только вспышку и огонь, без второго облака, волны и столба. */
        boolean minor;
        /** Множитель количества частиц (когда взрывов много — меньше). */
        float density = 1f;
        /** Свои частицы включены в настройках. */
        boolean fx = com.harddayz.ClientConfig.particles();
        /** Горящие обломки: x, y, z, vx, vy, vz. Летят по дуге и тянут дымный хвост. */
        final List<double[]> debris = new ArrayList<>();

        Active(Vec3 pos, float power, double dist, double ground, BlockState groundBlock) {
            this.pos = pos;
            this.power = power;
            this.dist = dist;
            this.ground = ground;
            this.groundBlock = groundBlock;
        }

        int life() {
            return (int) (70 + power * 10);
        }
    }

    public static void add(Net.BlastFx fx) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }
        Vec3 pos = new Vec3(fx.x(), fx.y(), fx.z());
        Vec3 cam = RangeSound.ears();
        double dist = cam.distanceTo(pos);
        boolean near0 = loaded(level, Mth.floor(pos.x), Mth.floor(pos.z));
        if (!near0 && fx.power() < 6 && dist > 140 + fx.power() * 70) {
            return; // не видно и не слышно
        }
        double ground = near0 ? floor(level, pos) : pos.y;
        BlockState below = level.getBlockState(BlockPos.containing(pos.x, ground - 1, pos.z));
        if (near0 && below.isAir()) { // воронка уже выбита — берём грунт глубже
            for (int i = 2; i < 8 && below.isAir(); i++) {
                below = level.getBlockState(BlockPos.containing(pos.x, ground - i, pos.z));
            }
        }
        Active a = new Active(pos, fx.power(), dist, ground, below);
        a.air = (fx.sound() & 16) != 0;
        int col = below.getMapColor(level, BlockPos.containing(pos.x, ground - 1, pos.z)).col;
        if (col != 0) {
            a.dustR = Mth.lerp(0.45f, a.dustR, (col >> 16 & 255) / 255f);
            a.dustG = Mth.lerp(0.45f, a.dustG, (col >> 8 & 255) / 255f);
            a.dustB = Mth.lerp(0.45f, a.dustB, (col & 255) / 255f);
        }
        a.fx &= near0; // дальше загруженных чанков частиц не видно, остаются зарево и звук
        a.sound = fx.sound();
        int near = 0;
        for (Active o : ACTIVE) {
            double r = 3 + Math.max(o.power, fx.power()) * 1.2;
            if (o.age < 25 && o.power >= fx.power() * 0.6f && o.pos.distanceToSqr(pos) < r * r) {
                near++;
            }
        }
        a.minor = near > 0;
        a.density = (float) Mth.clamp(1.0 / (1 + near * 0.6 + ACTIVE.size() * 0.08), 0.15, 1.0);
        ACTIVE.add(a);
        if (com.harddayz.ClientConfig.flash()) {
            // слепит того, кто смотрит на взрыв и не за стеной
            Vec3 to = pos.subtract(cam);
            double look = dist < 2 ? 1 : to.scale(1 / dist).dot(new Vec3(mc.gameRenderer.getMainCamera().getLookVector()));
            boolean open = dist > 250 || level.clip(new net.minecraft.world.level.ClipContext(cam, pos.add(0, 1.2, 0),
                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player))
                    .getLocation().distanceToSqr(pos) < 9;
            double see = (open ? 1 : 0.12) * (0.2 + 0.8 * Mth.clamp((look + 0.1) / 0.8, 0, 1));
            flash = Math.max(flash, (float) (Mth.clamp(fx.power() * 0.09 / (1 + dist / 40), 0, 0.9) * see));
            // зарево: ночью видно издалека, днём солнце ярче
            double night = 1 - Mth.clamp((level.getSkyDarken(1f) - 0.2) / 0.8, 0, 1);
            boolean sky = level.canSeeSky(BlockPos.containing(cam));
            glow = Math.max(glow, (float) (Mth.clamp(fx.power() * 0.06 / (1 + dist / 250), 0, 0.85) * (0.2 + 0.8 * night) * (sky ? 1 : 0.15)));
        }
        if (near0 && !a.minor && dist < 220) {
            BlastLights.add(level, pos.x, pos.y, pos.z, ground, fx.power());
        }
        if (!a.fx) {
            return; // частицы рисует другой мод
        }
        fireball(level, a);
        if (!a.minor && fx.power() >= 2) {
            float p = fx.power();
            int n = (int) (Math.min(16, 3 + p * 1.1) * a.density);
            for (int i = 0; i < n; i++) {
                double out = 0.25 + R.nextDouble() * (0.25 + p * 0.025);
                double ang = R.nextDouble() * Math.PI * 2;
                a.debris.add(new double[] {pos.x, Math.max(pos.y, ground) + 0.5, pos.z, Math.cos(ang) * out, 0.55 + R.nextDouble() * (0.5 + p * 0.03),
                        Math.sin(ang) * out});
            }
        }
    }

    // ------------------------------------------------------------------ мгновенно: огненный шар, искры, обломки

    /** Чанк есть на клиенте (level.hasChunk на клиенте всегда отвечает «да»). */
    static boolean loaded(ClientLevel level, int x, int z) {
        return level.getChunkSource().hasChunk(x >> 4, z >> 4);
    }

    /** Пол под взрывом: ближайшая твёрдая поверхность (крыша, перекрытие), иначе земля по карте высот. */
    private static double floor(ClientLevel level, Vec3 pos) {
        BlockPos.MutableBlockPos p = BlockPos.containing(pos).mutable();
        for (int i = 0; i < 6; i++) {
            if (level.getBlockState(p).blocksMotion()) {
                return Math.min(pos.y, p.getY() + 1);
            }
            p.move(0, -1, 0);
        }
        return Math.min(pos.y, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z)));
    }

    /** Размер клубов от мощности: граната — маленькие, баллистика — огромные. */
    private static float scale(float p) {
        return Mth.clamp(0.5f * Mth.sqrt(p), 0.35f, 2.2f);
    }

    private static void fireball(ClientLevel level, Active a) {
        float p = a.power;
        float s0 = scale(p);
        Vec3 c = a.pos;
        double cy = a.air ? c.y : Math.max(c.y, a.ground) + 0.4;
        int n = (int) ((18 + p * 7) * a.density);
        for (int i = 0; i < n; i++) {
            Vec3 d = randomDir();
            double rr = p * 0.12 * R.nextDouble();
            double v = (0.06 + p * 0.1 * Math.cbrt(R.nextDouble())) * 0.9; // с трением 0.86 пролетает ~7 скоростей
            double dy = a.air ? d.y : Math.abs(d.y) * 0.8;
            puff(level, ModParticles.FIRE.get(), c.x + d.x * rr, cy + dy * rr, c.z + d.z * rr, d.x * v, dy * v + 0.02, d.z * v,
                    s0 * (0.8f + R.nextFloat() * 0.5f), 0.6f + p * 0.05f);
        }
        // выброс: острые струи земли и дыма веером, как корона
        if (!a.air && !a.minor && p >= 1) {
            int rays = (int) ((6 + p * 0.7) * a.density);
            for (int i = 0; i < rays; i++) {
                double ang = (i + R.nextDouble()) / rays * Math.PI * 2;
                double up = 0.7 + R.nextDouble() * 1.1;
                Vec3 d = new Vec3(Math.cos(ang), up, Math.sin(ang)).normalize();
                double v = (0.35 + p * 0.09) * (0.6 + R.nextDouble() * 0.6);
                for (int k = 1; k <= 7; k++) {
                    double q = v * k / 7.0;
                    boolean dark = k % 3 == 0;
                    var part = puff(level, dark ? ModParticles.SMOKE.get() : ModParticles.DUST.get(), c.x + d.x * 0.5, a.ground + 0.3, c.z + d.z * 0.5,
                            d.x * q, d.y * q, d.z * q, s0 * (0.75f - k * 0.06f), 0.6f);
                    if (!dark) {
                        tint(part, a, 0.75f);
                    }
                }
            }
        }
        // искры
        for (int i = 0; i < p * 6 * a.density; i++) {
            Vec3 d = randomDir();
            put(level, ParticleTypes.LAVA, c.x, cy, c.z, d.x * 0.5, Math.abs(d.y) * 0.6, d.z * 0.5);
        }
        // обломки грунта
        if (!a.air && !a.groundBlock.isAir()) {
            ParticleOptions dirt = new BlockParticleOption(ParticleTypes.BLOCK, a.groundBlock);
            for (int i = 0; i < p * 18 * a.density; i++) {
                double ang = R.nextDouble() * Math.PI * 2;
                double out = 0.2 + R.nextDouble() * 0.5;
                put(level, dirt, c.x, a.ground + 0.3, c.z, Math.cos(ang) * out, 0.6 + R.nextDouble() * (0.5 + p * 0.05), Math.sin(ang) * out);
            }
        }
    }

    // ------------------------------------------------------------------ по тикам

    static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientGunner.tickTracers();
        if (flash > 0) {
            flash = Math.max(0, flash - 0.08f);
        }
        if (glow > 0) {
            glow = Math.max(0, glow - 0.025f);
        }
        if (shakeTicks > 0) {
            shakeTicks--;
        }
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            if (mc.level == null) {
                ACTIVE.clear();
                world = null;
                BlastLights.tick(null);
                ClientAccess.tickLaunches(false);
            }
            return;
        }
        if (world != mc.level) {
            world = mc.level;
            ACTIVE.clear(); // другое измерение
            ClientAccess.tickLaunches(false);
        }
        ClientAccess.tickLaunches(true);
        BlastLights.tick(mc.level);
        soundsNow = Math.max(0, soundsNow - 1);
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof com.harddayz.entity.Missile m && m.kind() == com.harddayz.entity.Missile.Kind.BALLISTIC
                    && m.getDeltaMovement().y < -1) {
                double d = m.distanceTo(mc.player);
                if (com.harddayz.ClientConfig.flash()) {
                    glow = Math.max(glow, (float) Mth.clamp(0.5 / (1 + d / 220), 0, 0.5));
                }
            }
        }
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            tick(mc, mc.level, a);
            if (++a.age > a.life()) {
                it.remove();
            }
        }
    }

    private static boolean reached(Active a) {
        return a.age * SOUND_SPEED >= RangeSound.ears().distanceTo(a.pos);
    }

    private static void tick(Minecraft mc, ClientLevel level, Active a) {
        float p = a.power;
        int t = a.age;
        Vec3 c = a.pos;
        if (!a.fx) {
            if (!a.heard && reached(a)) {
                a.heard = true;
                hear(mc, a);
            }
            return;
        }
        if (a.minor) {
            if (t < 12 && t % 2 == 0) {
                put(level, ModParticles.FIRE.get(), c.x + R.nextGaussian() * p * 0.2, a.ground + 0.3, c.z + R.nextGaussian() * p * 0.2, 0, 0.05, 0);
            }
            if (t == 4) {
                put(level, ModParticles.SMOKE.get(), c.x, a.ground + 1, c.z, 0, 0.08, 0);
            }
            if (!a.heard && reached(a)) {
                a.heard = true;
                hear(mc, a);
            }
            return;
        }
        int type = a.sound & 15;
        boolean air = a.air;
        float s0 = scale(p);
        style(level, a, t, type, air);
        front(level, a, t);
        // горящие обломки с дымным хвостом
        for (var it = a.debris.iterator(); it.hasNext(); ) {
            double[] d = it.next();
            d[4] -= 0.055;
            d[3] *= 0.985;
            d[5] *= 0.985;
            for (int k = 0; k < 2; k++) {
                d[0] += d[3] * 0.5;
                d[1] += d[4] * 0.5;
                d[2] += d[5] * 0.5;
                put(level, ParticleTypes.SMOKE, d[0], d[1], d[2], 0, 0.01, 0);
                if (t < 14) {
                    put(level, ParticleTypes.FLAME, d[0], d[1], d[2], 0, 0, 0);
                }
            }
            if (d[1] < a.ground || t > 60) {
                if (d[1] < a.ground && t < 40) {
                    put(level, ParticleTypes.LARGE_SMOKE, d[0], a.ground + 0.2, d[2], 0, 0.05, 0);
                }
                it.remove();
            }
        }
        // фонтан грунта вверх в первые тики
        if (!air && t < 5 && !a.groundBlock.isAir() && a.pos.y - a.ground < 3) {
            ParticleOptions dirt = new BlockParticleOption(ParticleTypes.BLOCK, a.groundBlock);
            int n = (int) ((8 + p * 4) * a.density);
            for (int i = 0; i < n; i++) {
                double sx = R.nextGaussian() * 0.12, sz = R.nextGaussian() * 0.12;
                double up = 0.9 + R.nextDouble() * (0.6 + p * 0.06);
                put(level, dirt, c.x + R.nextGaussian() * 0.6, a.ground + 0.3, c.z + R.nextGaussian() * 0.6, sx, up, sz);
            }
        }
        // пыльная ударная волна по земле
        int waveTicks = (int) (8 + p * 0.7f);
        if (!air && t < waveTicks) {
            double speed = 0.9 + p * 0.14;
            double r = 1 + waveTicks * speed * (1 - Math.pow(1 - t / (double) waveTicks, 1.8)); // быстро вначале, потом тормозит
            int n = (int) (Math.min(70, 14 + p * 4) * a.density);
            for (int i = 0; i < n; i++) {
                double ang = (i + R.nextDouble()) / n * Math.PI * 2;
                double x = c.x + Math.cos(ang) * r;
                double z = c.z + Math.sin(ang) * r;
                double vx = Math.cos(ang) * speed * 0.3;
                double vz = Math.sin(ang) * speed * 0.3;
                tint(puff(level, ModParticles.DUST.get(), x, a.ground + 0.4 + R.nextDouble() * 0.6, z, vx, 0.03, vz, s0, 1f), a, 1f);
                if (i % 3 == 0) {
                    put(level, ParticleTypes.CLOUD, x, a.ground + 0.3, z, vx * 1.2, 0.02, vz * 1.2);
                }
            }
        }
        // купол конденсата у мощных
        if (p >= 8 && t < 5) {
            double r = 2 + t * p * 0.5;
            for (int i = 0; i < 70; i++) {
                Vec3 d = randomDir();
                if (d.y < -0.1) {
                    continue;
                }
                put(level, ModParticles.SMOKE_WHITE.get(), c.x + d.x * r, c.y + d.y * r * 0.8, c.z + d.z * r, d.x * 0.3, d.y * 0.2, d.z * 0.3);
            }
        }
        // огненный шар остывает в тёмное клубящееся облако
        int cloudEnd = (int) (12 + p * 2.5);
        if (t >= 2 && t < cloudEnd) {
            double k = t / (double) cloudEnd;
            double radius = Math.min(p * 1.1, 1 + t * (0.25 + p * 0.03));
            double cy = (air ? c.y : a.ground + 1) + t * (0.06 + p * 0.012);
            int n = Math.max(1, (int) ((4 + p * 0.7) * (1 - k * 0.5) * a.density));
            for (int i = 0; i < n; i++) {
                Vec3 d = randomDir();
                double rr = radius * Math.cbrt(R.nextDouble());
                double out = 0.03 + p * 0.004;
                puff(level, ModParticles.SMOKE.get(), c.x + d.x * rr, cy + Math.abs(d.y) * rr * 0.7, c.z + d.z * rr, d.x * out, 0.03 + R.nextDouble() * 0.04,
                        d.z * out, s0 * (0.8f + R.nextFloat() * 0.5f), 1f);
                if (k < 0.4 && i % 2 == 0) {
                    puff(level, ModParticles.FIRE.get(), c.x + d.x * rr * 0.5, cy + Math.abs(d.y) * rr * 0.4, c.z + d.z * rr * 0.5, 0, 0.05, 0, s0, 1f);
                }
            }
        }
        // огонь в воронке
        if (!air && t < 25 + p * 3 && t % 2 == 0) {
            for (int i = 0; i < 1 + p / 5; i++) {
                put(level, ModParticles.FIRE.get(), c.x + R.nextGaussian() * p * 0.3, a.ground + 0.3, c.z + R.nextGaussian() * p * 0.3, 0, 0.04, 0);
            }
        }
        // столб дыма от воронки
        int smokeEnd = (int) (50 + p * 9);
        if (!air && t > 6 && t < smokeEnd && t % 2 == 0) {
            double spread = 0.4 + p * 0.15;
            for (int i = 0; i < 1 + p / 5; i++) {
                puff(level, ModParticles.SMOKE.get(), c.x + R.nextGaussian() * spread, a.ground + 0.5 + R.nextDouble(), c.z + R.nextGaussian() * spread,
                        R.nextGaussian() * 0.01, 0.12 + R.nextDouble() * 0.08 + p * 0.004, R.nextGaussian() * 0.01, s0, 1f);
            }
        }
        // «гриб» у мощных: шапка на высоте расползается в стороны
        if (p >= 10 && t > 10 && t < 60 && t % 2 == 0) {
            double h = Math.min(p * 3.2, t * 0.9);
            double cap = Math.min(p * 1.1, 2 + t * 0.14);
            for (int i = 0; i < 4; i++) {
                double ang = R.nextDouble() * Math.PI * 2;
                double rr = cap * Math.sqrt(R.nextDouble());
                put(level, ModParticles.SMOKE_HUGE.get(), c.x + Math.cos(ang) * rr, a.ground + h + R.nextGaussian(), c.z + Math.sin(ang) * rr,
                        Math.cos(ang) * 0.06, 0.01, Math.sin(ang) * 0.06);
            }
        }
        if (!a.heard && reached(a)) {
            a.heard = true;
            hear(mc, a);
        }
    }

    /** Пыль, которую срывает с земли фронт волны: поднимается там, где он только что прошёл. */
    private static void front(ClientLevel level, Active a, int t) {
        double max = Math.min(BlastRender.reach(a.power) * 0.6, 70);
        double r0 = t * SOUND_SPEED, r1 = Math.min(max, r0 + SOUND_SPEED);
        if (a.power < 1.5f || r0 >= max || a.air && a.pos.y - a.ground > r1) {
            return;
        }
        int n = (int) ((20 + a.power * 4) * a.density);
        for (int i = 0; i < n; i++) {
            double ang = R.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(Mth.lerp(R.nextDouble(), r0 * r0, r1 * r1));
            double x = a.pos.x + Math.cos(ang) * r, z = a.pos.z + Math.sin(ang) * r;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            if (!loaded(level, bx, bz)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (Math.abs(y - a.ground) > 24) {
                continue;
            }
            float k = (float) (1 - r / max);
            if (!level.getFluidState(new BlockPos(bx, y - 1, bz)).isEmpty()) {
                put(level, ParticleTypes.SPLASH, x, y + 0.1, z, Math.cos(ang) * 0.2, 0.2, Math.sin(ang) * 0.2);
            } else {
                tint(puff(level, ModParticles.DUST.get(), x, y + 0.15, z, Math.cos(ang) * 0.25 * k, 0.03 + 0.03 * k, Math.sin(ang) * 0.25 * k,
                        0.3f + 0.35f * k, 0.4f), a, 1f);
            }
        }
    }

    /** Граница ближнего звучания: граната — 22 блока, «Град» — 41, баллистика — 100. */
    private static double nearRange(float p) {
        return 20 + p * 5;
    }

    private static void hear(Minecraft mc, Active a) {
        Vec3 ears = RangeSound.ears();
        double d = ears.distanceTo(a.pos);
        double hearRange = Math.min(1900, 140 + a.power * 70); // «Град» ~430 м, баллистика ~1260 м
        if (d > hearRange) {
            return;
        }
        boolean skip = a.minor && soundsNow > 8; // кассета, беглый огонь: отдельные хлопки уже не различить
        if (com.harddayz.ClientConfig.sounds() && !skip) {
            soundsNow += 2;
            int type = a.sound & 15;
            if (type < 1 || type > com.harddayz.registry.ModSounds.BLAST_TYPES.length) {
                // без явного оружия (мина, техника, свой подрыв): по силе — малый, РСЗО или крылатая
                type = a.power < 2.5f ? 1 : a.power < 7f ? 3 : 7;
            }
            float loud = Mth.clamp(0.5f + a.power * 0.12f, 0.35f, 1f) * (a.minor ? 0.6f : 1f);
            float pitch = 0.95f + R.nextFloat() * 0.1f;
            if (muffled(mc, ears, a.pos)) {
                loud *= 0.55f;
                pitch *= 0.85f;
            }
            // слои «близко — средне — далеко» плавно переходят друг в друга, без скачка на границе
            double n = nearRange(a.power), m = n * 3;
            double toMid = Mth.clamp((d - n * 0.75) / (n * 0.5), 0, 1);
            double toFar = Mth.clamp((d - m * 0.8) / (m * 0.4), 0, 1);
            layer(a, type, 0, (1 - toMid) * loud, hearRange, pitch);
            layer(a, type, 1, toMid * (1 - toFar) * loud, hearRange, pitch);
            layer(a, type, 2, toFar * loud, hearRange, pitch);
        }
        double shakeRange = 12 + a.power * 7; // трясёт только вблизи
        float s = d > shakeRange ? 0 : (float) Mth.clamp(a.power * 1.6 / (1 + d / 10) * (1 - d / shakeRange), 0, 7);
        if (s > 0.15f && com.harddayz.ClientConfig.shake()) {
            shake = Math.max(shake * shakeTicks / Math.max(1, shakeDuration), s);
            shakeTicks = shakeDuration = (int) (10 + Math.min(25, a.power * 1.5));
        }
        // волна дошла до камеры: сдувает пыль у ног
        if (a.fx && !a.minor && a.power >= 2 && d < shakeRange) {
            Vec3 out = ears.subtract(a.pos).multiply(1, 0, 1);
            out = out.lengthSqr() < 0.01 ? Vec3.ZERO : out.normalize();
            for (int i = 0; i < 10; i++) {
                double x = ears.x + R.nextGaussian() * 2.5, z = ears.z + R.nextGaussian() * 2.5;
                tint(puff(mc.level, ModParticles.DUST.get(), x - out.x * 2, ears.y - 1.4, z - out.z * 2, out.x * 0.5, 0.03, out.z * 0.5, 0.35f, 0.35f), a, 1f);
            }
        }
    }

    private static void layer(Active a, int type, int tier, double loud, double range, float pitch) {
        if (loud > 0.02) {
            RangeSound.play(com.harddayz.registry.ModSounds.blast(type - 1, a.air, tier), SoundSource.BLOCKS, a.pos, range, (float) loud, pitch, null);
        }
    }

    /** Слушатель в укрытии: над головой крыша и до взрыва нет прямой видимости. */
    private static boolean muffled(Minecraft mc, Vec3 ears, Vec3 at) {
        if (mc.level.canSeeSky(BlockPos.containing(ears))) {
            return false;
        }
        Vec3 to = at.add(0, 1, 0);
        if (ears.distanceTo(to) > 60) {
            to = ears.add(to.subtract(ears).normalize().scale(60));
        }
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(ears, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && hit.getLocation().distanceToSqr(to) > 4;
    }

    /** Короткий толчок камеры (удар машины). */
    public static void bump(float s) {
        if (!com.harddayz.ClientConfig.shake()) {
            return;
        }
        shake = Math.max(shake * shakeTicks / Math.max(1, shakeDuration), s);
        shakeTicks = shakeDuration = 8;
    }

    static void onCamera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0 || Minecraft.getInstance().isPaused()) {
            return;
        }
        float k = shake * shakeTicks / (float) shakeDuration;
        event.setYaw(event.getYaw() + (R.nextFloat() - 0.5f) * k * 2.2f);
        event.setPitch(event.getPitch() + (R.nextFloat() - 0.5f) * k * 2.2f);
        event.setRoll(event.getRoll() + (R.nextFloat() - 0.5f) * k * 2.5f);
    }

    /** Зарево: тянем цвет неба/тумана к оранжевому. */
    static void onFogColor(net.minecraftforge.client.event.ViewportEvent.ComputeFogColor event) {
        if (glow <= 0.01f) {
            return;
        }
        float k = glow;
        event.setRed(Mth.lerp(k, event.getRed(), 1.0f));
        event.setGreen(Mth.lerp(k, event.getGreen(), 0.55f));
        event.setBlue(Mth.lerp(k, event.getBlue(), 0.25f));
    }

    public static void renderFlash(Gfx g, float delta) {
        if (glow > 0.02f) {
            // небо светится: оранжевый градиент сверху вниз
            int h = g.guiHeight();
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                float k = 1 - i / (float) steps;
                int a = (int) (glow * 80 * k * k);
                int y0 = h * i / (steps * 2);
                int y1 = h * (i + 1) / (steps * 2);
                g.fill(0, y0, g.guiWidth(), y1, (a << 24) | 0xFF7A30);
            }
        }
        if (flash <= 0.01f) {
            return;
        }
        int alpha = (int) (flash * 255) << 24;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha | 0xFFF2D8);
    }

    private static Vec3 randomDir() {
        return new Vec3(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).normalize();
    }

    // ------------------------------------------------------------------ свой почерк у каждого боеприпаса

    private static void style(ClientLevel level, Active a, int t, int type, boolean air) {
        float p = a.power;
        Vec3 c = a.pos;
        double g = a.ground;
        float dn = a.density;
        if (air) {
            // подрыв в воздухе: чёрный шар дыма висит, вниз сыплются горящие осколки
            if (t < 4) {
                double r = 0.8 + t * (0.5 + p * 0.06);
                for (int i = 0; i < 30 * dn; i++) {
                    Vec3 d = randomDir();
                    put(level, ModParticles.SMOKE.get(), c.x + d.x * r, c.y + d.y * r, c.z + d.z * r, d.x * 0.05, d.y * 0.05, d.z * 0.05);
                }
            }
            if (t == 0) {
                for (int i = 0; i < (20 + p * 6) * dn; i++) {
                    Vec3 d = randomDir();
                    put(level, ParticleTypes.LAVA, c.x, c.y, c.z, d.x * 0.6, d.y * 0.4, d.z * 0.6);
                    put(level, ParticleTypes.FIREWORK, c.x, c.y, c.z, d.x * 0.7, d.y * 0.7, d.z * 0.7);
                }
            }
            if (t > 4 && t < 40 && t % 3 == 0) {
                put(level, ParticleTypes.FALLING_LAVA, c.x + R.nextGaussian() * p * 0.5, c.y, c.z + R.nextGaussian() * p * 0.5, 0, 0, 0);
            }
            return;
        }
        switch (type) {
            case 2 -> { // мина: высокий узкий столб земли и тёмного дыма
                if (t < 4 && !a.groundBlock.isAir()) {
                    ParticleOptions dirt = new BlockParticleOption(ParticleTypes.BLOCK, a.groundBlock);
                    for (int i = 0; i < 40 * dn; i++) {
                        put(level, dirt, c.x + R.nextGaussian() * 0.3, g + 0.3, c.z + R.nextGaussian() * 0.3,
                                R.nextGaussian() * 0.06, 1.1 + R.nextDouble() * 0.9, R.nextGaussian() * 0.06);
                    }
                }
                if (t < 12) {
                    for (int i = 0; i < 4 * dn; i++) {
                        put(level, ModParticles.SMOKE.get(), c.x + R.nextGaussian() * 0.4, g + 0.5 + t * 0.9 + R.nextDouble() * 2, c.z + R.nextGaussian() * 0.4,
                                R.nextGaussian() * 0.02, 0.25, R.nextGaussian() * 0.02);
                    }
                }
            }
            case 3 -> { // РСЗО: веер осколков-трассеров и рыжая пыль
                if (t == 0) {
                    for (int i = 0; i < (40 + p * 8) * dn; i++) {
                        double ang = R.nextDouble() * Math.PI * 2;
                        double sp = 0.8 + R.nextDouble() * 1.2;
                        put(level, ParticleTypes.CRIT, c.x, g + 0.6, c.z, Math.cos(ang) * sp, 0.1 + R.nextDouble() * 0.4, Math.sin(ang) * sp);
                        if (i % 3 == 0) {
                            put(level, ParticleTypes.LAVA, c.x, g + 0.6, c.z, Math.cos(ang) * sp * 0.4, 0.4, Math.sin(ang) * sp * 0.4);
                        }
                    }
                }
                if (t > 2 && t < 20 && t % 2 == 0) {
                    for (int i = 0; i < 3 * dn; i++) {
                        put(level, ModParticles.DUST.get(), c.x + R.nextGaussian() * p * 0.4, g + 1 + R.nextDouble() * 2, c.z + R.nextGaussian() * p * 0.4,
                                0, 0.05, 0);
                    }
                }
            }
            case 4 -> { // ТОС: огненное облако разрастается, вторая вспышка, плотный чёрный дым
                if (t < 22) {
                    double r = Math.min(p * 1.6, 1.5 + t * (0.35 + p * 0.05));
                    for (int i = 0; i < (18 + p * 2) * dn; i++) {
                        Vec3 d = randomDir();
                        double rr = r * Math.cbrt(R.nextDouble());
                        put(level, ModParticles.FIRE.get(), c.x + d.x * rr, g + 1 + Math.abs(d.y) * rr * 0.8, c.z + d.z * rr, d.x * 0.08, 0.06, d.z * 0.08);
                    }
                }
                if (t == 6) {
                    put(level, ModParticles.FLASH.get(), c.x, g + p * 0.8, c.z, 0, 0, 0);
                    if (com.harddayz.ClientConfig.flash()) {
                        glow = Math.max(glow, (float) Mth.clamp(p * 0.07 / (1 + a.dist / 200), 0, 0.9));
                    }
                }
                if (t > 14 && t < 70 && t % 2 == 0) {
                    for (int i = 0; i < 3 * dn; i++) {
                        put(level, ModParticles.SMOKE_HUGE.get(), c.x + R.nextGaussian() * p * 0.5, g + 2 + R.nextDouble() * p * 0.8,
                                c.z + R.nextGaussian() * p * 0.5, 0, 0.06, 0);
                    }
                }
            }
            case 5 -> { // баллистика: белая ударная волна по земле, вспышки, высокий огненный столб
                if (t < 3) {
                    put(level, ModParticles.FLASH.get(), c.x + R.nextGaussian() * 2, g + 2 + t * 4, c.z + R.nextGaussian() * 2, 0, 0, 0);
                }
                if (t < 14) {
                    double r = 2 + t * (2.4 + p * 0.06);
                    int n = (int) (110 * dn);
                    for (int i = 0; i < n; i++) {
                        double ang = (i + R.nextDouble()) / n * Math.PI * 2;
                        put(level, ModParticles.SMOKE_WHITE.get(), c.x + Math.cos(ang) * r, g + 0.6 + R.nextDouble() * 1.5, c.z + Math.sin(ang) * r,
                                Math.cos(ang) * 0.5, 0.02, Math.sin(ang) * 0.5);
                    }
                }
                if (t > 3 && t < 40) {
                    for (int i = 0; i < 4 * dn; i++) {
                        put(level, ModParticles.FIRE.get(), c.x + R.nextGaussian() * 1.2, g + 1 + t * 0.9 + R.nextDouble() * 3, c.z + R.nextGaussian() * 1.2,
                                0, 0.35, 0);
                        put(level, ModParticles.SMOKE_HUGE.get(), c.x + R.nextGaussian() * 2, g + t * 0.8 + R.nextDouble() * 4, c.z + R.nextGaussian() * 2,
                                0, 0.25, 0);
                    }
                }
            }
            case 6 -> { // Шахед: шар осколков во все стороны, серо-белый дым, горящие куски
                if (t == 0) {
                    for (int i = 0; i < (60 + p * 6) * dn; i++) {
                        Vec3 d = randomDir();
                        put(level, ParticleTypes.CRIT, c.x, c.y + 0.5, c.z, d.x * 1.4, Math.abs(d.y) * 1.0, d.z * 1.4);
                        if (i % 2 == 0) {
                            put(level, ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, d.x * 0.7, Math.abs(d.y) * 0.8, d.z * 0.7);
                        }
                    }
                    for (int i = 0; i < 8 * dn; i++) {
                        double ang = R.nextDouble() * Math.PI * 2;
                        double out = 0.4 + R.nextDouble() * 0.5;
                        a.debris.add(new double[] {c.x, c.y + 0.5, c.z, Math.cos(ang) * out, 0.7 + R.nextDouble() * 0.6, Math.sin(ang) * out});
                    }
                }
                if (t > 2 && t < 30 && t % 2 == 0) {
                    for (int i = 0; i < 3 * dn; i++) {
                        put(level, ModParticles.SMOKE_WHITE.get(), c.x + R.nextGaussian() * p * 0.3, g + 1 + R.nextDouble() * p * 0.4,
                                c.z + R.nextGaussian() * p * 0.3, 0, 0.05, 0);
                    }
                }
            }
            case 7 -> { // крылатая: огненный гриб поднимается, жирный чёрный столб
                if (t < 18) {
                    for (int i = 0; i < 10 * dn; i++) {
                        double rr = 0.8 + t * 0.15;
                        put(level, ModParticles.FIRE.get(), c.x + R.nextGaussian() * rr, g + 1 + t * 0.6 + R.nextDouble() * 2, c.z + R.nextGaussian() * rr,
                                0, 0.2, 0);
                    }
                }
                if (t > 8 && t < 90 && t % 2 == 0) {
                    for (int i = 0; i < 2 * dn; i++) {
                        put(level, ModParticles.SMOKE_HUGE.get(), c.x + R.nextGaussian() * 1.5, g + 2 + R.nextDouble() * 3, c.z + R.nextGaussian() * 1.5,
                                R.nextGaussian() * 0.01, 0.2, R.nextGaussian() * 0.01);
                    }
                }
            }
            default -> { // FPV, граната: короткий хлопок и серое облачко
                if (t == 1) {
                    for (int i = 0; i < 14 * dn; i++) {
                        Vec3 d = randomDir();
                        put(level, ParticleTypes.POOF, c.x, c.y + 0.4, c.z, d.x * 0.2, Math.abs(d.y) * 0.2, d.z * 0.2);
                    }
                }
            }
        }
    }


    /** Своя частица с размером и временем жизни. */
    private static @javax.annotation.Nullable HdParticle puff(ClientLevel level, ParticleOptions type, double x, double y, double z, double dx, double dy,
            double dz, float size, float life) {
        if (Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, dx, dy, dz) instanceof HdParticle h) {
            h.tune(size, life);
            return h;
        }
        return null;
    }

    private static void tint(@javax.annotation.Nullable HdParticle h, Active a, float k) {
        if (h != null) {
            float v = k * (0.85f + R.nextFloat() * 0.25f);
            h.tint(a.dustR * v, a.dustG * v, a.dustB * v);
        }
    }

    private static void put(ClientLevel level, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz) {
        level.addAlwaysVisibleParticle(type, true, x, y, z, dx, dy, dz);
    }
}
