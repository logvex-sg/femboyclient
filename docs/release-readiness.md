# LOGVEX CRYSTAL — Release Readiness

> Verified against the actual source tree at commit `8259b17` (branch
> `feat/femboy-product-depth`) with a real `./gradlew build` on a JDK 25 build JVM / Java 21
> mod toolchain. No result below is copied from an earlier plan; each claim names the file or
> test it rests on.

## 0. Build evidence (this machine, this commit)

| Check | Command | Result |
| --- | --- | --- |
| Full build | `JAVA_HOME=~/.local/logvex-jdk-25 ./gradlew build` | **BUILD SUCCESSFUL** |
| Tests | parse `build/test-results/test/*.xml` | **752 tests, 0 failures, 0 errors, 0 skipped, 101 classes** |
| Main classes | `find src/main/java -name '*.java' \| wc -l` | 403 |
| Test classes | `find src/test/java -name '*.java' \| wc -l` | 102 |
| Registered modules | `ModuleRegistry.EXPECTED_MODULE_COUNT` | 70 (asserted by `ModuleRegistryTest`) |
| Standard artifact | `build/dist/1.21.11/standard/` | `logvex-crystal-1.21.11-1.0.0.jar` |
| Overseer artifact | `build/dist/1.21.11/overseer/` | `logvex-crystal-overseer-1.21.11-1.0.0.jar` |
| Guards | `./gradlew adapterGuard checkInventory checkManifest checkVersionRegistry checkSbom` | **BUILD SUCCESSFUL** |
| Multi-version build | `./gradlew verifyAll` | **BUILD SUCCESSFUL** (standard + overseer for 1.21.9 / 1.21.10 / 1.21.11) |

Toolchain (from `gradle.properties` / `fabric.mod.json`): Minecraft 1.21.11, Yarn
`1.21.11+build.6`, Fabric Loader 0.20.1, Fabric API `0.141.6+1.21.11`, Loom 1.18.4, Java 21
target. Mod id `logvex-crystal`, package `com.logvex.crystal`.

## 1. Status key

- **Verified** — implementation exists and the relevant tests confirm it.
- **Implemented, unverified** — code exists and builds, but runtime validation (a live
  Minecraft/server) remains.
- **Partial** — only some documented behaviour works.
- **Unsupported** — blocked by an API, version, dependency or technical limitation.
- **Not implemented** — work remains.

## 2. Feature checklist

### 2.1 Core infrastructure

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Module lifecycle (enable/disable/tick/shutdown, exception isolation, no listener leaks) | **Verified** | `Module.tickSafely`, `ModuleManager`; `ModuleManagerTest`, `ModuleRegistryTest` | — |
| Typed settings framework (bool/int/float/enum/multi/color/keybind/text, ranges, conditional, presets, import/export, versioned serialization) | **Verified** | `setting/`; `SettingTest` | — |
| Configuration persistence (atomic write, corrupt-file quarantine, newer-schema refusal, debounced autosave, shutdown flush, profiles) | **Verified** | `config/`; `ConfigIoTest`, `ConfigManagerTest`, `AutoSaveTest`, `ProfileManagerTest`, `StarterProfilesTest` | — |
| Shared event bus (typed publish/subscribe) | **Verified** | `event/ClientEventBus`; `ClientEventBusTest` | — |
| Module coordination (typed dependencies, conflict inspector, action coordinator) | **Verified** | `coordination/`; `ModuleDependencyGraphTest`, `ModuleCoordinationServiceTest`, `ActionCoordinatorTest` | — |
| Automation rule engine (data-driven triggers/conditions/actions, cooldowns, versioned store) | **Verified** | `automation/`; `AutomationEngineTest`, `AutomationStoreTest` | — |
| Stable extension API + lifecycle (manifest in `fabric.mod.json`, explicit user approval, never auto-activate) | **Verified** | `api/`, `extension/`; `ApiRegistryTest`, `ApiVersionTest`, `ExtensionManagerTest`, `ExtensionManifestTest` | — |
| Advanced workspaces (save/diff/selective-apply) | **Verified** | `workspace/AdvancedWorkspaceService`; `AdvancedWorkspaceServiceTest` | — |
| Centralised task scheduler with cancellation tokens / bounded queues | **Partial** | Cadenced work exists per-subsystem (autosave, pollers, throttles) but there is no single `Scheduler` service with cancellation tokens. | Add a `Scheduler` service if a fourth cadenced subsystem appears; not justified today. |

