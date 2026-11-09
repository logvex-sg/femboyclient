# Phase 9 — Verification Report

> Every claim here is backed by a command run on this machine against this source tree.
> Nothing is copied from a plan or estimated.

## 0. Environment

- Build JVM: JDK 25.0.5 (Loom 1.18.x / 1.17.20 both need Java 25 to run).
- Toolchains available: JDK 21.0.13 (mod target for 1.21.x), JDK 25.0.5 (mod target for 26.x).
- Network: `meta.fabricmc.net`, `maven.fabricmc.net`, `piston-meta.mojang.com` reachable.

## 1. Baseline — Minecraft 1.21.11 (the default build)

| Check | Command | Result |
| --- | --- | --- |
| Full build | `./gradlew build` | **BUILD SUCCESSFUL** |
| Tests | parse `build/test-results/test/*.xml` | **700 tests, 0 failures, 0 errors, 0 skipped, 93 classes** |
| Artifact | `ls build/libs/` | `logvex-crystal-1.21.11-1.0.0.jar` (+ `-sources.jar`) |

The default target is `inventory.primary` (1.21.11). The build reads its whole version matrix
from `build-system/versions.json`; no version string is hard-coded in `build.gradle`.

## 2. Toolchain resolution — the two families

Verified by running the build with each plugin classpath and reading the resolved
dependencies.

| Target | Loom plugin | Resolves? | Evidence |
| --- | --- | --- | --- |
| `1.21.11` | `fabric-loom:1.18.4` (remap) | **yes** | full build green |
| `1.21.1` | `fabric-loom:1.18.4` (remap) | **yes** | `dependencies --configuration minecraft` resolves; source then compiles with the errors in §3 |
| `26.3` | `net.fabricmc.fabric-loom:1.17.20` (no-remap) | **yes** | `dependencies --configuration compileClasspath` → `net.minecraft:minecraft-merged-7e9a32a5b8:26.3`; a probe class using `Minecraft.getInstance()`, `ItemStack`, `Component` compiled |

Key finding, verified directly against the 26.3 client jar
(`piston-data.mojang.com/.../client.jar`): the jar contains
`net/minecraft/client/Minecraft.class`, `net/minecraft/client/gui/screens/Screen.class`,
`net/minecraft/core/BlockPos.class` — i.e. it **ships with Mojang official names**.
Consequently:

- `26.3` has **no** `client_mappings` in its piston-meta manifest.
- `intermediary-26.3-v2.jar` on the Fabric Maven returns **404**.
- The standard `fabric-loom` fails on 26.3 with `Configuration 'mappings' has no dependencies`
  and `Failed to find official mojang mappings for 26.3`; the **no-remap**
  `net.fabricmc.fabric-loom` plugin resolves it.

## 3. Cross-version compile — real breaking-change evidence

### 3.1 Against 1.21.1 (same family, older API)

```
./gradlew -PmcVersion=1.21.1 compileJava
```

- **18 files, 101 diagnostics.**
- Errors cluster into eight changes (see `build-system/breaking-changes.json`). Examples:

| File | Error |
| --- | --- |
| `gui/ClickGui.java:188` | `cannot find symbol: method applyBlur()` on `DrawContext` |
| `gui/ClickGui.java:200` | `cannot find symbol: method pushMatrix()` on `MatrixStack` |
| `gui/HudManager.java:401` | `no suitable method found for translate(int,int)` |
| `client/CrystalClient.java:773` | `cannot find symbol: getDynamicDeltaTicks()` on `RenderTickCounter` |
| `client/CrystalClient.java:34` | `package net.fabricmc.fabric.api.client.rendering.v1.hud does not exist` |
| `assets/AssetRegistry.java:157` | `cannot find symbol: class Store` in `ResourceReloader` |
| `cosmetic/render/PlayerCosmeticFeatureRenderer.java:6` | `package net.minecraft.client.render.command does not exist` |
| `combat/CombatService.java:79` | `cannot find symbol: method getEntityPos()` on `LivingEntity` |

This is the exact work list for the 1.21.x shims; it is not estimated.

### 3.2 Against 26.3 (family boundary)

```
./gradlew -PmcVersion=26.3 compileJava
```

