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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Взрывы на клиенте из своих мягких частиц: огненный шар, который остывает в тёмное клубящееся облако,
 * пыльная волна по земле, купол конденсата, столб дыма и «гриб» у мощных. Свет приходит сразу, звук
 * и толчок — с задержкой по скорости звука (~17 блоков за тик).
 */
public final class ClientBlasts {
    private static final double SOUND_SPEED = 17.0;
    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final RandomSource R = RandomSource.create();

    private static float flash;
    private static float glow;
    private static float shake;
    private static int shakeTicks;
    private static int shakeDuration;

    private ClientBlasts() {
    }

    private static final class Active {
        final Vec3 pos;
        final float power;
        final double dist;
        final double ground;
        final BlockState groundBlock;
        int age;
        boolean heard;
        /** Рядом уже идёт свежий взрыв: рисуем только вспышку и огонь, без второго облака, волны и столба. */
        boolean minor;
        /** Множитель количества частиц (когда взрывов много — меньше). */
        float density = 1f;

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
        double dist = mc.player.getEyePosition().distanceTo(pos);
        BlockPos gp = BlockPos.containing(pos.x, pos.y - 0.5, pos.z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, gp.getX(), gp.getZ());
        double ground = Math.min(pos.y, top);
        BlockState below = level.getBlockState(BlockPos.containing(pos.x, ground - 1, pos.z));
        Active a = new Active(pos, fx.power(), dist, ground, below);
        int near = 0;
        for (Active o : ACTIVE) {
            double r = 3 + Math.max(o.power, fx.power()) * 1.2;
            if (o.age < 25 && o.pos.distanceToSqr(pos) < r * r) {
                near++;
            }
        }
        a.minor = near > 0;
        a.density = (float) Mth.clamp(1.0 / (1 + near * 0.6 + ACTIVE.size() * 0.08), 0.15, 1.0);
        ACTIVE.add(a);
        flash = Math.max(flash, (float) Mth.clamp(fx.power() * 0.09 / (1 + dist / 40), 0, 0.9));
        // зарево: видно издалека, небо и туман окрашиваются
        glow = Math.max(glow, (float) Mth.clamp(fx.power() * 0.06 / (1 + dist / 250), 0, 0.85));
        if (!a.minor) {
            float p = fx.power();
            put(level, ModParticles.FLASH.get(), pos.x, Math.max(pos.y, ground) + p * 0.6, pos.z, 0, 0, 0);
            if (p >= 8) {
                put(level, ModParticles.FLASH.get(), pos.x, Math.max(pos.y, ground) + p * 1.5, pos.z, 0, 0.02, 0);
            }
        }
        fireball(level, a);
    }

    // ------------------------------------------------------------------ мгновенно: огненный шар, искры, обломки

