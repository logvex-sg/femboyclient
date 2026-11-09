# Phase 9 — Version Inventory

> Machine-generated from authoritative upstream metadata by
> `python3 build-system/generate_inventory.py` (writes `build-system/versions.json`).
> Do not hand-edit the table; regenerate it. Every value below was read from Mojang
> piston-meta, Fabric meta or the Fabric Maven, then confirmed by a real Gradle resolution.

## 1. Range

One comprehensive compatibility range:

- **Minimum:** Minecraft `1.21.1` (released 2024-08-08)
- **Maximum:** Minecraft `26.3` (released 2026-09-15)
- **Stable releases in range:** 16
- **Fabric Loader:** `0.20.1` (latest release at generation time)

This is a single range, not two parallel versions. The releases fall into two build
*families* because Mojang changed both the Java requirement and the obfuscation policy
at the 1 → 26 boundary (see §3).

## 2. The 16 releases

| Version | Family | Java | Mappings scheme | Fabric API | Supportable | Changes needing a shim | Namespace migration |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `1.21.1` | mc-1.21.x | Java 21 | yarn | `0.116.17+1.21.1` | yes | 8 | no |
| `1.21.2` | mc-1.21.x | Java 21 | yarn | `0.106.1+1.21.2` | yes | 7 | no |
| `1.21.3` | mc-1.21.x | Java 21 | yarn | `0.114.1+1.21.3` | yes | 7 | no |
| `1.21.4` | mc-1.21.x | Java 21 | yarn | `0.119.4+1.21.4` | yes | 7 | no |
| `1.21.5` | mc-1.21.x | Java 21 | yarn | `0.128.2+1.21.5` | yes | 7 | no |
| `1.21.6` | mc-1.21.x | Java 21 | yarn | `0.128.2+1.21.6` | yes | 7 | no |
| `1.21.7` | mc-1.21.x | Java 21 | yarn | `0.129.0+1.21.7` | yes | 7 | no |
| `1.21.8` | mc-1.21.x | Java 21 | yarn | `0.136.1+1.21.8` | yes | 7 | no |
| `1.21.9` | mc-1.21.x | Java 21 | yarn | `0.134.1+1.21.9` | yes | 0 | no |
| `1.21.10` | mc-1.21.x | Java 21 | yarn | `0.138.4+1.21.10` | yes | 0 | no |
| `1.21.11` | mc-1.21.x | Java 21 | yarn | `0.141.6+1.21.11` | yes | 0 | no |
| `26.1` | mc-26.x | Java 25 | mojang-names-in-jar | `0.145.1+26.1` | yes | 0 | yes |
| `26.1.1` | mc-26.x | Java 25 | mojang-names-in-jar | `0.145.4+26.1.1` | yes | 0 | yes |
| `26.1.2` | mc-26.x | Java 25 | mojang-names-in-jar | `0.155.3+26.1.2` | yes | 0 | yes |
| `26.2` | mc-26.x | Java 25 | mojang-names-in-jar | `0.161.0+26.2` | yes | 0 | yes |
| `26.3` | mc-26.x | Java 25 | mojang-names-in-jar | `0.165.0+26.3` | yes | 0 | yes |

- **Supportable = yes** means: Fabric lists the version, a Fabric API release exists, and
  either a Yarn mapping or a deobfuscated client jar is available.
- **Changes needing a shim** counts the breaking-change register entries that apply to that
  version (see `docs/phase9/compatibility-matrix.md`).
- **Namespace migration** marks the 26.x family, whose client jar uses Mojang official
  names rather than Yarn (see §3).

## 3. The two build families

The 1 → 26 boundary is a hard split in the toolchain, not a patch bump.

| Property | mc-1.21.x (`1.21.1` … `1.21.11`) | mc-26.x (`26.1` … `26.3`) |
| --- | --- | --- |
| Java runtime | 21 (`java-runtime-delta`) | 25 (`java-runtime-epsilon`) |
| Obfuscation | Obfuscated; Yarn mappings published | **Ships deobfuscated** under Mojang official names |
| Yarn mappings | available (e.g. `1.21.11+build.6`) | **none** |
| Intermediary | available | **none** (Maven artifact 404s) |
| Mojang official mappings | `client_mappings` published | **none** — none needed, the jar is already named |
| Loom plugin | `fabric-loom` (remap) | `net.fabricmc.fabric-loom` (no-remap) |
| Representative class name | `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |

Evidence for the 26.x row: the `26.3` client jar contains `net/minecraft/client/Minecraft.class`,
`net/minecraft/client/gui/screens/Screen.class`, `net/minecraft/core/BlockPos.class`,
`net/minecraft/world/phys/Vec3.class` — i.e. Mojang's own names, with no mappings applied.
`https://maven.fabricmc.net/net/fabricmc/intermediary/26.3/intermediary-26.3-v2.jar` returns 404,
and `26.3` has no `client_mappings` in its piston-meta manifest.

## 4. Consequence for the mod

Because family A is written against Yarn names and family B against Mojang names, the
*same compiled classes cannot load on both families*. One physical jar therefore cannot
serve both. The project ships **one artifact per build family** (and, once the shims land,
one per release within a family if a break needs pinning). See
`docs/phase9/implementation-plan.md` §"Artifact strategy".

## 5. Reproducing this inventory

```bash
python3 build-system/generate_inventory.py          # regenerate versions.json
python3 build-system/generate_inventory.py --check  # CI: fail if stale
```

`versions.json` is the single source of truth the Gradle build reads; nothing in the build
hard-codes a Minecraft, mapping, Fabric API, Java or Loom version.