- **17 files, 200 diagnostics.**
- The dominant error is the namespace migration: `package net.minecraft.client.gui.screen
  does not exist`, `package net.minecraft.util.math does not exist`, `package net.minecraft.text
  does not exist`, etc. — Yarn package names that do not exist under Mojang naming.
- This confirms the namespace layer (implementation-plan §2.1) is required before 26.x
  compiles.

## 4. Reproduce

```bash
export JAVA_HOME=/path/to/jdk-25
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew -Porg.gradle.java.installations.paths=<jdk21>:<jdk25> build            # baseline
./gradlew -PmcVersion=1.21.1 ... compileJava   # 1.21.x work list
./gradlew -PmcVersion=26.3  ... compileJava    # namespace work list
python3 build-system/generate_inventory.py --check
python3 build-system/generate_manifest.py --check
```

## 5. Limitations of this verification

- No headless GUI smoke test was possible on this machine (no `xvfb`, no ImageMagick), so
  runtime behaviour per version is not yet verified — only compilation and unit tests.
- The 26.x probe proved the toolchain resolves and named classes compile; it did not build
  the full mod for 26.x (that needs the namespace layer).
- The inventory reflects upstream state at generation time; `--check` guards against drift.

## 6. Compat bands (implementation, 2026-10-14)

The first API breaks that cannot be written as a call valid on both sides are absorbed by a
compat facade. `Compat` (`compat/Compat.java`) is the only thing shared code calls;
`CompatHooks` is compiled from exactly one band directory under `src/versioned/<band>/java`,
selected in `build.gradle` from the target version (mirrored in `generate_manifest.py`).

| band | versions | `DrawContext.getMatrices()` | HUD hook | world hook | layers / sound / splash |
|------|----------|------------------------------|----------|------------|--------------------------|
| `pre-matrix` | 1.21.1 .. 1.21.5 | `MatrixStack` (`push`/`pop`) | `HudRenderCallback` | legacy `v1.WorldRenderEvents` | `RenderLayer.get*` / `master` / `String` |
| `pre-world` | 1.21.6 .. 1.21.8 | `Matrix3x2fStack` (`pushMatrix`) | `v1.hud.HudElementRegistry` | legacy `v1.WorldRenderEvents` | `RenderLayer.get*` / `master` / `String` |
| `no-world-event` | 1.21.9 | `Matrix3x2fStack` | `v1.hud.HudElementRegistry` | none (feature disabled) | `RenderLayer.get*` / `master` / `String` |
| `pre-layers` | 1.21.10 | `Matrix3x2fStack` | `v1.hud.HudElementRegistry` | typed `v1.world.WorldRenderEvents` | `RenderLayer.get*` / `master` / `String` |
| `modern` | 1.21.11+ | `Matrix3x2fStack` | `v1.hud.HudElementRegistry` | typed `v1.world.WorldRenderEvents` | `RenderLayers.*` / `ui` / `Text` |

Boundaries were re-measured with `javap` against the Loom and modularity caches and differ
from the first draft of the register in three places: the draw-matrix type flips at **1.21.6**
(not 1.21.11), the HUD registry lands at **1.21.6**, and the world render event moved to
`v1.world` at **1.21.10** — with **1.21.9** shipping *neither* the old nor the new event, so
world-space ESP is absent there and the hook logs one warning instead of registering.

### 6.1 Verified build results

| version | `compileJava` | `build` (incl. tests) |
|---------|---------------|------------------------|
| 1.21.11 (baseline) | pass | pass |
| 1.21.10 | pass | pass |
| 1.21.9 | pass | pass |
| 1.21.1 .. 1.21.8 | fail | fail |

The remaining 1.21.1 .. 1.21.8 work list is the register entries still marked
`"implemented": false` (input records, entity-pos, resource-loader-v1, entity render state,
misc renames, plus the render-pipeline call). `build-system/compatibility-manifest.json`
carries that list per version under `pendingChanges`.

### 6.2 Known runtime caveat

The band's matrix type is chosen at compile time, so a band whose `getMatrices()` type does
not match its `MatrixStack` cast would compile but fail at runtime. This was the latent defect
in the first two-band draft: it cast 1.21.10's `Matrix3x2fStack` to `MatrixStack`. The band
table above is the fix; no band crosses a type boundary now. Runtime behaviour per version is
still unverified (no headless GUI harness on this machine) — compilation plus unit tests only.

