package com.harddayz.client;

import com.harddayz.item.GunItem;
import com.harddayz.network.Net;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

/** Стрельба из ручного оружия: ЛКМ — огонь, ПКМ — прицел (приближение, меньше разброс, мушка на экране). */
public final class ClientGuns {
    private static int cooldown;
    private static boolean wasDown;
    private static float aimK;

    private ClientGuns() {
    }

    static boolean rangefinder() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && !DronePilot.active() && mc.player.getVehicle() == null
                && mc.player.getMainHandItem().is(com.harddayz.registry.ModItems.RANGEFINDER.get());
    }

    static GunItem gun() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || DronePilot.active() || mc.player.getVehicle() != null) {
            return null;
        }
        ItemStack s = mc.player.getMainHandItem();
        return s.getItem() instanceof GunItem g ? g : null;
    }

    static boolean aiming() {
        return gun() != null && Minecraft.getInstance().options.keyUse.isDown() && Minecraft.getInstance().screen == null;
    }

    static boolean onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if (rangefinder()) {
            Minecraft mc = Minecraft.getInstance();
            var hit = rangeHit();
            if (hit != null) {
                Net.toServer(new Net.LogMark(hit.getX(), hit.getY(), hit.getZ()));
                mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.value(), 0.8f, 1.8f);
            }
            event.setSwingHand(false);
            return true;
        }
        if (gun() == null) {
            return false;
        }
        event.setSwingHand(false);
        return true; // не бить и не ломать блоки
    }

    static boolean onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (gun() == null && !rangefinder()) {
            return false;
        }
        // Let the entity interaction reach the server even when holding a gun.
        if (Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof com.harddayz.entity.Drone d && !d.kind().autonomous) {
            return false;
        }
        event.setSwingHand(false);
        return true; // ПКМ — только прицел
    }

    static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        GunItem g = gun();
        aimK += ((aiming() ? 1 : 0) - aimK) * 0.35f;
        boolean th = g != null && aimK > 0.6f && "thermal".equals(GunItem.part(mc.player.getMainHandItem(), "sight"));
        if (th || !HOT.isEmpty()) {
            thermal(th);
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (g == null || mc.screen != null) {
            wasDown = false;
            return;
        }
        if (g instanceof com.harddayz.item.JavelinItem) {
            var t = aiming() ? com.harddayz.item.JavelinItem.target(mc.level, mc.player) : null;
            if (t != null && t == lockOn) {
                lockTicks++;
            } else {
                lockOn = t;
                lockTicks = 0;
            }
            if (lockOn != null && lockTicks == 40) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 2f);
            } else if (lockOn != null && lockTicks < 40 && lockTicks % 5 == 0) {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), 0.5f, 1.4f);
            }
            if (mc.options.keyAttack.isDown() && !wasDown && cooldown <= 0 && (lockTicks >= 40
                    || GunItem.mag(mc.player.getMainHandItem()) <= 0)) {
                Net.toServer(new Net.GunShot(true));
                cooldown = g.spec.cooldown();
                lastShot = mc.level.getGameTime();
            }
            wasDown = mc.options.keyAttack.isDown();
            return;
        }
        boolean down = mc.options.keyAttack.isDown();
        if (down && cooldown <= 0 && (g.spec.auto() || !wasDown)) {
            Net.toServer(new Net.GunShot(aiming()));
            if (GunItem.mag(mc.player.getMainHandItem()) > 0) {
                lastShot = mc.level.getGameTime();
            }
            cooldown = g.spec.cooldown();
            if (GunItem.mag(mc.player.getMainHandItem()) > 0) {
                float r = g.spec.recoil() * (aiming() ? 0.6f : 1f);
                mc.player.setXRot(mc.player.getXRot() - r);
                mc.player.setYRot(mc.player.getYRot() + (mc.player.getRandom().nextFloat() - 0.5f) * r * 0.5f);
            }
        }
        wasDown = down;
    }

    /** Точка под прицелом дальномера (до 800 блоков). */
    static net.minecraft.core.BlockPos rangeHit() {
        Minecraft mc = Minecraft.getInstance();
        var eye = mc.player.getEyePosition();
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(mc.player.getLookAngle().scale(800)),
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.ANY, mc.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? null : hit.getBlockPos();
    }

    private static final java.util.Set<net.minecraft.world.entity.Entity> HOT = new java.util.HashSet<>();

    /** Тепловизор: тёплые цели в прямой видимости подсвечиваются (сквозь стены — нет). */
    private static void thermal(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        for (var e : HOT) {
            e.setGlowingTag(false);
        }
        HOT.clear();
        if (!on || mc.level == null) {
            return;
        }
        var eye = mc.player.getEyePosition();
        for (var e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(160), e -> e instanceof net.minecraft.world.entity.LivingEntity
                || e instanceof com.harddayz.entity.Vehicle || e instanceof com.harddayz.entity.Drone)) {
            var c = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, e.getBoundingBox().getCenter(),
                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
            if (c.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                e.setGlowingTag(true);
                HOT.add(e);
            }
        }
    }

    static void onFov(ViewportEvent.ComputeFov event) {
        if (rangefinder() && Minecraft.getInstance().options.keyUse.isDown()) {
            event.setFOV(event.getFOV() * 0.15f);
            return;
        }
        GunItem g = gun();
        if (g != null && aimK > 0.01f) {
            float z = g.zoom(Minecraft.getInstance().player.getMainHandItem());
            event.setFOV(event.getFOV() * (1 - (1 - z) * aimK));
        }
    }

    /** 0..1 — насколько поднято к глазу (для рендера в руке). */
    public static float aimK() {
        return aimK;
    }

    private static long lastShot;
    private static net.minecraft.world.entity.Entity lockOn;
    private static int lockTicks;

    /** 0..1 — отдача после выстрела (для анимации). */
    public static float recoilK(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        float t = mc.level.getGameTime() - lastShot + pt;
        return t < 0 || t > 4 ? 0 : 1 - t / 4f;
    }

    /** Мушка и целик при прицеливании. */
    static void renderSight(Gfx g, float dt) {
        Minecraft m0 = Minecraft.getInstance();
        if (rangefinder() && m0.options.keyUse.isDown()) {
            int cx = g.guiWidth() / 2;
            int cy = g.guiHeight() / 2;
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0x30000000);
            g.outline(cx - 40, cy - 25, 80, 50, 0xFFFF4040);
            g.fill(cx - 6, cy, cx + 7, cy + 1, 0xFFFF4040);
            g.fill(cx, cy - 6, cx + 1, cy + 7, 0xFFFF4040);
            var hit = rangeHit();
            String t = hit == null ? "---" : (int) Math.sqrt(hit.distToCenterSqr(m0.player.getEyePosition())) + " м   " + hit.getX() + " " + hit.getY()
                    + " " + hit.getZ();
            g.centeredText(m0.font, t, cx, cy + 32, 0xFFFF6060);
            g.centeredText(m0.font, net.minecraft.network.chat.Component.translatable("hud.harddayz.rangefinder"), cx, cy + 44, 0xC0FFFFFF);
            return;
        }
        GunItem gun = gun();
        if (gun == null || aimK < 0.6f) {
            return;
        }
        if (gun instanceof com.harddayz.item.JavelinItem) {
            // прицел ПТРК: зелёный тепловизор, рамка захвата
            Minecraft m = Minecraft.getInstance();
            int w = g.guiWidth();
            int h = g.guiHeight();
            g.fill(0, 0, w, h, 0x3010FF30);
            int cx = w / 2;
            int cy = h / 2;
            int col = lockTicks >= 40 ? 0xFFFF3030 : 0xFF40FF40;
            int s = lockOn == null ? 30 : 30 - Math.min(20, lockTicks / 2);
            g.outline(cx - s, cy - s / 2, s * 2, s, col);
            g.fill(cx - 60, cy, cx - s - 4, cy + 1, 0xFF40FF40);
            g.fill(cx + s + 4, cy, cx + 60, cy + 1, 0xFF40FF40);
            String st = lockOn == null ? "НЕТ ЦЕЛИ" : lockTicks >= 40 ? "ЗАХВАТ — ЛКМ ПУСК" : "ЗАХВАТ...";
            g.centeredText(m.font, st, cx, cy + 40, col);
            return;
        }
        if ("thermal".equals(GunItem.part(Minecraft.getInstance().player.getMainHandItem(), "sight"))) {
            int w = g.guiWidth();
            int h = g.guiHeight();
            g.fill(0, 0, w, h, 0x40102030);
            g.fill(w / 2 - 12, h / 2, w / 2 + 13, h / 2 + 1, 0xFFFFFFFF);
            g.fill(w / 2, h / 2 - 12, w / 2 + 1, h / 2 + 13, 0xFFFFFFFF);
            g.text(Minecraft.getInstance().font, "WHT", 20, 20, 0xFFFFFFFF, true);
            return;
        }
        boolean scope = gun.zoom(Minecraft.getInstance().player.getMainHandItem()) < 0.4f;
        if (!scope) {
            // открытый прицел и коллиматор видны на самой модели в руке — экран не трогаем
            if (GunItem.part(Minecraft.getInstance().player.getMainHandItem(), "sight").equals("reddot")) {
                g.fill(g.guiWidth() / 2, g.guiHeight() / 2, g.guiWidth() / 2 + 1, g.guiHeight() / 2 + 1, 0xFFFF2020);
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;
        int c = 0xE0101010;
        if (scope) {
            // оптика: тёмные края и перекрестие с «ёлочкой»
            int r = Math.min(cx, cy) - 10;
            g.fill(0, 0, g.guiWidth(), cy - r, 0xFF000000);
            g.fill(0, cy + r, g.guiWidth(), g.guiHeight(), 0xFF000000);
            g.fill(0, cy - r, cx - r, cy + r, 0xFF000000);
            g.fill(cx + r, cy - r, g.guiWidth(), cy + r, 0xFF000000);
            g.fill(cx - r, cy, cx + r, cy + 1, c);
            g.fill(cx, cy - r, cx + 1, cy + r, c);
            for (int i = 1; i <= 4; i++) {
                g.fill(cx - 3, cy + i * 8, cx + 4, cy + i * 8 + 1, c);
            }
            return;
        }
        // открытый прицел: целик (прорезь) и мушка
        g.fill(cx - 22, cy + 3, cx - 3, cy + 7, c);
        g.fill(cx + 4, cy + 3, cx + 23, cy + 7, c);
        g.fill(cx - 1, cy, cx + 2, cy + 10, c);
        g.fill(cx, cy - 1, cx + 1, cy, 0xFFFF4020);
        g.text(mc.font, GunItem.mag(mc.player.getMainHandItem()) + " / " + gun.spec.mag(), cx + 30, cy + 20, 0xFFE0E0E0, true);
    }
}
