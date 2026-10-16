package com.harddayz.client;

import javax.annotation.Nullable;

import com.harddayz.entity.Launcher;
import com.harddayz.network.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;

/** Стрелок на пулемёте или ручном ЗРК: огонь по ЛКМ, захват цели, трассеры, экран боезапаса. */
public final class ClientGunner {
    private static @Nullable Entity lock;
    private static int lockTicks;

    private ClientGunner() {
    }

    static @Nullable Launcher seat() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof Launcher l && l.kind().gunner() && l.gunnerPassenger() == mc.player ? l : null;
    }


    static boolean onUse(InputEvent.InteractionKeyMappingTriggered event) {
        return false;
    }

    /** ЛКМ на установке — не бить рукой. */
    static boolean onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (seat() == null) {
            return false;
        }
        event.setSwingHand(false);
        return true;
    }

    static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        Launcher l = seat();
        if (l == null || mc.screen != null) {
            lock = null;
            lockTicks = 0;
            return;
        }
        if (l.kind().lockOn()) {
            Entity t = Launcher.lockTarget(mc.level, mc.player.getEyePosition(), mc.player.getLookAngle(), mc.player.getUUID());
            if (t != null && t == lock) {
                lockTicks++;
            } else {
                lock = t;
                lockTicks = 0;
            }
            if (lock != null && lockTicks == 20) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 2f);
            }
        }
        if (mc.options.keyAttack.isDown()) {
            if (!l.kind().lockOn() || lockTicks >= 20) {
                Net.toServer(new Net.GunnerFire(l.getId()));
            }
        }
    }

    /** Летящие трассеры: x, y, z, dx, dy, dz, длина трассы, пройдено. */
    private static final java.util.List<double[]> FLYING = new java.util.ArrayList<>();

    /** Каждый тик: светящаяся головка трассера с хвостом продвигается на ~22 блока. */
    public static void tickTracers() {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            FLYING.clear();
            return;
        }
        for (var it = FLYING.iterator(); it.hasNext(); ) {
            double[] t = it.next();
            double from = t[7];
            t[7] = Math.min(t[6], t[7] + 22);
            for (double s = from; s <= t[7]; s += 0.6) {
                level.addAlwaysVisibleParticle(com.harddayz.registry.ModParticles.GLOW.get(), true, t[0] + t[3] * s, t[1] + t[4] * s, t[2] + t[5] * s, 0, 0, 0);
            }
            if (t[7] >= t[6]) {
                it.remove();
            }
        }
    }

    public static void tracer(Net.Tracer fx) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 a = new Vec3(fx.x1(), fx.y1(), fx.z1());
        Vec3 b = new Vec3(fx.x2(), fx.y2(), fx.z2());
        Vec3 d = b.subtract(a);
        double len = d.length();
        Vec3 dir = len < 0.01 ? Vec3.ZERO : d.scale(1 / len);
        level.addParticle(ParticleTypes.FLAME, a.x, a.y, a.z, dir.x * 0.1, dir.y * 0.1, dir.z * 0.1);
        level.addParticle(ParticleTypes.SMOKE, a.x, a.y, a.z, 0, 0.02, 0);
        if (FLYING.size() < 200) {
            FLYING.add(new double[] {a.x, a.y, a.z, dir.x, dir.y, dir.z, len, 0});
        }
        if (fx.block()) {
            var state = level.getBlockState(BlockPos.containing(b.add(dir.scale(0.05))));
            if (!state.isAir()) {
                for (int i = 0; i < 6; i++) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), b.x, b.y, b.z, (Math.random() - 0.5) * 0.3, 0.15,
                            (Math.random() - 0.5) * 0.3);
                }
            }
            level.addParticle(ParticleTypes.CRIT, b.x, b.y, b.z, -dir.x * 0.3, 0.2, -dir.z * 0.3);
        }
    }

    public static void renderHud(Gfx g, float dt) {
        Minecraft mc = Minecraft.getInstance();
        Launcher l = seat();
        if (l == null) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int cx = w / 2;
        int cy = h / 2;
        g.text(mc.font, Component.translatable("hud.harddayz.gun_ammo", l.ammo(), l.kind().capacity), w - 130, h - 40, 0xFFF0F0E0, true);
        if (l.kind() == Launcher.Kind.SAM_MANUAL) {
            boolean locked = lock != null && lockTicks >= 20;
            int col = locked ? 0xFFFF4040 : lock != null ? 0xFFFFD040 : 0x80FFFFFF;
            int r = 18;
            g.outline(cx - r, cy - r, r * 2, r * 2, col);
            Component txt = locked ? Component.translatable("hud.harddayz.lock_on") : lock != null ? Component.translatable("hud.harddayz.locking")
                    : Component.translatable("hud.harddayz.no_target");
            g.text(mc.font, txt, cx - mc.font.width(txt) / 2, cy + r + 4, col, true);
        } else {
            g.fill(cx - 6, cy, cx + 7, cy + 1, 0xC0FFFFFF);
            g.fill(cx, cy - 6, cx + 1, cy + 7, 0xC0FFFFFF);
        }
        g.text(mc.font, Component.translatable("hud.harddayz.gunner_help"), 12, h - 30, 0xB0FFFFFF, true);
    }
}
