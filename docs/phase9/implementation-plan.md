# Phase 9 — Implementation Plan

> Status: **scaffolding landed, adaptation layer in progress.** This document states exactly
> what exists, what is verified, and what remains. Nothing here is claimed as done without a
> command or test that proves it.

## 1. What is done

### 1.1 Version inventory (done, verified)

`build-system/generate_inventory.py` queries Mojang piston-meta, Fabric meta and the Fabric
Maven and writes `build-system/versions.json` — 16 stable releases, `1.21.1` … `26.3`, with
the Java requirement, mapping scheme, Loom plugin and Fabric API coordinate for each.
See `docs/phase9/version-inventory.md`.

### 1.2 Compatibility matrix (done, verified)

`build-system/generate_manifest.py` cross-references the inventory with the breaking-change
register (`build-system/breaking-changes.json`) and writes
`build-system/compatibility-manifest.json`. Every register entry was observed in a real
compile, not guessed. See `docs/phase9/compatibility-matrix.md`.

### 1.3 Inventory-driven build (done, verified)

`build.gradle` no longer hard-codes any Minecraft, mapping, Fabric API, Java or Loom version.
It reads `versions.json` in its `buildscript {}` block and selects:

- the Loom plugin classpath (`fabric-loom` vs the no-remap `net.fabricmc.fabric-loom`),
- the Java toolchain (21 vs 25),
- the mapping and Fabric API coordinates.

Target selection:

```bash
./gradlew build                    # default: inventory.primary (1.21.11), the verified baseline
./gradlew -PmcVersion=1.21.1 build # any version in range
./gradlew -PmcVersion=26.3 build
```

**Verified:** the default build is green — `./gradlew build` → BUILD SUCCESSFUL, 700 tests,
0 failures, artifact `logvex-crystal-1.21.11-1.0.0.jar`.

### 1.4 Version-adapter SPI (scaffolded)

`build-system/version-adapters/` holds the interface contracts for the eight adapters named
in the register. The interface is what the screens/renderers will call; the implementation is
selected per family.

## 2. What remains

### 2.1 The 26.x namespace layer (largest item)

The 26.x client jar uses Mojang official names; the source uses Yarn names. Making the tree
compile for 26.x means one of:

1. **Abstraction package (recommended).** A `platform/` package exposes the ~30 Minecraft
   types the mod uses under the mod's own names, with a Yarn implementation and a Mojang
   implementation selected by the build. 66 of 347 files import Minecraft; those imports
   change, the logic does not.
2. **Generated mapping layer.** A script rewrites Yarn names to Mojang names at build time
   (a source transform). Faster to start, harder to maintain.

The recommendation is (1): it is compiler-checked and needs no code generation.

### 2.2 The eight 1.21.x shims

Straightforward, bounded by the register: one adapter call site per affected file. The
`input-records` change is the widest (nine screens) but each is a signature change.

### 2.3 Multi-artifact build

Because one jar cannot serve both families (§1 of the inventory doc), the release job must
build and publish one artifact per family, and — where a shim cannot cover a break — one per
release band. A Gradle matrix task is the natural home; it is not written yet.

## 3. Artifact strategy

| Family | Versions | Artifact | Java | Status |
| --- | --- | --- | --- | --- |
| mc-1.21.x | 1.21.9 … 1.21.11 | `logvex-crystal-1.21.11-…jar` | 21 | **built, tested** |
| mc-1.21.x | 1.21.1 … 1.21.8 | (same source + shims) | 21 | shims pending |
| mc-26.x | 26.1 … 26.3 | (namespace layer) | 25 | namespace layer pending |

## 4. Honest status

- The **inventory and matrix are complete and reproducible** — the "which versions, what
  breaks where" question is answered with primary-source evidence.
- The **build can target any version in range** and the baseline is green.
- The **adaptation layer is not written**, so `1.21.1` and `26.3` do **not** yet compile.
  Their real compile output is recorded in `docs/phase9/verification.md` as the work list.
- Phase 9 is therefore **in progress**, not complete.
