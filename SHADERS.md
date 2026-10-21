# SHADERS

Shader strategy for the Ithavollr client pack. Declarative. Updated 2026-10-04.

## DECISION

Ship Complementary Reimagined + Euphoria Patches, tuned via the shipped
settings file. No custom or forked shader pack. Stay on 1.21.11 until
Vibrant Visuals ships for Java; re-evaluate shaders on Aperture then.

## COST MODEL (from Complementary r5.9.3 source)

Every full-screen pass runs at native resolution; the pack has no
render-scale or upscaler. Disabled features compile out (`#if` guards,
`program.*.enabled`), so cost = enabled features x pixels. Pass skeleton
(deferred1, composite, composite1, composite3-6, final) and the colour
target set are fixed regardless of settings.

Heaviest knobs, by loop count, highest first:

| Key                         | Cost mechanism                                            |
|-----------------------------|-----------------------------------------------------------|
| LIGHTSHAFT_QUALI_DEFINE     | 6/10/15/30 ray steps per pixel, each a shadow-map fetch   |
| POM (+POM_DISTANCE)         | up to 128-step loop per terrain fragment within radius; cost ~ radius^2 |
| CLOUD_QUALITY + DOUBLE_REIM_CLOUDS | 6-30 step march per pixel; double = second march on miss |
| SHADOW_QUALITY              | 1=unfiltered 1024 map; 2=2 taps 2048; 3=4; 4=8; 5=16 taps 4096 |
| shadowDistance              | all terrain within radius re-rendered into shadow map     |
| BLOCK_REFLECT_QUALITY       | screen-space ray march, 0/1/3                             |
| FXAA_DEFINE                 | extra full-screen pass (composite7); TAA is already on    |
| DETAIL_QUALITY, SSAO_QUALI_DEFINE | per-pixel, full res                                  |

r5.9.3 profiles (quality prefix):

```
LOW     SHADOW_QUALITY=0 shadowDistance=96  LIGHTSHAFT=0 BLOCK_REFLECT=1 FXAA=-1 DETAIL=2 CLOUD=2
MEDIUM  SHADOW_QUALITY=2 shadowDistance=128 LIGHTSHAFT=1 BLOCK_REFLECT=1 FXAA=1  DETAIL=2 CLOUD=2
HIGH    SHADOW_QUALITY=2 shadowDistance=192 LIGHTSHAFT=2 BLOCK_REFLECT=3 FXAA=1  DETAIL=2 CLOUD=2  (= compiled defaults)
```

## BASELINE TO SHIP

MEDIUM, plus: DOUBLE_REIM_CLOUDS=true, ENTITY_SHADOW=-1,
PLAYER_SHADOW=-1, POM_DISTANCE at default 32, all existing cosmetic
overrides (clouds alt/texture, stars, moon phases, water style, glowing
ores, rain, etc.) carried over by key name. Candidate further cut if a
client still struggles: FXAA_DEFINE=-1 (drops one pass), then
LIGHTSHAFT_QUALI_DEFINE=0.

Per-client tuning is not supported by this design: the txt is a synced
file and unsup re-asserts it when the pack changes it.

## PLAN (seed)

```
1. ./packwiz modrinth add complementary-reimagined   # r5.9.3 -> shaderpacks/*.pw.toml
   ./packwiz modrinth add euphoria-patches           # 1.10.5-r5.9.3-fabric -> mods/
2. ./packwiz remove CompReimi_EuphiPatch (zip + txt); delete from repo
3. write shaderpacks/"ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5.txt"
   with the BASELINE above
4. config/iris.properties: shaderPack=ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5
5. ./packwiz refresh
6. ./gradlew buildPrismPack ; import build/Iffy.zip ; launch
   verify: patcher log reports success; Iris loads the pack; no unknown-option
   warnings; GPU utilisation under ~80% in play on an RTX 3070 at 3440x1440
```

## EUPHORIA PATCHER MECHANICS (from source, 1.10.5)

- Requires `ComplementaryReimagined_r5.9.3.zip` already present in
  `shaderpacks/`. Does not download it.
- Output is a DIRECTORY: `ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5`.
  Settings file: same name + `.txt`.
- On success it rewrites `shaderPack=` in iris.properties to that name.
- On version bump it copies the previous txt to the new name and applies
  `settingsConversions.txt` renames.
- Config: `config/euphoria_patcher/settings.toml`. `advanced.alternativeShaderNames`
  can emit extra copies under custom names; not used.
- Patcher jar version is bound to one Complementary version. Bump both
  together.

## RUNTIME HORIZON

- Mojang replaces OpenGL with Vulkan in Java Edition. Vulkan shipped in
  snapshot 26.2-1 (2026-04-07); default where supported since
  26.4-snapshot-1 (2026-09-22). OpenGL removal date unannounced. Minimum
  spec raised to Vulkan 1.3 GPU (2026-07-21).
- Iris is OpenGL-only and will be discontinued. Successor is Aperture
  (same team): new pack format, Java + Slang, does not load OptiFine/Iris
  packs. Private beta as of 2026-05. No public date.
- Vibrant Visuals for Java: no date; stated to ship without coloured
  lighting.
- Consequence: every current pack, Complementary included, is on a format
  with an end of life. Forking or resurrecting an OptiFine-format pack is
  bridge work only. Custom-pack effort is deferred to Aperture.

## DIAGNOSTICS

GPU-bound check (shaders are the load if utilisation drops when toggled):

```
while true; do echo "$(date +%T) $(nvidia-smi --query-gpu=utilization.gpu,power.draw --format=csv,noheader,nounits)"; sleep 1; done > /tmp/gpu.log
```

Press K in game (Iris shader toggle); compare timestamps with
`.minecraft/logs/latest.log` lines "Shaders disabled!" / "Using shaderpack".
Alt+F3 shows the frametime graph: uniform-tall = GPU bound; spikes = stutter.

Measured 2026-10-03, RTX 3070, 3440x1440, r5.6.1+EP shipped settings:
shaders on 94-100% GPU at 220 W cap; shaders off 25-45%.

## OPEN

- DistantHorizons numberOfThreads=6 is high for 6-core clients. Not changed.
- Whether EP 1.10.5 keeps all 33 Euphoria keys by name; confirm on first
  launch (step 6) from patcher/Iris logs.
