package com.harddayz.client;

import com.harddayz.HardDayz;
import com.harddayz.client.model.GeoGpu;
import com.harddayz.client.model.GeoModel;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL13;

/** Offscreen CPU/GPU image and reuse checks; available only in the explicit test-client run. */
@Mod.EventBusSubscriber(modid = "harddayz", value = Dist.CLIENT)
public final class GpuRenderChecks {
    private static boolean started;
    private static final Path OUTPUT = Path.of("../../review/gpu-checks");
    private static final List<String> REPORT = new ArrayList<>();
    private static ResourceLocation white;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("harddayz.gpuSmokeTest") || started || event.phase != TickEvent.Phase.END
                || mc.getOverlay() != null || !GeoGpu.ready()) return;
        started = true;
        try {
            Files.createDirectories(OUTPUT);
            setupWhite();
            checkImages();
            checkReuseAndBenchmark();
            mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
                try {
                    checkImages();
                    REPORT.add("PASS resource reload, shaders and VBO recreation");
                    finish(true, null);
                } catch (Throwable failure) { finish(false, failure); }
            }));
        } catch (Throwable failure) { finish(false, failure); }
    }

    static void setupWhite() {
        NativeImage pixels = new NativeImage(16, 16, false);
        for (int y=0; y<16; y++) for (int x=0; x<16; x++) pixels.setPixelRGBA(x, y, (255<<24) | (200<<16) | ((80+y*10)<<8) | (80+x*10));
        white = Minecraft.getInstance().getTextureManager().register("gpu-test-white", new DynamicTexture(pixels));
    }

    static RenderType type(String model, GeoGpu.Pass pass) {
        var texture = new ResourceLocation("harddayz", "textures/entity/" + model + (pass == GeoGpu.Pass.EYES ? "_glowmask" : "") + ".png");
        RenderType base = switch (pass) {
            case TRANSLUCENT -> RenderType.entityTranslucent(texture);
            case CUTOUT -> RenderType.entityCutout(texture);
            case EYES -> RenderType.eyes(texture);
        };
        RenderType wrapper = new RenderType("gpu_test", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                65536, false, pass == GeoGpu.Pass.TRANSLUCENT, () -> {
                    base.setupRenderState();
                    RenderSystem.setShaderTexture(1, white);
                    RenderSystem.setShaderTexture(2, white);
                }, base::clearRenderState) {};
        return GeoGpu.pass(wrapper, pass);
    }

    private static PoseStack pose() {
        PoseStack pose = new PoseStack();
        pose.translate(0, 0, -15);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(26));
        return pose;
    }

    static NativeImage renderCompatible(GeoModel geo, RenderType type, boolean reference) {
        return render(geo, type, false, 1, reference ? 2 : 1);
    }

    private static NativeImage render(GeoModel geo, RenderType type, boolean gpu, int copies) {
        return render(geo, type, gpu, copies, 0);
    }

    private static NativeImage render(GeoModel geo, RenderType type, boolean gpu, int copies, int compatible) {
        var mc = Minecraft.getInstance();
        var target = new TextureTarget(256, 256, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose(); modelView.setIdentity(); RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-6, 6, -1, 11, 1, 100), VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(0.1f, 0.16f, 0.22f, 1);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            Lighting.setupFor3DItems();
            var source = MultiBufferSource.immediate(new BufferBuilder(65536));
            for (int i=0; i<copies; i++) {
                if (compatible == 2) {
                    geo.root.visit(pose(), (p, path, index, cube) -> cube.compile(p, source.getBuffer(type), 0xA00070, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1));
                    continue;
                }
                if (compatible == 1) {
                    new HdModel<Object>(geo.root).draw(pose(), source, type, 0xA00070);
                    continue;
                }
                if (!gpu || !GeoGpu.render(geo.root, pose(), source, type, 0xA00070, OverlayTexture.pack(6, 3))) {
                    if (gpu) throw new AssertionError("GPU path was not selected");
                    geo.root.render(pose(), source.getBuffer(type), 0xA00070, OverlayTexture.pack(6, 3));
                }
            }
            source.endBatch();
            NativeImage image = new NativeImage(256, 256, false);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            return image;
        } finally {
            target.destroyBuffers();
            RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.popPose(); RenderSystem.applyModelViewMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void compare(String label, NativeImage cpu, NativeImage gpu) throws Exception {
        int changed=0, nonBackground=0;
        long sum=0;
        int background=cpu.getPixelRGBA(0,0);
        for (int y=0;y<256;y++) for(int x=0;x<256;x++) {
            int a=cpu.getPixelRGBA(x,y), b=gpu.getPixelRGBA(x,y), worst=0;
            if(a!=background) nonBackground++;
            for(int channel=0;channel<3;channel++) {
                int delta=Math.abs(((a>>>(channel*8))&255)-((b>>>(channel*8))&255));
                sum+=delta; worst=Math.max(worst,delta);
            }
            if(worst>12) changed++;
        }
        cpu.writeToFile(OUTPUT.resolve(label+"-cpu.png"));
        gpu.writeToFile(OUTPUT.resolve(label+"-gpu.png"));
        double mean=sum/(256.0*256*3);
        REPORT.add(label+": mean channel difference="+mean+", pixels >12="+changed+", visible="+nonBackground);
        if(nonBackground<(label.endsWith("EYES") ? 1 : 100) || mean>2 || changed>1500) throw new AssertionError("Image mismatch: "+label);
    }

    private static void checkImages() throws Exception {
        for(String model : new String[]{"grad","iskander","sam","fpv","m_grad"}) {
            GeoModel geo=GeoModel.get(model,null);
            for(var p:geo.root.getAllParts().toList()) p.resetPose();
            if(geo.parts.containsKey("turret")) geo.parts.get("turret").yRot=-0.6f;
            if(geo.parts.containsKey("rack")) geo.parts.get("rack").xRot=0.7f;
            if(geo.parts.containsKey("erector")) geo.parts.get("erector").xRot=1.2f;
            if(geo.parts.containsKey("tip0")) geo.parts.get("tip0").visible=false;
            if(geo.parts.containsKey("jack0_extension")) geo.parts.get("jack0_extension").yScale=1.58f;
            if(geo.parts.containsKey("jack0_foot")) geo.parts.get("jack0_foot").y-=7;
            for(GeoGpu.Pass pass : GeoGpu.Pass.values()) {
                if(pass==GeoGpu.Pass.EYES && !model.equals("grad")) continue;
                RenderType type=type(model,pass);
                try(var cpu=render(geo,type,false,1);var gpu=render(geo,type,true,1)) { compare(model+"-"+pass,cpu,gpu); }
            }
        }
    }

    private static void checkReuseAndBenchmark() {
        var geo=GeoModel.get("grad",null);
        for (var pass : new GeoGpu.Pass[]{GeoGpu.Pass.CUTOUT, GeoGpu.Pass.TRANSLUCENT}) {
            var type=type("grad",pass);
            try(var warm=render(geo,type,true,1)) {}
            long before=GeoGpu.uploads();
            long[] cpu=new long[5], gpu=new long[5];
            for(int i=0;i<5;i++) {
                long start=System.nanoTime(); try(var image=render(geo,type,false,32)) {} cpu[i]=System.nanoTime()-start;
                start=System.nanoTime(); try(var image=render(geo,type,true,32)) {} gpu[i]=System.nanoTime()-start;
            }
            if(GeoGpu.uploads()!=before) throw new AssertionError("Geometry reuploaded on every instance");
            Arrays.sort(cpu); Arrays.sort(gpu);
            REPORT.add("32 Grad copies "+pass+", median offscreen render+readback: CPU="+cpu[2]/1e6+"ms GPU="+gpu[2]/1e6+"ms (not world FPS)");
        }
        REPORT.add("PASS shared VBO reuse; uploads="+GeoGpu.uploads()+" draws="+GeoGpu.draws());
    }

    private static void finish(boolean ok, Throwable failure) {
        REPORT.add(ok ? "GPU_CHECKS_PASSED" : "GPU_CHECKS_FAILED: "+failure);
        try { Files.write(OUTPUT.resolve("results.txt"),REPORT); } catch(Exception e) { HardDayz.LOGGER.error("Cannot write GPU checks",e); }
        if(failure!=null) HardDayz.LOGGER.error("GPU_CHECKS_FAILED",failure);
        else HardDayz.LOGGER.info("GPU_CHECKS_PASSED");
        Minecraft.getInstance().stop();
    }
}
