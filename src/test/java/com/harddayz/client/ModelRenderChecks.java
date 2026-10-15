package com.harddayz.client;

import com.harddayz.client.model.GeoGpu;
import com.harddayz.client.model.GeoModel;
import com.harddayz.entity.Launcher;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Run with -PmodelSmokeTest, optionally -Poculus. Uses the installed renderer, not mocked consumers. */
@Mod.EventBusSubscriber(modid = "harddayz", value = Dist.CLIENT)
public final class ModelRenderChecks {
    private static boolean started;
    private static final Path OUTPUT = Path.of("../../review/model-checks");
    private static final List<String> REPORT = new ArrayList<>();
    private static final String[] MODELS = {"grad", "shahed_launcher", "smerch", "uragan", "kalibr", "sam",
            "himars", "nasams", "patriot", "s400", "tochka", "iskander"};

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("harddayz.modelSmokeTest") || started || event.phase != TickEvent.Phase.END
                || mc.getOverlay() != null || !GeoGpu.ready()) return;
        started = true;
        try {
            Files.createDirectories(OUTPUT);
            REPORT.add("Oculus=" + ModList.get().isLoaded("oculus") + ", Embeddium=" + ModList.get().isLoaded("embeddium"));
            checkHydraulics();
            GpuRenderChecks.setupWhite();
            checkImages();
            mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> {
                try { checkImages(); finish(null); } catch (Throwable failure) { finish(failure); }
            }));
        } catch (Throwable failure) { finish(failure); }
    }

    private static Vector3f position(GeoModel geo, String name, Vector3f local) {
        PoseStack pose = new PoseStack();
        geo.transformTo(pose, name);
        return pose.last().pose().transformPosition(new Vector3f(local).div(16)).mul(16);
    }

    private static void checkHydraulics() {
        int poses = 0;
        for (String name : MODELS) {
            var model = new LauncherRenderer.GeoLauncherModel(name, null, true);
            GeoModel geo = model.geo;
            String ram = geo.parts.containsKey("ram0") ? "ram0" : "eram0";
            float[] pivot = geo.pivots.get(ram);
            float[] mount = geo.pivots.get(ram + "_mount");
            var localTip = new Vector3f(mount[0] - pivot[0], mount[1] - pivot[1], mount[2] - pivot[2]);
            for (int angle = 0; angle <= 90; angle++) {
                var state = new LauncherRenderer.State();
                state.kind = Launcher.Kind.GRAD;
                state.turret = 127;
                state.elevation = angle;
                state.erect = angle / 90f;
                model.setupAnim(state);
                Vector3f tip = position(geo, ram + "_rod", localTip);
                Vector3f anchor = position(geo, ram + "_mount", new Vector3f());
                if (tip.distance(anchor) > 0.001f) throw new AssertionError(name + " detached at " + angle + ": " + tip + " / " + anchor);
                var barrel = geo.parts.get(ram);
                var stage = geo.parts.get(ram + "_stage");
                var rod = geo.parts.get(ram + "_rod");
                if (barrel.zScale != 1 || stage.zScale != 1 || rod.zScale != 1) throw new AssertionError("Stretched cylinder");
                float[] bodyBox = geo.boxes.get(ram);
                float[] stageBox = geo.boxes.get(ram + "_stage");
                // The back of each sliding sleeve must remain behind its parent's seal.
                if (stageBox[5] + stage.z < bodyBox[2] + 1) throw new AssertionError(name + " empty barrel seal at " + angle);
                // Rodbar is shorter than its stage; exclude the front eye head from bounds.
                float rodBack = mount[2] + (stageBox[5] - stageBox[2] - 1) + rod.z;
                if (rodBack < stageBox[2] + 1) throw new AssertionError(name + " empty rod seal at " + angle);
                poses++;
            }
            REPORT.add("PASS " + name + " pins and sleeve overlap, 0..90 degrees, rotated turret");
        }
        REPORT.add("PASS " + poses + " hydraulic poses; rigid barrels and rods");
    }

    private static void checkImages() throws Exception {
        for (String name : new String[]{"grad", "iskander", "himars", "s400", "fpv", "m_grad"}) {
            GeoModel geo = GeoModel.get(name, null);
            for (var part : geo.root.getAllParts().toList()) { part.resetPose(); part.visible = true; part.skipDraw = false; }
            if (geo.parts.containsKey("ram0_stage") || geo.parts.containsKey("eram0_stage")) {
                var state = new LauncherRenderer.State();
                state.elevation = 55; state.erect = 0.7f; state.ammo = 40;
                new LauncherRenderer.GeoLauncherModel(name, null, true).setupAnim(state);
                for (var part : geo.root.getAllParts().toList()) part.visible = true;
            }
            var type = GpuRenderChecks.type(name, GeoGpu.Pass.TRANSLUCENT);
            try (var reference = GpuRenderChecks.renderCompatible(geo, type, true);
                 var actual = GpuRenderChecks.renderCompatible(geo, type, false)) {
                int visible = 0, mismatch = 0, background = actual.getPixelRGBA(0, 0);
                for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
                    int a = actual.getPixelRGBA(x, y), b = reference.getPixelRGBA(x, y);
                    if (a != background) visible++;
                    if (a != b) mismatch++;
                }
                actual.flipY(); actual.writeToFile(OUTPUT.resolve(name + ".png"));
                if (visible < 100 || mismatch != 0) throw new AssertionError(name + " visible=" + visible + " mismatch=" + mismatch);
                REPORT.add("PASS " + name + " visible pixels=" + visible + ", reference differences=" + mismatch);
            }
        }
    }

    private static void finish(Throwable failure) {
        try {
            REPORT.add(failure == null ? "PASS resource reload and compatibility rendering" : "FAIL " + failure);
            Files.write(OUTPUT.resolve("results.txt"), REPORT);
        } catch (Exception e) { e.printStackTrace(); }
        if (failure != null) failure.printStackTrace();
        Minecraft.getInstance().stop();
    }
}
