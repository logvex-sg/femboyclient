# Version Adapters (SPI)

This directory specifies the compatibility layer that lets one source tree build across the
whole Phase 9 range. It is the contract; the implementations are the pending work described
in `docs/phase9/implementation-plan.md`.

## Why an adapter layer

Two independent axes break the source across the range (see
`docs/phase9/compatibility-matrix.md`):

1. **API drift** inside the 1.21.x family — signatures and packages move.
2. **Namespace migration** at the 1 → 26 boundary — Yarn names become Mojang official names.

Axis 1 is absorbed by small adapters. Axis 2 cannot be: the *type names themselves* differ,
so it needs a facade package whose implementation is chosen per family.

## The facade rule

Screens and renderers must not import Minecraft types directly where an adapter exists.
Instead they call the mod's facade, which is implemented once per family:

```
com.logvex.crystal.compat            <- facade (JDK-only signatures where possible)
com.logvex.crystal.compat.mc121x     <- Yarn implementation (Java 21)
com.logvex.crystal.compat.mc26x      <- Mojang implementation (Java 25)
```

The build selects the implementation from `build-system/versions.json` (`family` field), the
same way `build.gradle` selects the Loom plugin.

## Adapters (from `build-system/breaking-changes.json`)

| Adapter | Absorbs | Call sites |
| --- | --- | --- |
| `InputAdapter` | `Click` / `KeyInput` / `CharInput` records vs loose `mouseClicked` / `keyPressed` / `charTyped` | 9 screens, `mixin/KeyboardMixin` |
| `MatrixStackAdapter` | `push()/pop()` vs `pushMatrix()/popMatrix()`, `scale(x,y,z)` vs `scale(x,y)` | `gui/ClickGui`, `gui/HudManager` |
| `EntityPosAdapter` | `getEntityPos()` vs `getPos()` | `combat/CombatService` |
| `HudEventsAdapter` | `v1.hud.HudElementRegistry` vs `HudRenderCallback` | `client/CrystalClient` |
| `WorldEventsAdapter` | `v1.world.WorldRenderEvents` vs legacy package | `client/CrystalClient`, `render/esp/WorldEspRenderer` |
| `ResourceEventsAdapter` | `v1.resource` + new `ReloadListener.reload` signature | `client/CrystalClient`, `assets/AssetRegistry` |
| `EntityRenderAdapter` | `*RenderState` + `OrderedRenderCommandQueue` vs direct model context | `cosmetic/render/*`, `client/CrystalClient` |
| `MiscApiAdapter` | keybind category, tick delta, screenshot, key polling, texture ctor, blur, pipelines | `client/CrystalClient`, `gui/ClickGui`, `assets/*` |

## Selection

Each adapter exposes a static factory resolved once at class-load:

```java
Compat.input()   // -> InputAdapter, one of the two family implementations
```

The family implementation is placed on the compile path by the build, so the unused
implementation never ships. No reflection, no runtime version sniffing: the choice is made
when the artifact is built, and a wrong choice fails the compile, not the player.

## Status

- The facade and the two implementations are **not written yet**.
- The breaking-change register and the affected-file lists are complete and verified, so the
  work is bounded: one adapter call site per affected file.
