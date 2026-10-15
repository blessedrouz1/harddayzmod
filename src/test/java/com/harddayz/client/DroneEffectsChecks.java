package com.harddayz.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Offscreen verification of actual GUI and particle materials, with optional Oculus runtime. */
@Mod.EventBusSubscriber(modid = "harddayz", value = Dist.CLIENT)
public final class DroneEffectsChecks {
    private static final Path OUTPUT = Path.of("../../review/drone-fx-checks");
    private static boolean started;
    private static ResourceLocation white;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("harddayz.droneFxSmokeTest") || started || event.phase != TickEvent.Phase.END || mc.getOverlay() != null) return;
        started = true;
        List<String> report = new ArrayList<>();
        try {
            Files.createDirectories(OUTPUT);
            NativeImage pixels = new NativeImage(16, 16, false);
            for (int y=0; y<16; y++) for (int x=0; x<16; x++) pixels.setPixelRGBA(x, y, -1);
            white = mc.getTextureManager().register("drone-fx-white", new DynamicTexture(pixels));
            for (int[] size : new int[][]{{640,360}, {320,240}, {1080,360}}) {
                try (var image = image(size[0], size[1], false, false, false)) {
                    image.flipY();
                    int w=size[0], h=size[1];
                    if ((image.getPixelRGBA(0,h/2)&0xFFFFFF)!=0 || (image.getPixelRGBA(w/2,0)&0xFFFFFF)!=0
                            || (image.getPixelRGBA(w/2,h/2)&0xFFFFFF)!=0xFFFFFF) throw new AssertionError("Vignette bounds");
                    image.writeToFile(OUTPUT.resolve("vignette-"+w+"x"+h+".png"));
                }
            }
            report.add("PASS edge vignette: 16:9, 4:3 and ultrawide; center remains clear");
            try (var dark=image(128,128,true,false,false); var light=image(128,128,true,true,false);
                 var blendedDark=image(128,128,true,false,true); var blendedLight=image(128,128,true,true,true)) {
                int solid=0, translucent=0;
                for (int y=0; y<128; y++) for (int x=0; x<128; x++) {
                    if (dark.getPixelRGBA(x,y)==light.getPixelRGBA(x,y)) solid++;
                    if (blendedDark.getPixelRGBA(x,y)==blendedLight.getPixelRGBA(x,y)) translucent++;
                }
                if (solid<300 || translucent>=solid/2) throw new AssertionError("Particle opacity: "+solid+" / "+translucent);
                dark.flipY(); dark.writeToFile(OUTPUT.resolve("dense-fire.png"));
                report.add("PASS particle material: "+solid+" opaque pixels; translucent control="+translucent);
            }
            if (DroneExhaust.nozzle(Vec3.ZERO, new Vec3(0,0,1),1).distanceTo(new Vec3(0,0.25,-1.5125))>1e-6
                    || DroneExhaust.nozzle(Vec3.ZERO, new Vec3(-1,0,0),0.85f).distanceTo(new Vec3(1.5125*0.85,0.25,0))>1e-6)
                throw new AssertionError("Jet nozzle transform");
            report.add("PASS jet nozzle position, yaw and model scale");
        } catch (Throwable failure) { report.add("FAIL "+failure); failure.printStackTrace(); }
        try { Files.write(OUTPUT.resolve("results.txt"), report); } catch (Exception e) { e.printStackTrace(); }
        mc.stop();
    }

    private static NativeImage image(int w, int h, boolean particle, boolean lightBackground, boolean translucent) {
        var mc=Minecraft.getInstance();
        var target=new TextureTarget(w,h,true,Minecraft.ON_OSX);
        var projection=new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting=RenderSystem.getVertexSorting();
        var stack=RenderSystem.getModelViewStack();
        stack.pushPose(); stack.setIdentity(); RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,w,h,0,1000,3000),VertexSorting.ORTHOGRAPHIC_Z);
            float bg=particle && !lightBackground ? 0 : 1;
            target.setClearColor(bg,bg,bg,1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.disableCull(); RenderSystem.setShaderColor(1,1,1,1);
            if (particle) {
                var material=translucent ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : HdParticle.renderType(HdParticle.Kind.FIRE);
                var tess=Tesselator.getInstance(); var builder=tess.getBuilder();
                var sprite=((TextureAtlas)mc.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES)).getSprite(new ResourceLocation("harddayz","fire_2"));
                material.begin(builder,mc.getTextureManager());
                RenderSystem.setShader(GameRenderer::getParticleShader);
                RenderSystem.setShaderTexture(2,white);
                builder.vertex(16,16,-2000).uv(sprite.getU0(),sprite.getV0()).color(255,255,255,255).uv2(0xF000F0).endVertex();
                builder.vertex(16,112,-2000).uv(sprite.getU0(),sprite.getV1()).color(255,255,255,255).uv2(0xF000F0).endVertex();
                builder.vertex(112,112,-2000).uv(sprite.getU1(),sprite.getV1()).color(255,255,255,255).uv2(0xF000F0).endVertex();
                builder.vertex(112,16,-2000).uv(sprite.getU1(),sprite.getV0()).color(255,255,255,255).uv2(0xF000F0).endVertex();
                material.end(tess);
            } else {
                var graphics=new GuiGraphics(mc,MultiBufferSource.immediate(new BufferBuilder(65536)));
                graphics.pose().translate(0,0,-2000);
                DronePilot.renderVignette(new Gfx(graphics),w,h);
                graphics.renderOutline(16,16,w-32,h-32,0xFFFFFFFF);
                graphics.flush();
            }
            NativeImage result=new NativeImage(w,h,false);
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(target.getColorTextureId()); result.downloadTexture(0,false);
            return result;
        } finally {
            RenderSystem.enableCull(); target.destroyBuffers();
            RenderSystem.setProjectionMatrix(projection,sorting);
            stack.popPose(); RenderSystem.applyModelViewMatrix(); mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
