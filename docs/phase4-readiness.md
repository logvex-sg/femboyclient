# LOGVEX CRYSTAL — Phase 4 Readiness (Phase 0 audit)

> Verified against the actual source tree at commit `490d970` on branch
> `feat/femboy-product-depth`, with a real `./gradlew build` (JDK 25 build JVM / Java 21
> toolchain). Build result: **BUILD SUCCESSFUL, 446 tests, 0 failures, 0 errors** (59 test
> classes, 241 main classes). Every claim below was re-checked against the files named; no
> result is copied from a previous plan.

> **Update (post-implementation).** The Phase 4 vertical slices described in section 4 have since
> landed on the same branch: the stable API (`api/`) and the extension lifecycle
> (`extension/`) are built and tested, along with advanced workspaces (`workspace/`). The
> current tree builds **BUILD SUCCESSFUL, 481 tests, 0 failures** across 64 test classes and
> 258 main classes. The subsystem table in section 1 describes the state *before* that work;
> see `docs/extension-api.md`, `docs/extension-security.md` and `docs/extension-development.md`
> for the API as actually built.

## 0. How this was verified

| Check | Command | Result |
| --- | --- | --- |
| Full build + tests | `JAVA_HOME=~/.local/logvex-jdk-25 ./gradlew build` | BUILD SUCCESSFUL |
| Test totals | parse `build/test-results/test/*.xml` | 446 tests, 0 failures, 0 errors, 59 classes |
| Main classes | `find src/main/java -name '*.java' \| wc -l` | 241 |
| Test classes | `find src/test/java -name '*.java' \| wc -l` | 59 |
| Registered modules | `ModuleRegistry.EXPECTED_MODULE_COUNT` | 68, asserted by `ModuleRegistryTest` |
| Toolchain | `gradle.properties` | MC 1.21.11, Yarn `1.21.11+build.6`, loader 0.19.5, Fabric API `0.141.6+1.21.11`, Loom 1.18.4, Java 21 target |

## 1. Subsystem state

### 1.1 Module lifecycle and registration — **Verified**

- `module/Module.java`: identity, typed settings, dependency/conflict metadata, strict
  lifecycle (`onEnable`/`onDisable`/`onTick`/`onShutdown`), exception isolation in
  `tickSafely()`, throttled error reporting, `enabledMillis()`, `lastTickNanos()`.
- `module/ModuleManager.java`: registration with duplicate detection, ordered ticking,
  `firstMissingDependency`, `firstActiveConflict`, `canEnable`, keybind dispatch and
  keybind-conflict resolution.
- `module/ModuleRegistry.java`: 68 modules registered in display order, count pinned by test.
- `module/ModuleContext.java`: injected service surface; services never depend on modules.

**Phase 4 need:** there is no public, versioned API facade. A first-party module reaches
services through `ModuleContext`, but an extension has no supported entry point, and there is
no capability discovery or compatibility contract.

### 1.2 Typed settings — **Verified**

- `setting/`: Boolean/Int/Double/String/Color/Enum/Keybind settings with validators,
  `visibleWhen` conditions, a global change hook and JSON codecs.

**Phase 4 need:** settings are owned by a `Module`; there is no way for an extension to own a
settings group that persists independently of a module.

### 1.3 Event dispatch — **Verified**

- `event/ClientEventBus.java`: bounded, deduplicating, failure-isolated bus with per-kind
  filters, subscription handles and per-listener stats.
- `event/ClientEventFeed.java`: the single producer (world/dimension, entity spawn/despawn,
  tracked health/armour, combat-target, Baritone, player health, module toggles).
- `automation/AutomationEngine.java`: consumes the bus for `OnEvent` triggers.

**Phase 4 need:** the bus is real and reusable, but subscriptions are keyed by listener
identity only; there is no scoped subscription group that an extension can unregister in one
call. This is addressed in Phase 1.3.

### 1.4 Dependency and conflict handling — **Verified**

- `coordination/`: `ModuleDependency` (typed: module/service/integration/capability/mode),
  `ModuleDependencyGraph` (cycle detection, keybind-clash grouping, active-conflict detection),
  `ConflictReport`, `ModuleCoordinationService`, `ClientDependencyContext`, `ActionCoordinator`.
- `; conflicts` command surfaces the report.

**Phase 4 need:** the typed dependency model is a natural fit for extension-declared
capabilities; nothing needs to be rebuilt.

### 1.5 Entity snapshots — **Verified**

- `entity/EntityQuery`, `EntityTracker` (diffing, spawn/despawn events, bounded history),
  `EntityInfo` (immutable snapshot record).

**Phase 4 need:** safe to expose read-only via the API; do not hand extensions the tracker.

### 1.6 Rendering and HUD — **Verified**

- `gui/HudManager.java`: element registration, layout, visibility, overlays, world-ESP
  providers, anchors, editor overlay.
- `gui/HudElement.java`: side-effect-free renderer interface with column/size/anchor contract.

**Phase 4 need:** registration exists but has no unregister path for a dynamically added
element beyond `removeOverlay`/`removeWorldEsp`; extension HUD elements need a scoped handle.

### 1.7 ClickGUI — **Verified**

