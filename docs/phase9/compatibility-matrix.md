# Phase 9 — Compatibility Matrix

> Derived from `build-system/compatibility-manifest.json`, which
> `python3 build-system/generate_manifest.py` builds from the version inventory and the
> hand-verified breaking-change register (`build-system/breaking-changes.json`).

## 1. Two independent axes

A version can break the current source in two ways:

1. **API drift (family-internal).** Inside the 1.21.x family the classes keep their Yarn
   names, but method signatures and packages move. These are the register entries below.
2. **Namespace migration (family boundary).** At 26.x the whole naming scheme changes from
   Yarn to Mojang official names. This is not a signature change; it is a rename of nearly
   every Minecraft symbol the mod touches.

The register covers axis 1. Axis 2 is modelled separately (`requiresNamespaceMigration` in
the manifest) because it is handled by the adaptation layer, not by per-call shims.

## 2. Breaking-change register

Each entry is a change that breaks the 1.21.11 source when compiling against an older
version. `introduced` is the first in-range version that has the new API; everything below
it needs the compatibility path.

| id | area | introduced | last version without it | adapter |
| --- | --- | --- | --- | --- |
| `matrixstack-pushpop` | render | `1.21.2` | `1.21.1` | `MatrixStackAdapter` |
| `input-records` | gui/input | `1.21.9` | `1.21.8` | `InputAdapter` |
| `entity-pos` | entity | `1.21.9` | `1.21.8` | `EntityPosAdapter` |
| `fabric-hud-events` | fabric-api | `1.21.9` | `1.21.8` | `HudEventsAdapter` |
| `fabric-world-events` | fabric-api | `1.21.9` | `1.21.8` | `WorldEventsAdapter` |
| `fabric-resource-events` | fabric-api | `1.21.9` | `1.21.8` | `ResourceEventsAdapter` |
| `entity-render-state` | render/entity | `1.21.9` | `1.21.8` | `EntityRenderAdapter` |
| `misc-api-renames` | misc | `1.21.9` | `1.21.8` | `MiscApiAdapter` |

Full signatures for each entry are in `build-system/breaking-changes.json`.

## 3. Per-version matrix (API drift)

| Version | Changes | Affected files | Adapters |
| --- | --- | --- | --- |
| `1.21.1` | 8 | 18 | all eight |
| `1.21.2` … `1.21.8` | 7 | 17 | all but `MatrixStackAdapter` |
| `1.21.9` … `1.21.11` | 0 | 0 | none (current source) |
| `26.1` … `26.3` | 0 | 0 | none, but **namespace migration required** |

## 4. Affected files (union across the range)

18 files, listed by the register:

```
assets/AssetRegistry.java
assets/Assets.java
client/CrystalClient.java
combat/CombatService.java
cosmetic/render/CatCosmeticsModel.java
cosmetic/render/PlayerCosmeticFeatureRenderer.java
gui/AIAssistantScreen.java
gui/BaritoneScreen.java
gui/ClickGui.java
gui/CommandCenterScreen.java
gui/EntityBrowserScreen.java
gui/HudEditorScreen.java
gui/HudManager.java
gui/KeybindScreen.java
gui/ProfileScreen.java
gui/WaypointScreen.java
mixin/KeyboardMixin.java
render/esp/WorldEspRenderer.java
```

The most concentrated change is `input-records`: nine of the eighteen files are screens
whose input callbacks moved to the typed `Click` / `KeyInput` / `CharInput` records at
`1.21.9`. That is one adapter call site per file, not a rewrite.

## 5. Where the break boundaries fall

```
1.21.1  |  1.21.2 ................ 1.21.8  |  1.21.9 ... 1.21.11  ||  26.1 ... 26.3
   ^                                              ^                        ^
MatrixStack                                input records,            namespace
push/pop rename                            entity pos, Fabric        migration
                                           events, render state      (Yarn -> Mojang)
```

- `1.21.1` is the only version needing `MatrixStackAdapter`.
- `1.21.2`–`1.21.8` need everything except `MatrixStackAdapter`.
- `1.21.9`–`1.21.11` are the current source, no shims.
- `26.x` needs no per-call shim but does need the namespace layer.

## 6. Verification

The `1.21.1` column is not theoretical: a real `./gradlew -PmcVersion=1.21.1 compileJava`
produced exactly these errors (101 diagnostics across 18 files). The raw evidence is in
`docs/phase9/verification.md`.
