package com.harddayz.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Мягкие частицы взрыва: дым растёт, медленно всплывает и тает; огонь светится сам,
 * остывает от бело-жёлтого к тёмно-красному и гаснет.
 */
public class HdParticle extends TextureSheetParticle {
    public enum Kind {
        SMOKE, SMOKE_HUGE, SMOKE_WHITE, DUST, FIRE, FLASH, GLOW, JET_SMOKE, JET_FIRE
    }

    private final Kind kind;
    private final SpriteSet sprites;
    private final float baseSize;
    private final float spin;
    private final float r0;
    private final float g0;
    private final float b0;

    protected HdParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        pickSprite(sprites);
        this.kind = kind;
        this.sprites = sprites;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        RandomSource r = level.getRandom();
        this.roll = r.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = this.roll;
        this.spin = (r.nextFloat() - 0.5f) * 0.03f;
        float shade;
        switch (kind) {
            case JET_SMOKE -> {
                lifetime = 35 + r.nextInt(20);
                baseSize = 0.4f + r.nextFloat() * 0.25f;
                friction = 0.94f;
                shade = 0.65f + r.nextFloat() * 0.15f;
                r0 = shade;
                g0 = shade;
                b0 = shade;
            }
            case JET_FIRE -> {
                lifetime = 4 + r.nextInt(4);
                baseSize = 0.22f + r.nextFloat() * 0.12f;
                friction = 0.9f;
                r0 = 1f;
                g0 = 0.85f;
                b0 = 0.4f;
            }
            case SMOKE -> {
                lifetime = 100 + r.nextInt(120);
                baseSize = 1.6f + r.nextFloat() * 1.8f;
                friction = 0.94f;
                shade = 0.10f + r.nextFloat() * 0.18f;
                r0 = shade;
                g0 = shade * 0.96f;
                b0 = shade * 0.92f;
            }
            case SMOKE_HUGE -> {
                lifetime = 160 + r.nextInt(160);
                baseSize = 4.5f + r.nextFloat() * 4f;
                friction = 0.95f;
                shade = 0.12f + r.nextFloat() * 0.16f;
                r0 = shade;
                g0 = shade * 0.95f;
                b0 = shade * 0.9f;
            }
            case SMOKE_WHITE -> {
                lifetime = 80 + r.nextInt(80);
                baseSize = 1.0f + r.nextFloat() * 1.0f;
                friction = 0.93f;
                shade = 0.82f + r.nextFloat() * 0.14f;
                r0 = shade;
                g0 = shade;
                b0 = shade;
            }
            case DUST -> {
                lifetime = 60 + r.nextInt(80);
                baseSize = 1.4f + r.nextFloat() * 1.6f;
                friction = 0.9f;
                r0 = 0.50f + r.nextFloat() * 0.1f;
                g0 = 0.42f + r.nextFloat() * 0.08f;
                b0 = 0.32f + r.nextFloat() * 0.06f;
            }
            case GLOW -> {
                lifetime = 6;
                baseSize = 3.5f;
                friction = 1f;
                r0 = 1f;
                g0 = 0.92f;
                b0 = 0.7f;
            }
            case FLASH -> {
                lifetime = 9;
                baseSize = 40f;
                friction = 1f;
                r0 = 1f;
                g0 = 0.85f;
                b0 = 0.6f;
            }
            default -> {
                lifetime = 10 + r.nextInt(14);
                baseSize = 1.2f + r.nextFloat() * 1.6f;
                friction = 0.86f;
                r0 = 1f;
                g0 = 0.9f;
                b0 = 0.6f;
            }
        }
        this.quadSize = baseSize * 0.5f;
        setColor(r0, g0, b0);
        setAlpha(1f);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float k = age / (float) lifetime;
        roll += spin;
        switch (kind) {
            case GLOW -> {
                quadSize = baseSize * (1 - k * 0.7f);
                setColor(1f, 0.92f - k * 0.4f, 0.7f - k * 0.55f);
                setAlpha(1 - k);
            }
            case FLASH -> {
                quadSize = baseSize * (0.6f + k * 0.6f);
                setAlpha(0.85f * (1 - k) * (1 - k));
            }
            case FIRE, JET_FIRE -> {
                setSpriteFromAge(sprites);
                quadSize = baseSize * (0.6f + k * 0.8f);
                // бело-жёлтый -> оранжевый -> тёмно-красный
                setColor(1f, Mth.lerp(k, 0.92f, 0.25f), Mth.lerp(k, 0.6f, 0.05f));
                setAlpha(k < 0.75f ? 1 : (1 - k) / 0.25f);
                yd += 0.004;
            }
            case DUST -> {
                quadSize = baseSize * (0.5f + k * 1.2f);
                yd -= 0.002;
                setAlpha(k < 0.65f ? 1 : (1 - k) / 0.35f);
            }
            default -> {
                // дым растёт, всплывает, к концу тает
                quadSize = baseSize * (0.5f + (float) Math.sqrt(k) * 1.3f);
                yd += kind == Kind.SMOKE_WHITE || kind == Kind.JET_SMOKE ? 0.0006 : 0.0012;
                float fade = k < 0.6f ? 1 : 1 - (k - 0.6f) / 0.4f;
                setAlpha(fade);
                // свежий дым подсвечен огнём снизу
                if (kind != Kind.SMOKE_WHITE && kind != Kind.JET_SMOKE && age < 8) {
                    float glow = 1 - age / 8f;
                    setColor(Mth.lerp(glow, r0, 0.9f), Mth.lerp(glow, g0, 0.45f), Mth.lerp(glow, b0, 0.15f));
                } else {
                    setColor(r0, g0, b0);
                }
            }
        }
        move(xd, yd, zd);
        xd *= friction;
        yd *= friction;
        zd *= friction;
    }

    @Override
    protected int getLightColor(float pt) {
        return kind == Kind.FIRE || kind == Kind.JET_FIRE || kind == Kind.FLASH || kind == Kind.GLOW
                || (kind != Kind.SMOKE_WHITE && kind != Kind.JET_SMOKE && age < 6) ? 0xF000F0 : super.getLightColor(pt);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return renderType(kind);
    }

    static ParticleRenderType renderType(Kind kind) {
        // Solid smoke/fire silhouettes, like vanilla smoke. Only light flashes blend with the background.
        return kind == Kind.FLASH || kind == Kind.GLOW ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                : ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new HdParticle(level, x, y, z, xd, yd, zd, sprites, kind);
        }
    }
}