- `gui/ClickGui.java`: sidebar of `Category` values plus special pages
  (`MODULES, APPEARANCE, FAVORITES, RECENT`), search, module cards, expandable settings,
  keybind/colour editors, dropdowns.

**Phase 4 need:** a new special page (`EXTENSIONS`, and later a developer workspace) must be
added to the `Page` enum and the sidebar, keeping draw and click paths in sync (the existing
code centralises button geometry for exactly this reason).

### 1.8 Configuration and profiles — **Verified**

- `config/`: `ConfigIo` (atomic write, `.bak`, malformed tolerance, automatic backup recovery
  on read via `recoverFromBackup`/`isCorrupt`, recovery + restore listeners), `ConfigManager`
  (schema-versioned `modules.json`, newer-schema refusal), `ConfigPaths`,
  `ProfileService`/`ProfileManager`, and stores for appearance/hud/friends/waypoints/
  favorites/notifications/context/automation/container-labels.

**Phase 4 need:** extension settings need their own store with the same guarantees; profile
migration and unknown-field preservation must be extended for the new keys.

### 1.9 Automation rules — **Verified**

- `automation/`: sealed `Trigger`/`Condition`/`Action` vocabulary, `AutomationRule`,
  `AutomationEngine` (enable, per-rule cooldown, per-minute rate limit, world requirement,
  bounded history), `AutomationStore` (versioned atomic JSON), `ClientAutomationContext`,
  `; automation` command.

**Phase 4 need:** none. This is a completed vertical slice and is reused as-is.

### 1.10 Baritone integration — **Verified**

- `integration/baritone/`: `BaritoneService`, `BaritoneStatus`, `BaritoneGoal`,
  `ReflectiveBaritoneBridge` (reflection only; no compile dependency), plus command, module,
  HUD and screen.

**Phase 4 need:** expose only a narrow capability (available/version/status/cancel) to
extensions; never the reflective bridge.

### 1.11 Diagnostics and profiling — **Verified**

- `logging/ModuleErrorLog`, `module/misc/DiagnosticsModule`, `; diag` report, `UpdateClock`.
- `Module.lastTickNanos()` already records per-module tick cost.

**Phase 4 need:** the diagnostics are summary-level; Phase 8 asks for bounded, per-subsystem
budgets (event handler duration, scheduler task duration, queue depth). This is additive.

### 1.12 Release build protection — **Partial**

- `build.gradle` produces normal jars; `dist/` is gitignored; releases are attached to GitHub
  releases (verified in earlier phases). There is no separate obfuscated/protected artifact.

**Phase 4 need:** keep source protection separate from normal development (Phase 9); do not
claim obfuscation prevents source recovery.

### 1.13 Existing tests — **Verified**

446 tests across 59 classes, all green. Coverage spans settings, config stores, module
lifecycle/registry, coordination, event bus, automation, combat math, waypoints, and theme.

## 2. Completed / incomplete / broken / incompatible

| Component | State | Note |
| --- | --- | --- |
| Module lifecycle + registry | Complete | 68 modules, count pinned |
| Typed settings | Complete | |
| Event bus + feed | Complete | Phase 3.2 |
| Coordination (deps/conflicts) | Complete | Phase 3.1 |
| Entity snapshots | Complete | |
| HUD + ClickGUI | Complete | needs new pages |
| Config + profiles | Complete | needs extension store |
| Automation engine | Complete | Phase 3.2 |
| Baritone integration | Complete | |
| Diagnostics | Complete (summary) | budgets pending |
| Stable internal API | **Missing** | Phase 1 |
| Plugin SDK | **Missing** | Phase 2 |
| Scripting | **Not started** | Phase 3 (feasibility gated) |
| World workspace | **Partial** | waypoint screen exists; workspace panel pending |
| Developer workspace | **Missing** | Phase 5 |
| Release workflow | **Partial** | no protected artifact |

## 3. Regressions

None found. The build and the full test suite are green at `490d970`. No critical regression
required repair before Phase 4 could begin.

## 4. Phase 4 direction (decided here, implemented next)

1. **Stable internal API (Phase 1)** — a versioned `api/` package that wraps the existing
   services behind narrow interfaces, with scoped registration handles and cleanup. The
   first-party client uses the same facade, so the contract is exercised, not decorative.
2. **Plugin SDK (Phase 2)** — **compile-time / separate-mod integration**, not a custom
   runtime classloader. Extensions are ordinary Fabric mods that depend on `logvex-crystal`
   and register through the API at client init. Rationale: this is the standard, maintainable
   mechanism; a Java classloader is **not** a security sandbox, and pretending otherwise would
   be dishonest. Discovery therefore reads *already-loaded* Fabric mods that declare the
   capability, not arbitrary downloaded files.
3. **Scripting (Phase 3)** — deferred. No maintained runtime with the required CPU/memory
   limits is available without adding a heavy dependency; the honest choice is to omit it and
   document why (see `docs/scripting.md`).
4. **World workspace, developer workspace, config/profiles, regression tests, budgets,
   release, docs** — implemented as vertical slices, each committed with its tests.

The rest of Phase 4 is implemented against this plan. See `docs/extension-api.md` for the
actual API as built.
