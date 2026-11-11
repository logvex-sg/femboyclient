# LOGVEX CRYSTAL — Command Center

> Verified against the source tree on branch `feat/femboy-product-depth`, with a real
> `./gradlew build` (JDK 25 build JVM / Java 21 toolchain). Every claim below names the file
> that implements it.

## 1. What this subsystem is

The Command Center is a single, read-mostly hub behind one package,
`com.logvex.crystal.commandcenter`. It does three things and nothing else:

| Feature | Implemented by |
| --- | --- |
| Unified status snapshot | `CommandCenterService` + the `provider/` status providers |
| Module compatibility checking | `ModuleCompatibilityEngine` / `CompatibilityVerdict` |
| Performance intelligence | `PerformanceMonitor` + `PerformanceIntelligence` / `PerformanceReport` |

It is a **discovery and aggregation layer**, not a second implementation of the client. Every
provider reads state the owning subsystem already keeps; every action forwards to an existing
client operation through `CommandCenterHost`. Nothing here enables, disables or reconfigures a
module on its own.

## 2. Architecture

| Type | Role |
| --- | --- |
| `CommandCenterService` | The orchestrator. Aggregates providers, owns the compatibility engine, the performance monitor and the action registry. Caches the last snapshot. |
| `StatusProvider` | One subsystem's contribution: a pure `snapshot()` returning a `StatusSection`. |
| `StatusEntry` / `StatusSection` / `ClientStatusModel` | Pure status values. A section rolls its own worst `HealthStatus` up; the model rolls the sections up. |
| `HealthStatus` | `OK` / `INFO` / `ATTENTION` / `CRITICAL`, ordered so a single `worseOf` comparison rolls anything up. |
| `ModuleCompatibilityEngine` | Read-only layer over `ModuleCoordinationService`: per-module blockers, warnings and declared requirements. |
| `PerformanceMonitor` | Bounded rolling window of frame times; produces a `PerformanceSample`. |
| `PerformanceIntelligence` | Deterministic, threshold-based rules over a sample; produces a `PerformanceReport`. |
| `CommandRegistry` / `CommandCenterAction` / `ActionResult` | The discoverable action set, keyed by stable id and grouped by category. |
| `StandardActions` | The standard verbs (status, diagnostics, interface, config, module toggles). |
| `CommandCenterHost` / `ClientCommandCenterHost` | The one seam to the live client; the production adapter is a thin forwarder. |
| `CommandCenterContext` | Builds the bounded, privacy-safe text block handed to the optional local model. |
| `CommandCenterModule` / `CommandCenterHudElement` | The opt-in HUD readout and its module home. |

Minecraft types appear only in `ClientCommandCenterHost`, the HUD element and the module.
Everything else is plain Java, so the whole hub is unit testable without a game runtime.

## 3. Status providers

`CommandCenterService.snapshot()` asks each registered provider for a section, isolates any
provider that throws (it becomes a single `CRITICAL` "provider failed" entry rather than
blanking the model), appends the performance section and caches the result.

The default set, wired in `CrystalClient.bootstrapCommandCenter()`:

| Provider id | Source |
| --- | --- |
| `client-info` | `ClientInfoStatusProvider` — mod version, MC version, loader, mod count |
| `modules` | `ModuleStatusProvider` — enabled count, recent failures from `ModuleErrorLog` |
| `coordination` | `CoordinationStatusProvider` — conflicts and in-flight actions |
| `ai` | `AIStatusProvider` — state, queue depth, crash monitor, pending reports |
| `baritone` | `BaritoneStatusProvider` — availability and navigation state |
| `events` | `EventStatusProvider` — event-bus backlog |
| `profiles` | `ProfileStatusProvider` — profile service state |
| `extensions` | `ExtensionStatusProvider` — discovered/enabled extensions |
| `automation` | `AutomationStatusProvider` — automation rule engine |
| `theme` | `ThemeStatusProvider` — active preset, variant and appearance flags |
| `performance` | Built by the service from the latest `PerformanceReport` |

A provider id must be unique; registering a duplicate throws immediately.

## 4. Performance intelligence

`PerformanceMonitor` keeps a bounded window of frame times (default 120). It reports
`fps`, mean frame time, the 95th-percentile frame time (the hitch the eye notices) and the
standard deviation (jitter). Unknown values are sentinels (`-1`), never a fabricated number.

`PerformanceIntelligence` then applies independent, threshold-based rules:

| Rule id | Fires when | Severity |
| --- | --- | --- |
| `low-fps-critical` | fps < 30 | CRITICAL |
| `low-fps` | fps < 60 | ATTENTION |
| `frame-hitch` | p95 frame time > 50 ms | ATTENTION |
| `frame-jitter` | frame-time deviation > 12 ms | INFO |
| `module-tick` | one module > 3 ms/tick | ATTENTION |
| `ai-queue` | inference queue ≥ 2 waiting | ATTENTION |
| `ai-dropped` | any inference request dropped | INFO |
| `event-backlog` | events retained in the bus | INFO |
| `action-contention` | more than one action in flight | ATTENTION |

Findings are sorted most-severe-first and each names the observation, the severity, a concrete
suggestion and the triggering metric. The rules are deterministic and need no model, so the
verdict is reproducible and unit tested (`PerformanceIntelligenceTest`).