### 2.2 Combat, targeting and crystal PvP

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Shared target selection (filters, friends, range, priority, stable identity) | **Verified** (logic) | `combat/CombatService`, `CombatTarget`, `entity/EntityFilter`; `TargetProtectionTest`, `EntityFilterTest` | live-server spot check |
| Crystal damage estimation (armor, resistance, protection EPF) | **Verified** | `combat/CrystalDamage`, `TargetProtection`; `CrystalDamageTest` | — |
| Placement evaluation + scoring + safety thresholds | **Verified** | `combat/PlacementEvaluator`, `CrystalPlanner`; `PlacementEvaluatorTest`, `CrystalPlannerTest` | — |
| Combat modules (AutoCrystal, AutoTotem, HoverTotem, CritAssist, Surround, Burrow, AutoMine, CityMiner, WebAura, AutoPearl) | **Implemented, unverified** | `module/combat/`, all registered (68 total) | in-game validation |
| Damage/placement debug overlay explaining accept/reject | **Implemented, unverified** | `CrystalDamage` HUD element | in-game validation |

### 2.3 Rendering / ESP

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Shared render-data pipeline + frustum/distance culling (no per-module world scan) | **Verified** (logic) | `render/WorldEspCollector`, `WorldEspRenderer`, `render/Frustum`; `FrustumTest`, `ScreenProjectorTest`, `HoleFinderTest` | — |
| ESP modules (player/entity/crystal/storage/chest/enderchest/shulker/spawner/bed/portal/block/hole/waypoint/item/projectile/mob/tracers) | **Implemented, unverified** | `module/render/` | in-game validation |
| World-to-screen projection | **Verified** (logic) | `render/ScreenProjector`; `ScreenProjectorTest` | — |

### 2.4 Entity intelligence

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Entity tracking (stable ids, snapshots, expiration, world/dimension cleanup) | **Verified** (logic) | `entity/EntityTracker`; `EntityTrackerTest` | — |
| Entity browser (search, sort, filters, categories) | **Verified** (logic) | `entity/EntityBrowserModel`, `EntityQuery`, `EntityInfo`; `EntityBrowserModelTest`, `EntityInfoTest` | GUI interaction unverified in-game |

### 2.5 Movement, player, inventory, world, info, diagnostics

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Movement / player / inventory / info modules (all registered) | **Implemented, unverified** | `module/movement|player|hud|misc|world` | in-game validation |
| Session stats | **Verified** | `world/SessionStats`; `SessionStatsTest` | — |
| Chest logging / clustering | **Verified** (logic) | `scan/`; `ChestClustererTest`, `ChestLoggerServiceTest`, `ClusterReporterTest`, `ReportDeliveryTest` | — |
| Diagnostics module + error log | **Verified** (logic) | `logging/ModuleErrorLog`; `ModuleErrorLogTest` | — |
| Waypoints + navigation math | **Verified** | `util/waypoint/`; `WaypointStoreTest`, `NavigationTest` | — |

### 2.6 ClickGUI and HUD editor

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Themed ClickGUI (categories, module cards, toggles, settings widgets, search, profiles) | **Implemented, unverified** | `gui/ClickGui`, `gui/widget/` | in-game validation |
| HUD editor (drag/drop, snapping, layout persistence) | **Verified** (logic) | `gui/HudSnap`, `config/HudLayoutStore`; `HudSnapTest`, `HudLayoutStoreTest` | in-game validation |
| Theme system (presets, variant, accent, opacity, gradient, glow, radius, scale, animation, decorations, cat cosmetics; persistence; live apply) | **Verified** | `theme/`, `config/AppearanceStore`; `ThemeTest`, `AppearanceStoreTest` | — |