    private static void fireball(ClientLevel level, Active a) {
        float p = a.power;
        Vec3 c = a.pos;
        put(level, a.minor ? ParticleTypes.EXPLOSION : ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 0, 0, 0);
        int n = (int) ((25 + p * 16) * a.density);
        for (int i = 0; i < n; i++) {
            Vec3 d = randomDir();
            double rr = p * 0.35 * R.nextDouble();
            double s = 0.08 + R.nextDouble() * 0.22 * (1 + p * 0.06);
            put(level, ModParticles.FIRE.get(), c.x + d.x * rr, c.y + 0.5 + Math.abs(d.y) * rr, c.z + d.z * rr, d.x * s, Math.abs(d.y) * s + 0.05, d.z * s);
        }
        // искры
        for (int i = 0; i < p * 6 * a.density; i++) {
            Vec3 d = randomDir();
            put(level, ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, d.x * 0.5, Math.abs(d.y) * 0.6, d.z * 0.5);
        }
        // обломки грунта
        if (!a.groundBlock.isAir()) {
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
            }
            return;
        }
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof com.harddayz.entity.Missile m && m.kind() == com.harddayz.entity.Missile.Kind.BALLISTIC
                    && m.getDeltaMovement().y < -1) {
                double d = m.distanceTo(mc.player);
                glow = Math.max(glow, (float) Mth.clamp(0.5 / (1 + d / 220), 0, 0.5));
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

    private static void tick(Minecraft mc, ClientLevel level, Active a) {
        float p = a.power;
        int t = a.age;
        Vec3 c = a.pos;
        if (a.minor) {
            if (t < 12 && t % 2 == 0) {
                put(level, ModParticles.FIRE.get(), c.x + R.nextGaussian() * p * 0.2, a.ground + 0.3, c.z + R.nextGaussian() * p * 0.2, 0, 0.05, 0);
            }
            if (t == 4) {
                put(level, ModParticles.SMOKE.get(), c.x, a.ground + 1, c.z, 0, 0.08, 0);
            }
            if (!a.heard && t >= a.dist / SOUND_SPEED) {
                a.heard = true;
                hear(mc, a);
            }
            return;
        }
        // пыльная ударная волна по земле
        int waveTicks = (int) (8 + p * 0.7f);
        if (t < waveTicks) {
            double speed = 0.9 + p * 0.14;
            double r = 1 + t * speed;
            int n = (int) (Math.min(90, 16 + p * 5) * a.density);
            for (int i = 0; i < n; i++) {
                double ang = (i + R.nextDouble()) / n * Math.PI * 2;
                double x = c.x + Math.cos(ang) * r;
                double z = c.z + Math.sin(ang) * r;
                double vx = Math.cos(ang) * speed * 0.3;
                double vz = Math.sin(ang) * speed * 0.3;
                put(level, ModParticles.DUST.get(), x, a.ground + 0.4 + R.nextDouble() * 0.6, z, vx, 0.03, vz);
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
            double cy = a.ground + 1 + t * (0.06 + p * 0.012);
            int n = Math.max(1, (int) ((4 + p * 1.1) * (1 - k * 0.5) * a.density));
            for (int i = 0; i < n; i++) {
                Vec3 d = randomDir();
                double rr = radius * Math.cbrt(R.nextDouble());
                double out = 0.03 + p * 0.004;
                var type = p >= 8 && R.nextFloat() < 0.4f ? ModParticles.SMOKE_HUGE.get() : ModParticles.SMOKE.get();
                put(level, type, c.x + d.x * rr, cy + Math.abs(d.y) * rr * 0.7, c.z + d.z * rr, d.x * out, 0.03 + R.nextDouble() * 0.04, d.z * out);
                if (k < 0.4 && i % 2 == 0) {
                    put(level, ModParticles.FIRE.get(), c.x + d.x * rr * 0.5, cy + Math.abs(d.y) * rr * 0.4, c.z + d.z * rr * 0.5, 0, 0.05, 0);
                }
            }
        }
        // огонь в воронке
        if (t < 25 + p * 3 && t % 2 == 0) {
            for (int i = 0; i < 1 + p / 5; i++) {
                put(level, ModParticles.FIRE.get(), c.x + R.nextGaussian() * p * 0.3, a.ground + 0.3, c.z + R.nextGaussian() * p * 0.3, 0, 0.04, 0);
            }
        }
        // столб дыма от воронки
        int smokeEnd = (int) (50 + p * 9);
        if (t > 6 && t < smokeEnd && t % 2 == 0) {
            double spread = 0.4 + p * 0.15;
            for (int i = 0; i < 1 + p / 5; i++) {
                put(level, ModParticles.SMOKE.get(), c.x + R.nextGaussian() * spread, a.ground + 0.5 + R.nextDouble(), c.z + R.nextGaussian() * spread,
                        R.nextGaussian() * 0.01, 0.12 + R.nextDouble() * 0.08 + p * 0.004, R.nextGaussian() * 0.01);
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
        if (!a.heard && t >= a.dist / SOUND_SPEED) {
            a.heard = true;
            hear(mc, a);
        }
    }

    private static void hear(Minecraft mc, Active a) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = a.pos.subtract(eye);
        double d = a.dist;
        Vec3 at = d < 0.01 ? eye : eye.add(dir.normalize().scale(Math.min(d, 6)));
        double hearRange = 140 + a.power * 70; // «Град» ~490 м, баллистика ~1100 м
        if (d > hearRange) {
            return;
        }
        float fade = (float) (1 - d / hearRange);
        float vol = (float) Mth.clamp(a.power * 0.6 / (1 + d / 110), 0.15, 4) * (a.minor ? 0.45f : 1f) * (0.25f + 0.75f * fade);
        var S = com.harddayz.registry.ModSounds.class;
        if (d < 160) {
            // ванильный удар + свой взрыв поверх
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, vol, 0.55f + R.nextFloat() * 0.1f, false);
            mc.level.playLocalSound(at.x, at.y, at.z, com.harddayz.registry.ModSounds.EXPLODE_NEAR.get(), SoundSource.BLOCKS, vol * 0.7f,
                    0.85f + R.nextFloat() * 0.25f, false);
            if (a.power >= 6) {
                mc.level.playLocalSound(at.x, at.y, at.z, com.harddayz.registry.ModSounds.EXPLODE_BIG.get(), SoundSource.BLOCKS, vol * 0.7f, 0.7f, false);
            }
        } else {
            mc.level.playLocalSound(at.x, at.y, at.z, com.harddayz.registry.ModSounds.EXPLODE_FAR.get(), SoundSource.BLOCKS,
                    (float) Mth.clamp(vol * 1.2, 0.05, 4), 0.9f + R.nextFloat() * 0.15f, false);
        }
        if (a.power >= 6 || d >= 160) {
            mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS,
                    (float) Mth.clamp(vol * 1.1, 0.05, 6), d < 160 ? 0.5f : 0.7f, false);
        }
        double shakeRange = 12 + a.power * 7; // трясёт только вблизи
        float s = d > shakeRange ? 0 : (float) Mth.clamp(a.power * 1.6 / (1 + d / 10) * (1 - d / shakeRange), 0, 7);
        if (s > 0.15f) {
            shake = Math.max(shake * shakeTicks / Math.max(1, shakeDuration), s);
            shakeTicks = shakeDuration = (int) (10 + Math.min(25, a.power * 1.5));
        }
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
                int a = (int) (glow * 150 * k * k);
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

    private static void put(ClientLevel level, ParticleOptions type, double x, double y, double z, double dx, double dy, double dz) {
        level.addAlwaysVisibleParticle(type, true, x, y, z, dx, dy, dz);
    }
}
