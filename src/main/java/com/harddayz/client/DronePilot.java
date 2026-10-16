package com.harddayz.client;

import javax.annotation.Nullable;

import com.harddayz.HardDayz;
import com.harddayz.entity.Drone;
import com.harddayz.network.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
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
    private static @Nullable Input savedInput;
    private static @Nullable CameraType savedCamera;
    private static Vec3 vel = Vec3.ZERO;
    private static float roll;
    private static float zoom = 1f;
    private static int ignoreUntil;
    private static net.minecraft.client.multiplayer.ClientLevel sessionLevel;
    private static net.minecraft.client.player.LocalPlayer sessionPlayer;

    private DronePilot() {
    }

    private static boolean nightVision;

    private static double savedViewScale;

    /** N: ночной режим камеры — всё видно в темноте, картинка зелёная, как у настоящего ПНВ. */
    /** Тепловизор: тёплые цели светятся белым контуром, картинка серая. */
    private static boolean thermal;

    public static boolean thermalOn() {
        return drone != null && thermal;
    }

    public static void toggleNightVision() {
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || mc.player == null) {
            return;
        }
        if (thermal) {
            thermal = false;
            mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            nightVision = false;
        } else if (nightVision) {
            thermal = true; // ночь → тепловизор (ночное зрение остаётся, чтобы видеть местность)
        } else {
            if (!mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
                mc.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 1_000_000, 0,
                        false, false));
            }
            nightVision = true;
        }
        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), 0.6f, thermal ? 2f : nightVision ? 1.6f : 1.2f);
    }

    public static boolean nightMode() {
        return drone != null && nightVision;
    }

    public static boolean active() {
        return drone != null;
    }

    public static boolean isPiloting(Drone d) {
        return drone == d;
    }

    /** Где стоит тело пилота: пока ведём дрон, чанки вокруг тела у клиента могут выгрузиться — держим тело на месте. */
    private static @org.jetbrains.annotations.Nullable Vec3 body;
    private static int freeze;

    /** Насколько помехи мешают сейчас (0..1): слабая связь + РЭБ. */
    static float noise(Drone d) {
        if (d.kind() == Drone.Kind.FPV_FIBER) {
            return 0; // по кабелю картинка чистая
        }
        float s = d.link();
        // аналоговая картинка портится постепенно: с половины дальности снег, у края почти ничего не видно
        return Math.max(Math.max(0.55f - s, 0) / 0.55f * 0.9f, d.interference());
    }

    static void start(Drone d) {
        Minecraft mc = Minecraft.getInstance();
        checkSession(mc);
        if (mc.player == null || drone != null || mc.player.tickCount < ignoreUntil) {
            return;
        }
        drone = d;
        headSet = false;
        body = mc.player.position();
        mc.player.setNoGravity(true);
        mc.gameRenderer.setRenderHand(false); // никаких автоматов в кадре камеры дрона
        vel = Vec3.ZERO;
        zoom = 1f;
        savedInput = mc.player.input;
        mc.player.input = new Input();
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(d);
        // с высоты видно далеко: сущности рисуются в несколько раз дальше обычного
        savedViewScale = net.minecraft.world.entity.Entity.getViewScale();
        net.minecraft.world.entity.Entity.setViewScale(Math.max(savedViewScale, 6.0));
        mc.player.displayClientMessage(Component.translatable("message.harddayz.pilot_start").withStyle(ChatFormatting.GREEN), true);
    }

    /** Выйти из управления. {@code release} — сообщить серверу (дрон повиснет на месте). */
    public static void stop(boolean release) {
        Minecraft mc = Minecraft.getInstance();
        Drone d = drone;
        drone = null;
        headSet = false;
        thermal = false;
        if (mc.player != null) {
            if (savedInput != null) {
                mc.player.input = savedInput;
            }
            mc.setCameraEntity(mc.player);
            if (nightVision) {
                mc.player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                nightVision = false;
            }
            if (savedViewScale > 0) {
                net.minecraft.world.entity.Entity.setViewScale(savedViewScale);
                savedViewScale = 0;
            }
            if (savedCamera != null) {
                mc.options.setCameraType(savedCamera);
            }
            ignoreUntil = mc.player.tickCount + 20;
            mc.gameRenderer.setRenderHand(true);
            freeze = 200; // держать тело, пока вокруг него не прогрузятся чанки
        }
        savedInput = null;
        if (release && d != null && !d.isRemoved()) {
            com.harddayz.network.Net.toServer(new Net.DroneAction(d.getId(), Net.DroneAction.RELEASE));
        }
    }

    private static float prevRoll;

    /** A new world/player has a new tick counter and must not inherit the old camera lock. */
    private static void checkSession(Minecraft mc) {
        if (sessionLevel == mc.level && sessionPlayer == mc.player) return;
        if (sessionPlayer != null) {
            if (savedInput != null) sessionPlayer.input = savedInput;
            sessionPlayer.setNoGravity(false);
            if (nightVision) sessionPlayer.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            if (savedViewScale > 0) {
                net.minecraft.world.entity.Entity.setViewScale(savedViewScale);
                savedViewScale = 0;
            }
        }
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        if (drone != null) {
            mc.setCameraEntity(mc.player);
            mc.gameRenderer.setRenderHand(true);
        }
        drone = null;
        savedInput = null;
        savedCamera = null;
        body = null;
        freeze = 0;
        nightVision = false;
        ignoreUntil = 0;
        stepReady = false;
        vel = Vec3.ZERO;
        prevRoll = roll = 0;
        zoom = 1;
        sessionLevel = mc.level;
        sessionPlayer = mc.player;
    }

    static void onTick() {
        prevRoll = roll;
        Minecraft mc = Minecraft.getInstance();
        checkSession(mc);
        Drone d = drone;
        if (mc.player != null && body != null && (d != null || freeze > 0)) {
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.player.setPos(body.x, body.y, body.z);
            mc.player.fallDistance = 0;
            if (d == null && (--freeze <= 0 || mc.level != null && mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(body))
                    && mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(body).below(2)) && freeze < 190)) {
                freeze = 0;
                body = null;
                mc.player.setNoGravity(false);
            }
        }
        if (d == null) {
            return;
        }
        if (mc.player == null || mc.level == null || d.isRemoved() || d.level() != mc.level || d.pilotId() != mc.player.getId()) {
            stop(false);
            return;
        }
        if (mc.screen != null) {
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
        steer(mc, d);
        if (d.kind().autonomous) {
            flyShahed(mc, d);
            return;
        }
        boolean fpv = d.kind().fpv();
        double max = fpv ? 1.5 : 0.65;
        double acc = fpv ? 0.12 : 0.06;
        float nz = noise(d);
        boolean drop = mc.player.getRandom().nextFloat() < nz * 0.45f; // пакет управления потерялся
        float f = drop ? 0 : (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        float s = drop ? 0 : (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        if (nz > 0.2f) {
            var rr = mc.player.getRandom();
            vel = vel.add((rr.nextFloat() - 0.5f) * nz * 0.14, (rr.nextFloat() - 0.5f) * nz * 0.08, (rr.nextFloat() - 0.5f) * nz * 0.14);
        }
        float u = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
        float yaw = yawOf(mc, d);
        float pitch = pitchOf(mc, d);
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
        float nz = noise(d);
        var rr = mc.player.getRandom();
        float yaw = yawOf(mc, d) + (rr.nextFloat() - 0.5f) * nz * 25;
        float pitch = Mth.clamp(pitchOf(mc, d) + (rr.nextFloat() - 0.5f) * nz * 18, -40, 70);
        Vec3 want = rr.nextFloat() < nz * 0.45f && vel.lengthSqr() > 1.0E-4 ? vel.normalize() : Vec3.directionFromRotation(pitch, yaw);
        double speed = d.kind().cruise * (mc.options.keyUp.isDown() ? 1.25 : mc.options.keyDown.isDown() ? 0.75 : 1.0);
        Vec3 cur = vel.lengthSqr() < 1.0E-4 ? d.getDeltaMovement() : vel;
        if (cur.lengthSqr() < 1.0E-4) {
            cur = want;
        }
        Vec3 dir = cur.normalize().scale(0.6).add(want.scale(0.4)).normalize(); // курс уже ограничен steer()
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
        roll += (Mth.clamp(-turn * 2.2f, -30, 30) - roll) * 0.1f;
        com.harddayz.network.Net.toServer(new Net.DroneMove(d.getId(), d.getX(), d.getY(), d.getZ(), yaw, pitch));
    }

    private static float lastYaw;

    /** Курс самого аппарата: мышь задаёт цель, аппарат доворачивает к ней не быстрее своей скорости поворота. */
    private static float headYaw, headPitch, prevHeadYaw, prevHeadPitch;
    private static boolean headSet;

    /** Градусов за тик: рыскание и тангаж. Тяжёлые и быстрые — медленно, FPV — резко. */
    private static float[] turnRate(Drone.Kind k) {
        return switch (k) {
            case FPV, FPV_FIBER -> new float[] {8f, 7f};
            case SHAHED -> new float[] {1.6f, 1.4f};
            case SHAHED_131, GERBERA -> new float[] {2.0f, 1.7f};
            case SHAHED_238, GERAN5, BANDEROL -> new float[] {3.2f, 2.8f};
            case CRUISE -> new float[] {0.9f, 0.8f};
            case LANCET, KUB, MOLNIYA -> new float[] {3.0f, 2.6f};
            case INTERCEPTOR -> new float[] {5f, 4.5f};
            case WING_RECON -> new float[] {2.4f, 2f};
            default -> null; // разведчик и Баба-Яга: камера на подвесе, крутится свободно
        };
    }

    /** Двигает курс к взгляду пилота; мышь не может убежать от курса дальше, чем на 50° / 35°. */
    private static boolean steer(Minecraft mc, Drone d) {
        float[] rate = turnRate(d.kind());
        if (rate == null) {
            return false;
        }
        if (!headSet) {
            headYaw = prevHeadYaw = mc.player.getYRot();
            headPitch = prevHeadPitch = mc.player.getXRot();
            headSet = true;
        }
        prevHeadYaw = headYaw;
        prevHeadPitch = headPitch;
        float dy = Mth.wrapDegrees(mc.player.getYRot() - headYaw);
        float dp = mc.player.getXRot() - headPitch;
        if (Math.abs(dy) > 50) {
            mc.player.setYRot(headYaw + Math.signum(dy) * 50);
            dy = Math.signum(dy) * 50;
        }
        if (Math.abs(dp) > 35) {
            mc.player.setXRot(Mth.clamp(headPitch + Math.signum(dp) * 35, -90, 90));
            dp = Math.signum(dp) * 35;
        }
        // плавный разгон поворота: чем дальше цель, тем быстрее, но не выше предела
        headYaw += Mth.clamp(dy * 0.25f, -rate[0], rate[0]);
        headPitch = Mth.clamp(headPitch + Mth.clamp(dp * 0.25f, -rate[1], rate[1]), -89, 89);
        return true;
    }

    /** Куда направлен аппарат сейчас (с учётом инерции поворота). */
    private static float yawOf(Minecraft mc, Drone d) {
        return turnRate(d.kind()) != null ? headYaw : mc.player.getYRot();
    }

    private static float pitchOf(Minecraft mc, Drone d) {
        return turnRate(d.kind()) != null ? headPitch : mc.player.getXRot();
    }

    // ------------------------------------------------------------------ ввод

    static boolean onAttack(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (drone == null) {
            return false;
        }
        event.setSwingHand(false);
        com.harddayz.network.Net.toServer(new Net.DroneAction(drone.getId(), Net.DroneAction.FIRE));
        return true;
    }

    static boolean onUse(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
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
            zoom = Mth.clamp(zoom + (float) event.getScrollDelta() * 0.5f, 1f, 8f);
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
            float pt = (float) event.getPartialTick();
            if (headSet && turnRate(drone.kind()) != null) {
                event.setYaw(prevHeadYaw + Mth.wrapDegrees(headYaw - prevHeadYaw) * pt);
                event.setPitch(Mth.lerp(pt, prevHeadPitch, headPitch));
            } else {
                event.setYaw(mc.player.getViewYRot(pt));
                event.setPitch(mc.player.getViewXRot(pt));
            }
            event.setRoll(Mth.lerp(pt, prevRoll, roll));
        }
    }

    static void onFov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
        if (drone != null) {
            event.setFOV((float) (event.getFOV() / zoom * (drone.kind().fpv() ? 1.15 : 1.0)));
        }
    }

    // ------------------------------------------------------------------ экран пилота

    /** B — завести/заглушить мотор. */
    public static void toggleEngine() {
        if (drone != null && !drone.kind().autonomous) {
            Net.toServer(new Net.DroneAction(drone.getId(), Net.DroneAction.ENGINE));
        }
    }

    public static void renderHud(Gfx g, float delta) {
        Drone d = drone;
        Minecraft mc = Minecraft.getInstance();
        if (d == null || mc.player == null) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        int col = 0xFFE8F0E0;
        double dist = d.distanceTo(mc.player);
        float signal = d.link();
        if (d.kind().autonomous) {
            g.fill(0, 0, w, h, 0x1830FF60); // зелёный оттенок ночной камеры
        }
        // помехи: слабый сигнал, РЭБ, аналоговая «картинка» FPV
        float noise = noise(d);
        if (d.kind() == Drone.Kind.FPV) {
            noise = Math.max(noise, 0.07f);
        }
        if (noise > 0.01f) {
            var r = mc.player.getRandom();
            int n = (int) (noise * 2600);
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
            // РЭБ: картинку рвёт полосами и сдвигает строки
            if (d.interference() > 0.15f) {
                int tears = (int) (d.interference() * 6);
                for (int i = 0; i < tears; i++) {
                    int y = r.nextInt(h), hh = 2 + r.nextInt(10), sh = (int) ((r.nextFloat() - 0.5f) * 40 * d.interference());
                    g.fill(Math.max(0, sh), y, Math.min(w, w + sh), y + hh, ((int) (d.interference() * 120) << 24) | 0x707070);
                }
                g.fill(0, 0, w, h, ((int) (d.interference() * 70) << 24) | 0x404040); // цвет выцветает
            }
            if (noise > 0.6f && r.nextFloat() < (noise - 0.6f) * 0.8f) {
                g.fill(0, 0, w, h, 0xC0101010); // кадр пропал
            }
            if (d.interference() > 0.2f) {
                g.text(mc.font, Component.translatable("hud.harddayz.jamming", (int) (d.interference() * 100)), w / 2 - 40, 30, 0xFFFF6040, true);
            }
        }
        if (thermal) {
            g.fill(0, 0, w, h, 0x90303030); // серая картинка, тёплое — белым контуром
            g.text(mc.font, Component.literal("WHT"), 24, 76, 0xFFFFFFFF, true);
        } else if (nightVision) {
            // зелёная картинка ПНВ и лёгкое «зерно»
            g.fill(0, 0, w, h, 0x5520FF40);
            var rnd = mc.player.getRandom();
            for (int i = 0; i < 90; i++) {
                int x = rnd.nextInt(w), y = rnd.nextInt(h);
                g.fill(x, y, x + 1, y + 1, 0x60C0FFC0);
            }
            g.text(mc.font, Component.literal("NV"), 24, 76, 0xFF60FF60, true);
        }
        if (headSet && turnRate(d.kind()) != null) {
            // кружок «куда хочу»: показывает, куда сейчас доворачивает аппарат
            double fov = Math.toRadians(mc.options.fov().get());
            double px = h / 2.0 / Math.tan(fov / 2);
            int mx = w / 2 + (int) (Math.tan(Math.toRadians(Mth.wrapDegrees(mc.player.getYRot() - headYaw))) * px);
            int my = h / 2 + (int) (Math.tan(Math.toRadians(mc.player.getXRot() - headPitch)) * px);
            int c = 0xC0FFFFFF;
            g.fill(mx - 4, my - 4, mx + 5, my - 3, c);
            g.fill(mx - 4, my + 4, mx + 5, my + 5, c);
            g.fill(mx - 4, my - 3, mx - 3, my + 4, c);
            g.fill(mx + 4, my - 3, mx + 5, my + 4, c);
        }
        renderVignette(g, w, h);
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
        g.text(font, Component.translatable(d.kind().autonomous ? "hud.harddayz.shahed" : d.kind().fpv() ? "hud.harddayz.fpv" : "hud.harddayz.recon"),
                24, 24, 0xFFFFD040, true);
        g.text(font, Component.translatable("hud.harddayz.alt", (int) (d.getY() - ground)), 24, 38, col, true);
        g.text(font, Component.translatable("hud.harddayz.speed", (int) speed), 24, 50, col, true);
        g.text(font, Component.translatable("hud.harddayz.dist", (int) dist), 24, 62, col, true);
        // у «Шахедов» бак с бензином, у реактивных — топливо, у коптеров — АКБ
        String tank = d.kind().jet() ? "hud.harddayz.fuel_jet" : d.kind().shahedLike() ? "hud.harddayz.fuel" : "hud.harddayz.battery";
        g.text(font, Component.translatable(tank, bat), w - 110, 24, bat < 20 ? 0xFFFF5040 : col, true);
        if (d.kind() == Drone.Kind.FPV_FIBER) {
            int left = (int) Math.max(0, Drone.FIBER_RANGE - dist);
            g.text(font, Component.translatable("hud.harddayz.fiber", left), w - 110, 36, left < 60 ? 0xFFFF6040 : col, true);
        } else {
            g.text(font, Component.translatable("hud.harddayz.signal", (int) (signal * 100)), w - 110, 36, signal < 0.3f ? 0xFFFF5040 : col, true);
        }
        if (!d.kind().autonomous) {
            g.text(font, Component.translatable(d.engineOn() ? "hud.harddayz.engine_on" : "hud.harddayz.engine_off"), w - 110, 72,
                    d.engineOn() ? col : 0xFFFFB040, true);
        }
        if (d.kind() == Drone.Kind.RECON) {
            g.text(font, Component.literal(String.format("x%.1f", zoom)), w - 110, 60, col, true);
        }
        g.text(font, Component.translatable(d.kind().autonomous ? "hud.harddayz.help_shahed" : d.kind().fpv() ? "hud.harddayz.help_fpv"
                : d.kind() == Drone.Kind.RECON ? "hud.harddayz.help_recon" : "hud.harddayz.help_bomber"), 24, h - 34, 0xB0FFFFFF, true);
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

    /** Rectangular camera vignette starts at the screen edges for every drone. */
    static void renderVignette(Gfx g, int w, int h) {
        int depth = Math.min(28, Math.min(w, h) / 8);
        for (int i = 0; i < depth; i++) {
            float fade = 1 - (float) i / depth;
            int color = Math.round(255 * fade * fade) << 24;
            int left = i, top = i;
            int right = w - i, bottom = h - i;
            g.fill(left, top, right, top + 1, color);
            g.fill(left, bottom - 1, right, bottom, color);
            g.fill(left, top + 1, left + 1, bottom - 1, color);
            g.fill(right - 1, top + 1, right, bottom - 1, color);
        }
    }
}