Sampling is driven from the tick loop: `CrystalClient.tickCommandCenter()` records the frame
delta every frame (a few array writes) and rebuilds the status snapshot at most twice a second.

## 5. Module compatibility

`ModuleCompatibilityEngine.evaluate(module)` groups the very same `ConflictReport` findings the
`; conflicts` command reports into a per-module `CompatibilityVerdict`:

- `blockers` — `BLOCKING` issues (the module cannot operate),
- `warnings` — `WARNING` issues (it operates, with caveats),
- `requirements` — the module's own declared `ModuleDependency` metadata.

`blocked()` returns only the modules that cannot operate; `summary()` is a one-line roll-up.
Nothing here mutates a module.

## 6. Actions and the command surface

The Command Center is reachable three ways:

- **Command** — `; center <status|perf|compat [module]|actions|run <id>|save|reload>`
  (aliases `; commandcenter`, `; hub`), implemented in `CommandManager.center(...)`.
- **GUI** — the same `CommandRegistry` is the data source for the action list.
- **AI context** — `CommandCenterContext.build(...)` summarises health, problems and the
  performance verdict into a bounded block (capped at 1400 chars, 12 problems) with no
  server address, seed or token.

Standard action ids (`StandardActions`): `status.report`, `diagnostics.conflicts`,
`diagnostics.errors`, `diagnostics.clear-errors`, `diagnostics.dump`, `performance.report`,
`interface.clickgui`, `interface.appearance`, `interface.hud-editor`, `interface.favorites`,
`interface.recent`, `config.save`, `config.reload`, plus `module.toggle.<name>` for every
registered module.

An action never throws: an unknown id, an unavailable action or a failing body all come back
as `ActionResult.failure(message)`.

## 7. Diagnostics report

`diagnostics/DiagnosticsService` gathers the client's own runtime state into an
`diagnostics/DiagnosticsReport` — a pure, stable formatter — and can export it. It owns two
pieces no other service keeps: a registry of named cache counters (a cache advertises its size
with `publishCache("waypoints", n)`) and an environment fact map (client, game version, loader).
Everything else is read from the services it is handed, so it holds no duplicated state.

The report has fixed sections: environment, performance (the same measured frames the
Performance workspace charts, plus the intelligence findings), render (the world-ESP pass
timings, when measured), module health (failures ranked, with the last error), caches,
config-file status and the recent error ring. It contains no server address, seed or token.

- `diagnostics/RenderStats` is a bounded window of the Crystal world-render pass: it records
  the ESP collect and draw times plus the box and label counts of the last pass, from the
  world-render hook. `DiagnosticsService.setRenderStats` attaches it, so the report can say
  how much of a frame the ESP pass actually cost rather than only how slow the frame was.
  The section is omitted entirely when nothing has been measured.
- The cache counters are published live from `CrystalClient.publishRuntimeCaches` at most
  twice a second: world block entities, entities, tracked entities, waypoints, navigation
  routes, event history, performance samples, automation tasks, inventory transactions,
  actions in flight, AI queue depth and crash records. So the caches section shows real
  numbers during normal play, not only in the smoke test.

- `; report` prints the roll-up and the first lines; `; report export` writes the whole
  document to `<config>/logvex-crystal/diagnostics.txt` with an atomic temp-then-move.
- `buildThrottled` rebuilds at most once a second; the workspace and the command both use it.
- The **Diagnostics workspace** in the Command Center renders the live report with severity
  colouring, a bounded scroll, an export button, a debug-mode toggle and a clear-errors button.

## 8. HUD and module

`CommandCenterHudElement` is a pure renderer over the cached snapshot — it never triggers a
scan while drawing. It shows the overall health, the problem count, the frame rate and the top
performance finding, coloured from the active palette.

The `CommandCenter` module (`module/misc/CommandCenterModule`) controls only that HUD panel's
visibility; the hub itself is always available through `; center` and the GUI. The panel is
hidden by default and revealed by the module, so it costs no HUD space unless the user asks
for it. Its state persists like any other module.

## 9. Optionality, cost and persistence

- **Optional.** The hub is created during bootstrap; nothing requires the HUD panel, the
  command, the AI or a model to be present.
- **Cheap.** Providers are queried at most twice a second, on the client thread, over state
  that already exists. The per-frame cost is a single frame-delta write.
- **Persistent.** The only user-facing setting is the module's `Hud` toggle, saved with the
  rest of the module state; every other value is derived live.

## 10. Tests

`src/test/java/com/logvex/crystal/commandcenter/` covers the pure core with no Minecraft
runtime: `StatusModelTest`, `PerformanceMonitorTest`, `PerformanceIntelligenceTest`,
`CommandRegistryTest`, `CommandCenterServiceTest`, `StandardActionsTest` (through a recording
`CommandCenterHost`), `ModuleCompatibilityEngineTest` and `CommandCenterContextTest`. The
registry count is guarded by `ModuleRegistryTest` (`EXPECTED_MODULE_COUNT`).

The diagnostics report is covered by `diagnostics/DiagnosticsServiceTest`: section presence,
healthy vs. unhealthy summaries, module-failure ranking, atomic export, one-per-second
throttling, the debug toggle and defensive null handling.
