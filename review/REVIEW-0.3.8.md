# Drone effects 0.3.8

- Rectangular camera vignette now starts at screen coordinates 0,0 and applies to every piloted drone, before the existing white frame and HUD labels.
- Jet Shahed-238 and Geran-5 emit smoke along interpolated nozzle movement and a short exhaust flame. Nozzle follows renderer pitch, yaw, 0.25-block offset and model scale (24.2 px tail). Parked/stopped engines, ground contact and teleports are excluded. Maximum six smoke samples plus one flame per client tick; no network particle packets or per-entity caches.
- Smoke, dust and fire now use vanilla opaque particle sheets with alpha clipping. Sprite edges/final-life fade shrink out; flash/glow retain translucent blending. New small, short-lived jet effects reuse bundled sprites.

Verification: clean build, 12 required dedicated-server GameTests, 246 asset JSONs with zero issues. OpenGL client with Oculus + Embeddium verified vignette on 16:9, 4:3, ultrawide; center remains clear. Production fire material yielded 1161 pixels independent of black/white background, versus zero for the former translucent control. Nozzle position, yaw and model scale checks passed. Images and results in drone-fx-checks. Live drone-flight appearance was not checked in a world; the runtime material and nozzle math were checked separately. No third-party shader pack selected.