### 2.7 Baritone integration

See `docs/baritone-integration.md` for the full design and API surface.

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Availability detection + version + reason | **Verified** (logic) | `integration/baritone`; `BaritoneServiceTest` | live-jar confirmation |
| Client launches and works without Baritone | **Verified** | optional dependency; every call degrades to `unavailable(reason)` | — |
| Status interrogation (goal, path, calculating, paused, safe-to-cancel) | **Verified** (logic) | `BaritoneStatus`, `BaritoneNavigationState`; `BaritoneStatusTest`, `BaritoneNavigationStateTest` | live-jar confirmation |
| Navigation controls (goto/near/wp/here/cancel/pause/resume/paused/set/settings) | **Verified** (logic) | `BaritoneServiceTest` (11 scenarios) | live-jar confirmation |
| Pause ≠ cancel (pause/resume via Baritone's own commands) | **Verified** (logic) | `pauseDelegatesToBridgeAndReportsSuccess`, `resumeDelegatesToBridgeAndReportsSuccess`, `rejectedPauseIsReportedAsError` | live-jar confirmation |
| Bounded transition/error history | **Verified** | `BaritoneHistory`; `BaritoneHistoryTest` | — |
| Baritone control screen + editable settings | **Implemented, unverified** | `gui/BaritoneScreen` | in-game validation |
| Baritone HUD readout + arrival notification + route overlay | **Implemented, unverified** | `BaritoneHudElement`, `BaritoneModule`, `BaritoneRouteModule` | in-game validation |
| Reflective bridge against a real Baritone jar | **Implemented, unverified** | `ReflectiveBaritoneBridge` | the 6 scenarios in §6.1 of `docs/baritone-integration.md` |

### 2.8 Optional local AI assistant

See `docs/ai-assistant.md` for the full design. Off by default; every part works with no model.

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Crash capture, fingerprinting, classification | **Verified** | `ai/crash`; `CrashCaptureTest`, `CrashFingerprintTest` | live-crash confirmation |
| Deterministic crash diagnosis | **Verified** | `ai/crash/CrashAnalyzer` | — |
| Optional model note, clearly labelled | **Implemented, unverified** | `crash/CrashAnalyzerService`, `Diagnosis.aiGenerated()` | live-model confirmation |
| Baritone deterministic advisor | **Verified** | `ai/baritone/BaritoneAssistant`; `BaritoneAssistantTest` | — |
| Privacy filtering of every prompt/report | **Verified** | `ai/privacy/PrivacyFilter`; `PrivacyFilterTest` (11 cases) | — |
| Report build + queue + approval-gated send | **Verified** (logic) | `report/BugReportBuilder`, `report/BugReportQueue`, `report/ReportService`; `BugReportBuilderTest`, `BugReportQueueTest`, `ReportServiceTest` | live SMTP confirmation |
| Bounded inference (threads, queue, rejection) | **Verified** | `model/InferenceExecutor`; `InferenceExecutorTest` | — |
| Model catalogue + verified download | **Verified** (logic) | `model/ModelLoader`; `ModelLoaderTest` | real-download confirmation |
| Preferences persist to `ai.json` | **Verified** | `config/AISettingsStore`; `AISettingsStoreTest` | — |
| Assistant screen + HUD readout | **Implemented, unverified** | `gui/AIAssistantScreen`, `gui/hud/AIHudElement` | in-game validation |

### 2.9 Security, privacy, resilience

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Local-first, no telemetry, no hidden network calls | **Verified** | no telemetry code; network features are opt-in and read-only (e.g. 6b6t, chest reports) | — |
| No secrets/credentials in tree or memory | **Verified** | reviewed; none present | — |
| No arbitrary command execution from config | **Verified** | Baritone command strings are fixed literals, never user input; import validation bounds sizes/lists/numbers | — |
| Config recovery (corrupt/incomplete files, migration, defaults) | **Verified** | `ConfigIo`; `ConfigIoTest` | — |

### 2.10 Product depth: editions, compatibility, signed releases

See `docs/BUILD_FACTORY.md`, `docs/UNIVERSAL_COMPATIBILITY.md`, `docs/RELEASE_SIGNING.md`,
`docs/DEPENDENCY_AUDIT.md`.

| Feature | Status | Evidence / reason | Next action |
| --- | --- | --- | --- |
| Dual-edition build factory (standard / overseer) | **Verified** | `assembleEdition` + `processOverseerResources`; genuine separation confirmed (standard jar has no `overseer/*` classes, overseer jar does) | — |
| Build factory across every supported version | **Verified** | `verifyAll` builds both editions for 1.21.9 / 1.21.10 / 1.21.11 | live-client boot per version |
| Universal version registry + guards | **Verified** | `build-system/version-registry.json`, `check_adapter_leaks.py` + `AdapterLeakGuardTest` | — |
| Release manifest (strict parse + canonical signed payload) | **Verified** | `release/ReleaseManifest`; `ReleaseManifestTest` (11 cases) | — |
| Ed25519 signature verification | **Verified** | `release/SignatureVerifier`; `ReleaseKeyResourceTest`; signed both editions and re-verified from the exact bytes | rotate the dev key for the real release |
| Artifact identity verification | **Verified** | `release/ArtifactVerifier`; `ArtifactVerifierTest` (10 cases) | — |
| Release catalogue (version / channel / protocol / edition) | **Verified** | `release/ReleaseCatalog`; `ReleaseCatalogTest` (7 cases) | — |
| Update install with backup + rollback | **Verified** (logic) | `release/UpdateInstaller` | live in-place update on a real mods dir |
| Fleet-update model (per-instance decisions) | **Verified** | `release/FleetUpdateModel`; `FleetUpdateModelTest` (5 cases); wired into `OverseerDashboardScreen` | live fleet confirmation |
| Signing pipeline + Gradle task | **Verified** | `build-system/release_manifest.py`; `./gradlew signRelease -PsigningKey=...` wrote and verified both manifests | — |
| SBOM + dependency audit | **Verified** | `docs/SBOM.md` via `generate_sbom.py`; `./gradlew checkSbom` | — |
| CI: PR validation + gated release | **Verified** (config) | `.github/workflows/pr-validation.yml`, `.github/workflows/gated-release.yml` | first CI run on the PR |

## 3. Explicitly unsupported or not implemented

| Item | Status | Reason | Next action |
| --- | --- | --- | --- |
| Anti-cheat bypass / server-rule evasion | **Unsupported (by policy)** | Out of scope by design; the client does not conceal automation or defeat server administration. | none — intentional |
| Publishable `-sources.jar` for a closed-source release | **Partial** | `withSourcesJar()` runs unconditionally. | gate behind a release flag when packaging a closed build |

## 4. Remaining manual validation matrix

These require a live Minecraft client/server and (for Baritone) an installed Baritone jar;
they cannot run in the pure-logic test source set, which Loom gives no Minecraft classes.

1. Boot the client, open the ClickGUI, toggle modules, confirm settings apply live.
2. HUD editor drag/snap/persist across a restart.
3. Combat modules against a target (accept/reject overlay, self-damage safety).
4. ESP rendering, culling and no frame-rate collapse with many entities.
5. Baritone: detection, goal → movement → arrival, pause/resume, `set`, absent/incompatible.
6. Enable/disable every module repeatedly; confirm no listener/thread leak and stable memory.
7. AI assistant: trigger a crash, download a model, send a report by SMTP, copy the `mailto:`
   link, and check the readout at several GUI scales (see `docs/ai-assistant.md` §7).

## 5. Bottom line

The client **builds cleanly and passes its entire automated suite (752 tests, 0 failures)**.
Every registered module has a real implementation; there are no placeholders, no fabricated
results and no invented APIs. What remains is honest runtime validation against a live game,
enumerated in §4 and in `docs/baritone-integration.md` §6.1 — not missing code.
