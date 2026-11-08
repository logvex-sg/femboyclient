# LOGVEX CRYSTAL - Client Intelligence Audit Report

> Scope: the module lifecycle, settings, ClickGUI intelligence features, error reporting and
> persistence across the whole client. Every claim below was checked against the source tree
> and a real `./gradlew test build`. Numbers are from the run recorded in
> [Verification](#9-verification).

The brief was: audit every module/setting/command/HUD element, unify the module lifecycle,
add searchable/favourited/recent modules with dependency + compatibility indicators, make
every setting actually affect behaviour, guarantee cleanup on disable, and report errors
without flooding the log - with no placeholder functionality and no regression to working
features.

This report records what already met the bar, what was fixed, and what remains.

---

## 1. Summary of changes

| Area | Finding | Action |
| --- | --- | --- |
| Failed `onEnable` leaked resources | A module that acquired a listener/worker and then threw was left holding it (enable aborted without `onDisable`) | **Fixed** in `Module.enable` - the throw path now unwinds through `onDisable` |
| Search/sort logic untested and duplicated | The browser's filter was inline in `ClickGui` with no unit coverage | **Extracted** to `ModuleQuery` (Minecraft-free) and covered by `ModuleQueryTest` |
| No module ordering control | Cards were always in registration order | **Added** a sort control (default / name / category / enabled-first), persisted per session |
| Dependency/conflict indicators thin | Only a generic warning glyph; the tooltip did not explain *why* | **Added** `ModuleQuery.requirementLine` shown in the card tooltip |
| Search did not explain matches | A hit on a setting name was silent | **Added** `matchReason` ("matched: setting: Health") to the tooltip |
| Recents ignored command usage | Only GUI clicks called `markUsed` | **Added** `recordUsage` in `CommandManager` toggle + scan/intelligence |
| Module count drift | Matrix doc said 70, registry says 71 | **Fixed** the doc |

No module was removed, and no working behaviour was replaced.

---

## 2. Module inventory (71 registered)

`ModuleRegistry.EXPECTED_MODULE_COUNT = 71`, asserted by `ModuleRegistryTest`. Every module
is a real implementation with a `Category`, a description and typed settings; none is a
placeholder. The 22 HUD readouts inherit `HudToggleModule` (which owns their enable/disable
and render registration), so their per-file hook counts are zero by design.

Counts by category (registration order):

| Category | Modules | Settings declared |
| --- | ---: | ---: |
| Combat | 10 | 35 |
| Movement | 3 | 4 |
| Player | 8 | 29 |
| Render | 17 | 59 |
| HUD | 22 | 2 |
| Misc | 9 | 49 |
| World | 2 | 19 |
| **Total** | **71** | **~212** |

The full per-module table (file, settings, deps, conflicts, description) lives in
`docs/module-implementation-matrix.md`.

### Settings actually affect behaviour

Every setting is wired through `onChange` handlers or read via `.value()` in the module's
tick/render path. The two modules the static heuristic flags as "declares settings, reads
none" are false positives that were checked by hand:

- `ThemeModule` - its settings are bound with `onChange(...)` that write into the live
  `theme.settings()` object (e.g. `cornerRadius.onChange(v -> settings.cornerRadius = v)`).
- `AIAssistantModule` - its 21 settings are bound with `onChange(v -> { assistant.x = v;
  reconfigure(); })`, i.e. they configure the assistant service rather than being read in
  the module body.

No module declares a setting that is never consulted.

---

## 3. Unified module lifecycle

`Module` + `ModuleManager` already provide one lifecycle every module shares:

- **Identity**: unique `name()` (duplicate registration throws), `description()`,
  `category()`, `keybind()`, `searchKey()`.
- **Enable**: `enable(manager)` checks dependencies (`firstMissingDependency`) and conflicts
  (`firstActiveConflict`) first, then flips `enabled`, then runs `onEnable()`.
- **Disable**: `disable()` flips `enabled`, runs `onDisable()` (the release hook), marks the
  config dirty. Always-enabled modules ignore it.
- **Tick**: `tickSafely()` runs `onTick()` under a try/catch so one broken module cannot stall
  the loop; per-module `lastTickNanos` feeds the performance readout.
- **Disconnect**: `ModuleManager.onDisconnect()` disables every enabled module (releasing
  world-bound resources) with per-module isolation.
- **Shutdown**: `ModuleManager.shutdownAll()` runs `onShutdown()` for **every** module
  (enabled or not), so workers/executors are always released.

### Fix: a failed enable could leak

Previously, if `onEnable()` threw after acquiring a resource, the module was set back to
disabled **without** calling `onDisable()`, leaking whatever it had taken. `enable` now
unwinds through `onDisable()` on the failure path (and records a separate error if the
cleanup itself throws). Covered by `ModuleManagerTest.aFailedEnableUnwindsInsteadOfLeaking`.

New lifecycle tests (in `ModuleManagerTest`):

- `disablingAModuleReleasesItsResources` - disable calls the release hook exactly once.
- `aFailedEnableUnwindsInsteadOfLeaking` - a throwing `onEnable` leaves nothing held and the
  module disabled.
- `disconnectRunsCleanupForEveryEnabledModule` - disconnect releases every enabled module.
- `shutdownRunsForEveryModuleEvenWhenDisabled` - shutdown reaches a module that never ran.

---

## 4. ClickGUI intelligence (search, favourites, recents, sort, indicators)

All of the following now exist and are unit tested via `ModuleQuery`:

| Feature | Where | Notes |
| --- | --- | --- |
| Live search | `ModuleQuery.matches` | Matches name, description, category and setting names; spans every category while the box is non-empty |
| Favourites | `FavoritesStore` + ClickGUI Favourites page | Star toggle per card and in the context menu; persisted to `favorites.json` |
| Recently used | `FavoritesStore.markUsed` + Recent page | Bounded (8), most-recent-first; now also updated by commands |
| Sorting | `ModuleQuery.SortOrder` + search-bar control | default / name / category / enabled-first |
| Dependency + conflict indicator | `ModuleQuery.requirementLine` | Card warning glyph plus a tooltip line ("needs X", "conflicts with Y") |
| Search-match explanation | `ModuleQuery.matchReason` | Tooltip shows whether the hit was name/description/category/setting |

`ClickGui.visibleModules()` now delegates to `ModuleQuery.apply(...)`, so the draw path and
the click path can never disagree about what is on screen - the same reason the sort control
captures its hit box during draw and reuses it for clicks.

---

## 5. Dependency checking and compatibility validation

- `ModuleManager` resolves dependencies and conflicts at enable time and can report the
  first blocker (`firstMissingDependency`, `firstActiveConflict`, `canEnable`).
- `ModuleCoordinationService` builds a dependency graph over both legacy `dependsOn` edges and
  typed `requires(ModuleDependency)` edges, and produces a `ConflictReport`.
- `ModuleCompatibilityEngine` turns that into a per-module verdict
  (`operable`, blockers, warnings) used by the command center; `; conflicts` reports it.

The ClickGUI now surfaces the same finding per card, so the requirement the coordination
layer knows about is visible where the user toggles the module.

---

## 6. Error reporting

`Module.recordFailure` keeps the message on the module (`lastError`, `errorCount`), logs the
original throwable once, and files it in the bounded `ModuleErrorLog` (a ring buffer surfaced
by `; errors`). `reportErrorThrottled` rate-limits per distinct message so a fault that fires
every tick records at most once per cooldown. The lifecycle fix adds one new recorded failure
("failed to clean up after a failed enable") rather than ever swallowing a cleanup error.

---

## 7. Configuration persistence and restoration

- `ConfigManager` + `AutoSave` (debounced, 1500 ms) persist enabled-state, settings,
  keybinds, HUD layout, favourites/recents and theme on any change (`Setting.onAnyChange`
  marks the config dirty).
- Writes are atomic; a corrupt file is quarantined as `*.json.corrupt` and the client starts
  from defaults rather than failing; a newer schema is refused rather than silently downgraded.
- `applyEnabledStates` restores enabled modules with dependency retries so an out-of-order
  config still resolves.

The new sort order is per-session UI state; it is intentionally not persisted (it is not a
module setting). Favourites/recents continue to round-trip through `favorites.json`.

---

## 8. Remaining gaps (honest status)

- **In-game verification.** This environment has no display, so rendering, keybind dispatch,
  GUI interaction and the ClickGUI sort control are verified by unit tests + the dev-only
  headless smoke driver, not by a live game. See `docs/release-readiness.md`.
- **Server-dependent behaviour.** Combat/crystal modules are complete and build, but their
  effect depends on a live server and is marked *implemented, unverified* in the matrix.
- **Sort order is not persisted.** Deliberate; it is transient UI preference. If persistence
  is wanted it belongs in the theme/UI config alongside `guiX`/`guiY`.

---

## 9. Verification

```
./gradlew clean test build      ->  BUILD SUCCESSFUL
tests                           ->  826 tests / 0 failures / 0 errors / 0 skipped
artifacts                       ->  build/libs/logvex-crystal-1.21.11-1.0.0.jar (+ -sources.jar)
```

Test classes touched or added in this pass:

- `module/ModuleQueryTest` (new) - search, matching, ordering, requirement lines.
- `module/ModuleManagerTest` (extended) - lifecycle cleanup and failed-enable unwinding.
- `gui/dashboard/IntelligenceDashboardStateTest` (new) - dashboard interaction state.
- `scan/world/WorldIntelligenceAiTest` (extended) - deterministic-vs-model tier.
