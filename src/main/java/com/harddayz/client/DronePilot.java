package com.harddayz.client;

import org.jspecify.annotations.Nullable;

import com.harddayz.HardDayz;
import com.harddayz.entity.Drone;
import com.harddayz.network.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Пилотирование дрона с камеры. Тело игрока стоит на месте (ввод подменяется пустым), мышь крутит
 * камеру дрона, WASD — полёт, пробел/Shift — вверх/вниз, ЛКМ — подрыв (FPV) или сброс гранаты (разведчик),
 * колесо — зум (разведчик), V — выйти из управления.
 */
public final class DronePilot {
    private static @Nullable Drone drone;
    private static @Nullable ClientInput savedInput;
    private static @Nullable CameraType savedCamera;
    private static Vec3 vel = Vec3.ZERO;
    private static float roll;
    private static float zoom = 1f;
    private static int ignoreUntil;

    private DronePilot() {
    }

    private static boolean nightVision;

    public static boolean active() {
        return drone != null;
    }

    public static boolean isPiloting(Drone d) {
        return drone == d;
    }

    static void start(Drone d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || drone != null || mc.player.tickCount < ignoreUntil) {
            return;
        }
        drone = d;
        vel = Vec3.ZERO;
        zoom = 1f;
        savedInput = mc.player.input;
        mc.player.input = new ClientInput();
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(d);
        if (d.kind() != Drone.Kind.FPV && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
            mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 1_000_000, 0,
                    false, false));
            nightVision = true;
        }
        mc.player.sendOverlayMessage(Component.translatable("message.harddayz.pilot_start").withStyle(ChatFormatting.GREEN));
    }

    /** Выйти из управления. {@code release} — сообщить серверу (дрон повиснет на месте). */
    public static void stop(boolean release) {
        Minecraft mc = Minecraft.getInstance();
        Drone d = drone;
        drone = null;
        if (mc.player != null) {
            if (savedInput != null) {
                mc.player.input = savedInput;
            }
            mc.setCameraEntity(mc.player);
            if (nightVision) {
                mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                nightVision = false;
            }
            if (savedCamera != null) {
                mc.options.setCameraType(savedCamera);
            }
            ignoreUntil = mc.player.tickCount + 20;
        }
        savedInput = null;
        if (release && d != null && !d.isRemoved()) {
            com.harddayz.network.Net.toServer(new Net.DroneAction(d.getId(), Net.DroneAction.RELEASE));
        }
    }

    static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Drone d = drone;
        if (d == null) {
            return;
        }
        if (mc.player == null || mc.level == null || d.isRemoved() || d.level() != mc.level || d.pilotId() != mc.player.getId()) {
            stop(false);
            return;
        }
        if (mc.gui.screen() != null) {
            return;
        }
        stepReady = true;
    }

    private static boolean stepReady;

    /** Шаг полёта — из тика самого дрона, после сохранения старой позиции: камера интерполируется плавно. */
    public static void step(Drone d) {
        if (d != drone || !stepReady) {
            return;
        }
        stepReady = false;
        Minecraft mc = Minecraft.getInstance();
        if (d.kind().autonomous) {
            flyShahed(mc, d);
            return;
        }
        boolean fpv = d.kind() == Drone.Kind.FPV;
        double max = fpv ? 1.5 : 0.65;
        double acc = fpv ? 0.12 : 0.06;
        float f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        float s = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        float u = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        double yr = Math.toRadians(yaw);
        Vec3 fwd;
        if (fpv) {
            fwd = Vec3.directionFromRotation(pitch, yaw); // FPV летит туда, куда смотрит
        } else {
            fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
        }
        Vec3 right = new Vec3(Math.cos(yr), 0, Math.sin(yr));
        Vec3 want = d.engineOn() ? fwd.scale(f).add(right.scale(s)).add(0, u, 0) : Vec3.ZERO;
        if (want.lengthSqr() > 1) {
            want = want.normalize();
        }
        vel = vel.add(want.scale(acc));
        vel = vel.scale(want.lengthSqr() > 0 ? 0.93 : (fpv ? 0.96 : 0.82));
        if (vel.length() > max) {
            vel = vel.normalize().scale(max);
        }
        if (!d.engineOn()) {
            // мотор заглушен: тяги нет, дрон падает
            vel = new Vec3(vel.x * 0.9, Math.max(-1.6, vel.y - 0.06), vel.z * 0.9);
        }
        Vec3 before = d.position();
        d.setYRot(yaw);
        d.setXRot(pitch);
        d.move(MoverType.SELF, vel);
        boolean crashed = d.horizontalCollision || (d.verticalCollision && vel.y < -0.3);
        if (d.onGround() && vel.y < 0) {
            vel = new Vec3(vel.x, 0, vel.z);
        }
        if (crashed && fpv && vel.length() > 0.6) {
            com.harddayz.network.Net.toServer(new Net.DroneAction(d.getId(), Net.DroneAction.FIRE));
        }
        if (d.horizontalCollision) {
            vel = vel.multiply(0.3, 1, 0.3);
        }
        d.setDeltaMovement(d.position().subtract(before));
        roll += (s * (fpv ? 22 : 10) - roll) * 0.2f;
        com.harddayz.network.Net.toServer(new Net.DroneMove(d.getId(), d.getX(), d.getY(), d.getZ(), yaw, pitch));
    }

    /** «Шахед» под управлением: летит вперёд с крейсерской скоростью, поворачивает плавно за взглядом. */
    private static void flyShahed(Minecraft mc, Drone d) {
        float yaw = mc.player.getYRot();
        float pitch = Mth.clamp(mc.player.getXRot(), -40, 70);
        Vec3 want = Vec3.directionFromRotation(pitch, yaw);
        double speed = d.kind().cruise * (mc.options.keyUp.isDown() ? 1.25 : mc.options.keyDown.isDown() ? 0.75 : 1.0);
        Vec3 cur = vel.lengthSqr() < 1.0E-4 ? d.getDeltaMovement() : vel;
        if (cur.lengthSqr() < 1.0E-4) {
            cur = want;
        }
        Vec3 dir = cur.normalize().scale(0.88).add(want.scale(0.12)).normalize();
        vel = dir.scale(speed);
        Vec3 before = d.position();
        d.setYRot(yaw);
        d.setXRot(pitch);
        d.move(MoverType.SELF, vel);
        if (d.horizontalCollision || d.verticalCollision) {
            com.harddayz.network.Net.toServer(new Net.DroneAction(d.getId(), Net.DroneAction.FIRE));
        }
        d.setDeltaMovement(d.position().subtract(before));
        float turn = Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        roll += (Mth.clamp(-turn * 3, -35, 35) - roll) * 0.15f;
        com.harddayz.network.Net.toServer(new Net.DroneMove(d.getId(), d.getX(), d.getY(), d.getZ(), yaw, pitch));
    }

    private static float lastYaw;

    // ------------------------------------------------------------------ ввод

    static boolean onAttack(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered.Attack event) {
        if (drone == null) {
            return false;
        }
        event.setSwingHand(false);
        com.harddayz.network.Net.toServer(new Net.DroneAction(drone.getId(), Net.DroneAction.FIRE));
        return true;
    }

    static boolean onUse(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered.UseItem event) {
        if (drone == null) {
            return false;
        }
        event.setSwingHand(false);
        Minecraft mc = Minecraft.getInstance();
        if (drone.kind() == Drone.Kind.RECON && mc.player != null && mc.level != null) {
            // точка, куда смотрит камера дрона
            Vec3 eye = drone.getEyePosition();
            Vec3 end = eye.add(Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot()).scale(600));
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                    net.minecraft.world.level.ClipContext.Fluid.ANY, drone));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                var p = hit.getBlockPos();
                Net.toServer(new Net.DroneMark(drone.getId(), p.getX(), p.getY(), p.getZ()));
                markFlash = 10;
            }
        }
        return true;
    }

    private static int markFlash;

    static boolean onScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        if (drone != null && drone.kind() == Drone.Kind.RECON) {
            zoom = Mth.clamp(zoom + (float) event.getDeltaY() * 0.5f, 1f, 8f);
            return true;
        }
        return false;
    }

    static boolean onHand(net.minecraftforge.client.event.RenderHandEvent event) {
        return drone != null;
    }

    static void onCamera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (drone != null && mc.player != null) {
            float pt = event.getPartialTick();
            event.setYaw(mc.player.getViewYRot(pt));
            event.setPitch(mc.player.getViewXRot(pt));
            event.setRoll(roll);
        }
    }

    static void onFov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
        if (drone != null) {
            event.setFOV((float) (event.getFOV() / zoom * (drone.kind() == Drone.Kind.FPV ? 1.15 : 1.0)));
        }
    }

    // ------------------------------------------------------------------ экран пилота

    /** B — завести/заглушить мотор. */
    public static void toggleEngine() {
        if (drone != null && !drone.kind().autonomous) {
            Net.toServer(new Net.DroneAction(drone.getId(), Net.DroneAction.ENGINE));
        }
    }

    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Drone d = drone;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.player == null) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int col = 0xFFE8F0E0;
        double dist = d.distanceTo(mc.player);
        float signal = (float) Mth.clamp(1 - dist / (d.kind().autonomous ? Drone.SHAHED_LINK_RANGE : Drone.SIGNAL_RANGE), 0, 1);
        if (d.kind().autonomous) {
            g.fill(0, 0, w, h, 0x1830FF60); // зелёный оттенок ночной камеры
        }
        // помехи: слабый сигнал, РЭБ, аналоговая «картинка» FPV
        float noise = Math.max(Math.max(0.3f - signal, 0) / 0.3f * 0.8f, d.interference());
        if (d.kind() == Drone.Kind.FPV) {
            noise = Math.max(noise, 0.07f);
        }
        if (noise > 0.01f) {
            var r = mc.player.getRandom();
            int n = (int) (noise * 1400);
            for (int i = 0; i < n; i++) {
                int x = r.nextInt(w);
                int y = r.nextInt(h);
                int a = 0x30 + r.nextInt(0x60);
                g.fill(x, y, x + 1 + r.nextInt(3), y + 1, (a << 24) | (r.nextBoolean() ? 0xFFFFFF : 0x202020));
            }
            int bars = (int) (noise * 8);
            for (int i = 0; i < bars; i++) {
                int y = r.nextInt(h);
                g.fill(0, y, w, y + 1 + r.nextInt(3), ((int) (noise * 90) << 24) | 0xD0D0D0);
            }
            if (noise > 0.6f && r.nextFloat() < (noise - 0.6f) * 0.8f) {
                g.fill(0, 0, w, h, 0xC0101010); // кадр пропал
            }
            if (d.interference() > 0.2f) {
                g.text(mc.font, Component.translatable("hud.harddayz.jamming", (int) (d.interference() * 100)), w / 2 - 40, 30, 0xFFFF6040, true);
            }
        }
        // рамка и прицел
        int cx = w / 2;
        int cy = h / 2;
        g.fill(cx - 12, cy, cx - 4, cy + 1, col);
        g.fill(cx + 4, cy, cx + 12, cy + 1, col);
        g.fill(cx, cy - 10, cx + 1, cy - 4, col);
        g.fill(cx, cy + 4, cx + 1, cy + 10, col);
        g.outline(16, 16, w - 32, h - 32, 0x60FFFFFF);
        int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(d.getX()), Mth.floor(d.getZ()));
        double speed = d.getDeltaMovement().length() * 20;
        int bat = d.kind().battery <= 0 ? 0 : d.battery() * 100 / d.kind().battery;
        var font = mc.font;
        g.text(font, Component.translatable(d.kind().autonomous ? "hud.harddayz.shahed" : d.kind() == Drone.Kind.FPV ? "hud.harddayz.fpv" : "hud.harddayz.recon"),
                24, 24, 0xFFFFD040, true);
        g.text(font, Component.translatable("hud.harddayz.alt", (int) (d.getY() - ground)), 24, 38, col, true);
        g.text(font, Component.translatable("hud.harddayz.speed", (int) speed), 24, 50, col, true);
        g.text(font, Component.translatable("hud.harddayz.dist", (int) dist), 24, 62, col, true);
        g.text(font, Component.translatable("hud.harddayz.battery", bat), w - 110, 24, bat < 20 ? 0xFFFF5040 : col, true);
        g.text(font, Component.translatable("hud.harddayz.signal", (int) (signal * 100)), w - 110, 36, signal < 0.3f ? 0xFFFF5040 : col, true);
        if (!d.kind().autonomous) {
            g.text(font, Component.translatable(d.engineOn() ? "hud.harddayz.engine_on" : "hud.harddayz.engine_off"), w - 110, 72,
                    d.engineOn() ? col : 0xFFFFB040, true);
        }
        if (d.kind() == Drone.Kind.RECON) {
            g.text(font, Component.translatable("hud.harddayz.grenades", d.grenades()), w - 110, 48, col, true);
            g.text(font, Component.literal(String.format("x%.1f", zoom)), w - 110, 60, col, true);
        }
        g.text(font, Component.translatable(d.kind().autonomous ? "hud.harddayz.help_shahed" : d.kind() == Drone.Kind.FPV ? "hud.harddayz.help_fpv"
                : "hud.harddayz.help_recon"), 24, h - 34, 0xB0FFFFFF, true);
        if (mc.player.tickCount % 20 < 10) {
            g.fill(w - 40, h - 36, w - 34, h - 30, 0xFFFF3030);
        }
        g.text(font, "REC", w - 30, h - 36, col, true);
        if (d.kind() == Drone.Kind.RECON) {
            // дальномер: точка под прицелом
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(d.getEyePosition(),
                    d.getEyePosition().add(Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot()).scale(600)),
                    net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.ANY, d));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                var p = hit.getBlockPos();
                g.text(font, p.getX() + " " + p.getY() + " " + p.getZ() + "  (" + (int) Math.sqrt(p.distToCenterSqr(d.position())) + " м)",
                        cx + 14, cy + 6, 0xFFFFE070, true);
            }
            if (markFlash > 0) {
                markFlash--;
                g.outline(cx - 16, cy - 16, 33, 33, 0xFF40FF40);
            }
        }
    }
}
