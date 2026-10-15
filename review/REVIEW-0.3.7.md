# HardDayz 0.3.7 — hydraulics and Oculus

## Changes
- 12 launcher models now use a fixed barrel, sliding sleeve and rod with explicit moving pin bones. Solved in the barrel parent frame after rack/erector rotation, including turret rotation. No axial scaling; overlap retained throughout travel.
- Oculus 1.8.0 replaces ModelPart.render with a copied Sodium path that reconstructs cuboids from vanilla constructor parameters. FaceCube intentionally uses empty vanilla polygons and custom UV geometry, so the copied renderer emitted nothing. HdModel now traverses the registered geometry and calls each Cube.compile directly when VBO rendering is unavailable. Visibility, skipDraw and fixed cube rotations are preserved.
- Removed the ineffective Oculus-only cutout workaround. Standard translucent entity material preserves the original glass alpha.
- Oculus 1.8.0 and Embeddium 0.3.31 installed as optional deobfuscated development runtime dependencies. Original official JARs retained in runtime-mods; launch-minecraft.command enables the installed dependencies. They are not bundled into the mod JAR.

## Verification
- Clean release build and dedicated GameTest server: all 12 required tests passed.
- Actual OpenGL client with Oculus + Embeddium: all 1092 poses (12 models, 0..90 degrees, turret 127 degrees) retain pin alignment and sleeve overlap.
- Six model images match independent ModelPart.visit/Cube.compile reference exactly, before and after resource reload. Vehicle silhouettes have 7396..8785 visible pixels; drone and missile also visible. Images: model-checks/*.png.
- Without Oculus: CPU/VBO comparison, reuse benchmark and shader/resource reload passed (gpu-checks/results.txt).
- JSON/direct asset-reference check: 244 JSON files, zero issues.
- Visual hydraulic preview inspected: hydraulic-preview.png.

## Scope
Oculus compatibility images were checked without a selected third-party shader pack. This does not establish compatibility with every shader pack. Oculus uses the compatible vertex path; custom VBO shaders remain enabled when Oculus/Iris is absent.
