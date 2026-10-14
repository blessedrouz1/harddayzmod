package com.harddayz.client;

import com.harddayz.HardDayz;
import com.harddayz.client.model.HdModels;
import com.harddayz.entity.Launcher;
import com.harddayz.registry.ModEntities;
import com.harddayz.registry.ModParticles;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

/** Клиентская часть: рендеры, модели, частицы, клавиши, слои интерфейса и подписки на события. */
public final class ClientSetup {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(HardDayz.MODID, "main"));
    /** V: пульт машины, на которой едешь / выйти из управления дроном. */
    public static final KeyMapping CONTROL = new KeyMapping("key.harddayz.control", InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping ENGINE = new KeyMapping("key.harddayz.engine", InputConstants.KEY_B, CATEGORY);
    public static final KeyMapping UNDER = new KeyMapping("key.harddayz.under", InputConstants.KEY_G, CATEGORY);

    private ClientSetup() {
    }

    public static void init() {
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ClientSetup::onRenderers);
        EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(ClientSetup::onLayers);
        RegisterParticleProvidersEvent.BUS.addListener(ClientSetup::onParticles);
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            e.register(CONTROL);
            e.register(ENGINE);
            e.register(UNDER);
        });
        AddGuiOverlayLayersEvent.BUS.addListener(ClientSetup::onGuiLayers);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ClientSetup::onTick);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ClientBlasts::onTick);
        TickEvent.ClientTickEvent.Pre.BUS.addListener(DronePilot::onTick);
        ViewportEvent.ComputeCameraAngles.BUS.addListener(ClientBlasts::onCamera);
        ViewportEvent.ComputeFogColor.BUS.addListener(ClientBlasts::onFogColor);
        ViewportEvent.ComputeCameraAngles.BUS.addListener(DronePilot::onCamera);
        ViewportEvent.ComputeFov.BUS.addListener(DronePilot::onFov);
        ViewportEvent.ComputeFov.BUS.addListener(ClientGuns::onFov);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ClientGuns::onTick);
        InputEvent.InteractionKeyMappingTriggered.Attack.BUS.addListener(ClientGuns::onAttack);
        InputEvent.InteractionKeyMappingTriggered.UseItem.BUS.addListener(ClientGuns::onUse);
        InputEvent.InteractionKeyMappingTriggered.Attack.BUS.addListener(DronePilot::onAttack);
        InputEvent.InteractionKeyMappingTriggered.UseItem.BUS.addListener(DronePilot::onUse);
        InputEvent.MouseScrollingEvent.BUS.addListener(DronePilot::onScroll);
        RenderHandEvent.BUS.addListener(DronePilot::onHand);
        InputEvent.InteractionKeyMappingTriggered.Attack.BUS.addListener(ClientGunner::onAttack);
        TickEvent.ClientTickEvent.Post.BUS.addListener(ClientGunner::onTick);
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

    private static void onGuiLayers(AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(HardDayz.MODID, "flash"), ClientBlasts::renderFlash);
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(HardDayz.MODID, "drone_hud"), DronePilot::renderHud);
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(HardDayz.MODID, "vehicle_hp"), ClientSetup::renderVehicleHp);
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(HardDayz.MODID, "gunner"), ClientGunner::renderHud);
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(HardDayz.MODID, "alert"), ClientSetup::renderAlert);
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(HardDayz.MODID, "gun_sight"), ClientGuns::renderSight);
        event.getLayeredDraw().add(Identifier.fromNamespaceAndPath(HardDayz.MODID, "lives"), ClientSetup::renderLives);
    }

    /** Полоса прочности техники, на которую смотришь или в которой едешь. */
    private static void renderVehicleHp(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
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
        g.text(mc.font, txt, g.guiWidth() / 2 - mc.font.width(txt) / 2, y + 8, 0xFFE8E8E8, true);
    }

    /** Режим «жизни»: запас каждой команды вверху экрана. */
    private static void renderLives(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
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

    private static void renderAlert(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker dt) {
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

    private static void onTick(TickEvent.ClientTickEvent.Post event) {
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
