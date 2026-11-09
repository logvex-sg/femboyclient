# Build Factory

LOGVEX CRYSTAL produces two genuinely different artifacts from one source tree:

| Edition | Source root | Feature manifest | Archive name |
| --- | --- | --- | --- |
| Standard | `src/main/java` | `src/main/resources/logvex-features.txt` (empty) | `logvex-crystal-<mc>-<ver>.jar` |
| Overseer | `src/main/java` + `src/overseer/java` | `src/overseer/resources/logvex-features.txt` | `logvex-crystal-overseer-<mc>-<ver>.jar` |

The two are not a rename of one jar. The Overseer source directory and manifest are only on the
compile path when the edition is `overseer`, so the standard jar contains no administration classes
and `assembleEdition` fails the build if either artifact contains the wrong feature set.

## Selecting an edition and version

The edition is a property of the whole Gradle invocation, because it changes the compile path:

```
-Pvariant=<standard|overseer>   edition (default: standard)
-PmcVersion=<x>                 Minecraft target (default: inventory.primary, 1.21.11)
```

## Tasks

All build-factory tasks are in the `logvex` group (`./gradlew tasks --group=logvex`).

| Task | What it does |
| --- | --- |
| `buildStandard` | Assembles, validates and stages the Standard edition for the default version. |
| `buildOverseer` | Assembles, validates and stages the Overseer edition for the default version. |
| `buildAll` | Both editions for the default version. |
| `buildVersion` | One edition for `-PminecraftVersion=<x>`; `-Pvariant` picks the edition. |
| `assembleEdition` | The inner task: assemble → validate contents → write metadata → copy to `dist/`. |
| `adapterGuard` | Fails if shared code references a version-specific symbol. |
| `checkVersionRegistry` | Fails if `build-system/version-registry.json` is stale. |
| `verify_<v>_<edition>` | Builds one edition for one supported version. |
| `verifyAll` | `adapterGuard` + `checkVersionRegistry` + `test` + every supported version × both editions. |

`buildStandard`, `buildOverseer` and `buildVersion` re-invoke the Gradle wrapper with the right
properties. This is deliberate: Loom's mapping and toolchain selection happens in `buildscript {}`,
so an edition switch has to be a fresh build rather than a task graph in one JVM.

## Examples

```bash
./gradlew buildStandard
./gradlew buildOverseer
./gradlew buildAll
./gradlew buildVersion -PminecraftVersion=1.21.9 -Pvariant=overseer
./gradlew verifyAll
```

## Output layout

Each build stages its result under a predictable directory keyed by Minecraft version and edition:

```
build/dist/<minecraft>/<edition>/
  logvex-crystal[-overseer]-<minecraft>-<version>.jar
  build-metadata.json
```

`build-metadata.json` records the edition, client version, Minecraft version, adapter band, Java and
loader requirements, Fabric API coordinate, artifact name and size, SHA-256, and the feature class
names the manifest listed. It is the input to the release manifest system (see
`docs/RELEASE_PROCESS.md`).

## What `assembleEdition` validates

1. The expected jar exists.
2. Overseer edition: the artifact contains `com/logvex/crystal/overseer/OverseerFeature.class` and
   the feature manifest names it.
3. Standard edition: the artifact contains no `overseer/` classes and the feature manifest is empty
   of feature lines.
4. The SHA-256 is computed over the staged bytes.

A violation fails the build, so a mislabelled or mis-populated artifact cannot reach `dist/`.

## How the feature SPI keeps the core clean

The core never imports the Overseer class. `logvex-features.txt` lists fully-qualified
`ClientFeature` class names; `ClientFeatureHost` reads the file at startup and instantiates whatever
it names. The standard edition ships the file empty, so it links no administration code. See
`src/main/java/com/logvex/crystal/variant/` and `docs/UNIVERSAL_COMPATIBILITY.md`.

## Requirements

- A Java 25 JVM to run Loom 1.18.4 (`./scripts/setup-jdk.sh`).
- `python3` for the version-registry and adapter-guard tasks.
- Network on the first build, to resolve Loom, Yarn mappings and Fabric API.
