package com.harddayz.client;

import com.harddayz.HardDayz;
import com.harddayz.client.model.HdModels;
import com.harddayz.entity.Launcher;
import com.harddayz.registry.ModEntities;
import com.harddayz.registry.ModParticles;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

/** Клиентская часть: рендеры, модели, частицы, клавиши, слои интерфейса и подписки на события. */
public final class ClientSetup {
    public static final String CATEGORY = "key.category.harddayz.main";
    /** V: пульт машины, на которой едешь / выйти из управления дроном. */
    public static final KeyMapping CONTROL = new KeyMapping("key.harddayz.control", InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping ENGINE = new KeyMapping("key.harddayz.engine", InputConstants.KEY_B, CATEGORY);
    public static final KeyMapping UNDER = new KeyMapping("key.harddayz.under", InputConstants.KEY_G, CATEGORY);

    /**
     * Embeddium Extras по умолчанию прячет сущности дальше 64 блоков — техника и ракеты пропадают.
     * Дописываем наш мод в его список исключений (сработает со следующего запуска, если файл уже прочитан).
     */
    private static void allowFarRendering() {
        try {
            java.nio.file.Path cfg = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("sodiumextras-client.toml");
            if (!java.nio.file.Files.exists(cfg)) {
                return;
            }
            String s = java.nio.file.Files.readString(cfg);
            int sec = s.indexOf("[embeddiumextras.performance.distanceCulling.entities]");
            if (sec < 0) {
                return;
            }
            int at = s.indexOf("whitelist = [", sec);
            int end = at < 0 ? -1 : s.indexOf(']', at);
            if (end < 0 || s.substring(at, end).contains("harddayz:")) {
                return;
            }
            boolean empty = s.substring(at + "whitelist = [".length(), end).isBlank();
            s = s.substring(0, end) + (empty ? "" : ", ") + "\"harddayz:*\"" + s.substring(end);
            java.nio.file.Files.writeString(cfg, s);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("HardDayz: не удалось поправить sodiumextras-client.toml", e);
        }
    }

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        allowFarRendering();
        modBus.addListener(ClientSetup::onRenderers);
        modBus.addListener(ClientSetup::onLayers);
        modBus.addListener(ClientSetup::onParticles);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(CONTROL);
            e.register(ENGINE);
            e.register(UNDER);
        });
        modBus.addListener(ClientSetup::onGuiLayers);
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(ItemModels::register));
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) {
                DronePilot.onTick();
            } else {
                onTick();
                ClientBlasts.onTick();
                ClientGuns.onTick();
                ClientGunner.onTick();
            }
        });
        bus.addListener((ViewportEvent.ComputeCameraAngles e) -> {
            ClientBlasts.onCamera(e);
            DronePilot.onCamera(e);
        });
        bus.addListener(ClientBlasts::onFogColor);
        bus.addListener((ViewportEvent.ComputeFov e) -> {
            DronePilot.onFov(e);
            ClientGuns.onFov(e);
        });
        // ЛКМ / ПКМ: первый, кто вернул true, отменяет обычное действие (удар, использование предмета)
        bus.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            boolean cancel = false;
            if (e.isAttack()) {
                cancel = ClientGuns.onAttack(e) || DronePilot.onAttack(e) || ClientGunner.onAttack(e);
            } else if (e.isUseItem()) {
                cancel = ClientGuns.onUse(e) || ClientGunner.onUse(e) || DronePilot.onUse(e);
            }
            if (cancel) {
                e.setCanceled(true);
            }
        });
        bus.addListener((InputEvent.MouseScrollingEvent e) -> {
            if (DronePilot.onScroll(e)) {
                e.setCanceled(true);
            }
        });
        bus.addListener((RenderHandEvent e) -> {
            if (DronePilot.onHand(e)) {
                e.setCanceled(true);
            }
        });
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.LAUNCHER.get(), LauncherRenderer::new);
        event.registerEntityRenderer(ModEntities.MISSILE.get(), MissileRenderer::new);
        event.registerEntityRenderer(ModEntities.DRONE.get(), DroneRenderer::new);
        event.registerEntityRenderer(ModEntities.MINE.get(), MineRenderer::new);
    }

    private static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HdModels.GRAD, HdModels::grad);
        event.registerLayerDefinition(HdModels.ISKANDER, HdModels::iskander);
        event.registerLayerDefinition(HdModels.SAM, HdModels::sam);
        event.registerLayerDefinition(HdModels.SHAHED_LAUNCHER, HdModels::shahedLauncher);
        event.registerLayerDefinition(HdModels.ROCKET_GRAD, HdModels::rocketGrad);
        event.registerLayerDefinition(HdModels.ROCKET_BALLISTIC, HdModels::rocketBallistic);
        event.registerLayerDefinition(HdModels.ROCKET_SAM, HdModels::rocketSam);
        event.registerLayerDefinition(HdModels.BOMB, HdModels::bomb);
        event.registerLayerDefinition(HdModels.FLAME, HdModels::flame);
        event.registerLayerDefinition(HdModels.SHAHED, HdModels::shahed);
        event.registerLayerDefinition(HdModels.FPV, HdModels::fpv);
        event.registerLayerDefinition(HdModels.RECON, HdModels::recon);
        event.registerLayerDefinition(HdModels.RAMP, HdModels::ramp);
        event.registerLayerDefinition(HdModels.GRAD_P, HdModels::gradP);
        event.registerLayerDefinition(HdModels.SAM_POST, HdModels::samPost);
        event.registerLayerDefinition(HdModels.EW, HdModels::ew);
        event.registerLayerDefinition(HdModels.SHAHED_238, HdModels::shahed238);
        event.registerLayerDefinition(HdModels.GERBERA, HdModels::gerbera);
        event.registerLayerDefinition(HdModels.MG, HdModels::mg);
        event.registerLayerDefinition(HdModels.PICKUP_MG, () -> HdModels.pickup(false));
        event.registerLayerDefinition(HdModels.PICKUP_SAM, () -> HdModels.pickup(true));
        event.registerLayerDefinition(HdModels.BMP2, () -> HdModels.bmp(false));
        event.registerLayerDefinition(HdModels.BRADLEY, () -> HdModels.bmp(true));
        event.registerLayerDefinition(HdModels.RADAR, HdModels::radar);
        event.registerLayerDefinition(HdModels.SAM_MANUAL, HdModels::samManual);
        event.registerLayerDefinition(MineRenderer.AP, MineRenderer::ap);
        event.registerLayerDefinition(MineRenderer.AT, MineRenderer::at);
    }

    private static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SMOKE.get(), s -> new HdParticle.Provider(s, HdParticle.Kind.SMOKE));
        event.registerSpriteSet(ModParticles.SMOKE_HUGE.get(), s -> new HdParticle.Provider(s, HdParticle.Kind.SMOKE_HUGE));
        event.registerSpriteSet(ModParticles.SMOKE_WHITE.get(), s -> new HdParticle.Provider(s, HdParticle.Kind.SMOKE_WHITE));
        event.registerSpriteSet(ModParticles.DUST.get(), s -> new HdParticle.Provider(s, HdParticle.Kind.DUST));
        event.registerSpriteSet(ModParticles.FIRE.get(), s -> new HdParticle.Provider(s, HdParticle.Kind.FIRE));
        event.registerSpriteSet(ModParticles.FLASH.get(), s -> new HdParticle.Provider(s, HdParticle.Kind.FLASH));
        event.registerSpriteSet(ModParticles.GLOW.get(), s -> new HdParticle.Provider(s, HdParticle.Kind.GLOW));
    }

    /** Слой интерфейса: обёртка GuiGraphics в Gfx. */
    private interface Layer {
        void render(Gfx g, float pt);
    }

    private static void overlay(RegisterGuiOverlaysEvent event, String id, Layer layer) {
        event.registerAboveAll(id, (gui, graphics, pt, w, h) -> layer.render(new Gfx(graphics), pt));
    }

    private static void onGuiLayers(RegisterGuiOverlaysEvent event) {
        overlay(event, "flash", ClientBlasts::renderFlash);
        overlay(event, "drone_hud", DronePilot::renderHud);
        overlay(event, "vehicle_hp", ClientSetup::renderVehicleHp);
        overlay(event, "gunner", ClientGunner::renderHud);
        overlay(event, "alert", ClientSetup::renderAlert);
        overlay(event, "gun_sight", ClientGuns::renderSight);
        overlay(event, "lives", ClientSetup::renderLives);
    }

    /** Полоса прочности техники, на которую смотришь или в которой едешь. */
    private static void renderVehicleHp(Gfx g, float dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || DronePilot.active()) {
            return;
        }
        com.harddayz.entity.Vehicle v = null;
        if (mc.player.getVehicle() instanceof com.harddayz.entity.Vehicle rv) {
            v = rv;
        } else if (mc.player.isShiftKeyDown() && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh
                && eh.getEntity() instanceof com.harddayz.entity.Vehicle hv) {
            v = hv;
        }
        if (v == null) {
            return;
        }
        float k = net.minecraft.util.Mth.clamp(v.health() / Math.max(1, v.maxHealth()), 0, 1);
        int w = 120;
        int x = g.guiWidth() / 2 - w / 2;
        int y = g.guiHeight() / 2 + 22;
        int col = k > 0.6f ? 0xFF50D050 : k > 0.3f ? 0xFFE0C040 : 0xFFE04030;
        g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xC0000000);
        g.fill(x, y, x + (int) (w * k), y + 5, col);
        String txt = (int) v.health() + " / " + (int) v.maxHealth();
        if (v instanceof Launcher fl && fl.usesFuel()) {
            float f = fl.fuel() / fl.tank();
            g.fill(x - 1, y + 19, x + w + 1, y + 24, 0xC0000000);
            g.fill(x, y + 20, x + (int) (w * f), y + 23, f > 0.25f ? 0xFFD8A040 : 0xFFE04030);
            String ft = "⛽ " + (int) fl.fuel() + " / " + (int) fl.tank() + " л";
            g.text(mc.font, ft, g.guiWidth() / 2 - mc.font.width(ft) / 2, y + 26, f > 0.25f ? 0xFFE8D8A0 : 0xFFFF6050, true);
        }
        g.text(mc.font, txt, g.guiWidth() / 2 - mc.font.width(txt) / 2, y + 8, 0xFFE8E8E8, true);
    }

    /** Режим «жизни»: запас каждой команды вверху экрана. */
    private static void renderLives(Gfx g, float dt) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientAccess.lives.isEmpty() || DronePilot.active()) {
            return;
        }
        int x = g.guiWidth() / 2 - ClientAccess.lives.size() * 45;
        for (String r : ClientAccess.lives) {
            String[] p = r.split(";", -1);
            int col = switch (p[0]) {
                case "blue" -> 0xFF6080FF;
                case "red" -> 0xFFFF5050;
                case "green" -> 0xFF50E050;
                default -> 0xFFFFE050;
            };
            g.fill(x, 2, x + 86, 15, 0x90000000);
            g.text(mc.font, p[1] + ": " + p[2] + " ❤", x + 4, 5, col, true);
            x += 90;
        }
    }

    private static String alertText = "";
    private static int alertTicks;

    public static void alert(String text) {
        alertText = text;
        alertTicks = 80;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 0.6f);
        }
    }

    private static void renderAlert(Gfx g, float dt) {
        if (alertTicks <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if ((alertTicks / 6) % 2 == 0) {
            int w = mc.font.width(alertText);
            int x = g.guiWidth() / 2 - w / 2;
            g.fill(x - 6, 36, x + w + 6, 50, 0xB0400000);
            g.text(mc.font, alertText, x, 39, 0xFFFF5050, true);
        }
    }

    private static void onTick() {
        if (alertTicks > 0) {
            alertTicks--;
        }
        Minecraft mc = Minecraft.getInstance();
        while (UNDER.consumeClick()) {
            if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof com.harddayz.item.GunItem) {
                com.harddayz.network.Net.toServer(new com.harddayz.network.Net.UnderFire());
            }
        }
        while (ENGINE.consumeClick()) {
            DronePilot.toggleEngine();
        }
        while (CONTROL.consumeClick()) {
            if (DronePilot.active()) {
                DronePilot.stop(true);
            } else if (mc.player != null && mc.player.getVehicle() instanceof Launcher l) {
                ClientAccess.openLauncher(l);
            }
        }
    }
}
