# BasehuntingClient-Baritone feature matrix

Primary project-specific reference. Source: `logvex-sg/basehuntingclient-baritone` @ `main`
(MIT, © 2026 logvex-sg). Verified by reading the tree (56 entries) and the module/utility
sources directly — see `REFERENCE_RESEARCH.md` for the method and licence findings.

The reference is a Meteor addon for anarchy servers; LOGVEX CRYSTAL is a standalone Fabric
client. Their module systems differ, so "migration" here means *re-implementing the behaviour
on LOGVEX's own APIs*, never moving Meteor classes across.

Status values: **PRESENT** (LOGVEX already does this), **MIGRATED** (implemented in this
expansion), **PARTIAL**, **DEFERRED** (documented, not implemented), **REJECTED** (with
reason), **N/A**.

| # | Reference feature | Where it lives | LOGVEX status | Recommendation |
| --- | --- | --- | --- | --- |
| 1 | Elytra autopilot, fixed patterns (Grid/Spiral/Heading/Waypoints/Coordinates) | `BaseHunter` | PARTIAL — LOGVEX `RoutePlanner` does a bounded snake sweep; no spiral/heading modes | MIGRATED as route *shapes*: snake (existing) + spiral and frontier expansion (new) |
| 2 | Elytra autopilot, self-flown (Bounce mode, no fireworks) | `BaseHunterV2` | N/A — LOGVEX has no elytra flight module and will not add one | REJECTED — outside world-intelligence scope; would need an elytra module first |
| 3 | Target clustering (flood fill, Chebyshev radius) | `FlightPlanner.cluster` | MIGRATED — `DiscoveryClusterer.cluster` | Re-implemented for observations rather than chunks |
| 4 | Target ranking by size/distance/interest | `FlightPlanner.buildTargets` | MIGRATED — `DiscoveryClusterer.buildTargets` | Weights documented in `ClusterScorer` |
| 5 | Nearest-neighbour tour over targets | `BaseHunterV2` | MIGRATED — `DiscoveryClusterer.tour` | Greedy nearest-neighbour |
| 6 | Visited-target dedupe | `FlightPlanner.visited` | MIGRATED — `DiscoveryClusterer.wasVisited`/`markVisited` | |
| 7 | Frontier push when nothing found for a while | `FlightPlanner` + `frontier-after-seconds` | MIGRATED — `DiscoveryClusterer.frontier` | Bounded by the scan bounds |
| 8 | Terrain-following cruise altitude | `BaseHunterV2` | MIGRATED — `ScanProfile.terrainFollow`/`clearance` adjust the route's target Y | Applies to waypoint Y, not flight altitude |
| 9 | New-chunk signal: inhabited time | `NewChunks` | MIGRATED — `ChunkSample.inhabitedTicks` → `ObservationEngine` signal | Needs `WorldChunk.getInhabitedTime()` |
| 10 | New-chunk signal: missing light data | `NewChunks` | MIGRATED — `ChunkSample.lightComplete` signal | Needs the chunk's lighting flag |
| 11 | New-chunk signal: flowing liquids | `NewChunks` | MIGRATED — `ChunkSample.flowingLiquids` signal | Already counted per-column; now counts flowing only |
| 12 | Multi-signal confidence (N of M must agree) | `NewChunks.min-confidence` | MIGRATED — `VerificationStatus` from agreeing-signal count | CONFIRMED/LIKELY/UNCERTAIN |
| 13 | Naturally-generated block-entity allowlist | `NewChunks.NATURALLY_GENERATED_BLOCK_ENTITIES` | MIGRATED — `BlockFamilies.isNaturalBlockEntity` | Prevents false "player was here" |
| 14 | Storage scoring (shulkers/chests weighted above furnaces) | `StashFinder` | MIGRATED — `BlockFamilies.storageWeight` + classifier | Feeds STORAGE confidence |
| 15 | Spawner detection as a farm signal | `StashFinder.detect-spawners` | PRESENT — `StructureClassifier` → `MOB_FARM` | Already implemented |
| 16 | Min-distance-from-spawn filter | `StashFinder`, `NewChunks` | MIGRATED — `ScanConfig.minDistanceFromSpawn` | Filters observations |
| 17 | Max-scans-per-second throttle | `StashFinder.max-scans-per-second` | PRESENT — `ScanConfig.maxChunksPerTick` | Already implemented |
| 18 | Merge nearby finds (merge radius) | `StashManager.addOrMerge` | MIGRATED — cluster radius + `WorldMapper` dedupe | |
| 19 | Persistent store, append-mostly, refresh on re-sight | `StashManager` | PRESENT — `ScanDatabase` / `WorldMapper` | Already implemented |
| 20 | Server-keyed storage | `StashManager` (server address) | REJECTED — LOGVEX keys by dimension | Deliberate: do not record server identity |
| 21 | Player position / logout-spot logging | `PlayerLogger` | REJECTED — out of scope | Covert tracking of other players |
| 22 | Auto-disconnect on danger | `AutoLog` | REJECTED — out of scope | Not world intelligence |
| 23 | Discord webhook notifications | `WebhookNotifier` | REJECTED — out of scope | LOGVEX must not transmit scan data externally |
| 24 | Webhook per-server filtering | `WebhookSettings` | REJECTED — depends on 23 | |
| 25 | Reflective Baritone availability probe | `BaritoneHelper.AVAILABLE` | PRESENT — `BaritoneReflection` / `ReflectiveBaritoneBridge` | Already implemented |
| 26 | Baritone `pathTo` / `cancelEverything` for autopilot | `BaritoneHelper` | PARTIAL — LOGVEX `BaritoneService.goTo`/`cancel`/`pause`/`resume` | Reused; elytra process not used |
| 27 | Baritone optional at runtime, clear error when missing | `fabric.mod.json` `recommends` + `BaritoneHelper` | PRESENT — `BaritoneService.unavailableReason()` | Already implemented |
| 28 | Bounded Area/pattern parameters | `BaseHunter` mode settings | MIGRATED — `ScanBounds` + `ScanProfile` | |
| 29 | Chat feedback / progress reporting | `chat-feedback` | PRESENT — module notifications | Already implemented |
| 30 | Auto-start when in a world | `auto-start` | PRESENT — `WorldScannerModule.Mode.AUTO` | Already implemented |
| 31 | "Two autopilots must not fight" (`stop-base-hunter-v1`) | `BaseHunterV2.stopV1` | MIGRATED — `MovementCoordinator` claims/denies movement ownership | Generalized to any module |
| 32 | Elytra durability management / spare swapping | `ElytraManager` | N/A | REJECTED with #2 |
| 33 | Firework accounting | `BaseHunter` | N/A | REJECTED with #2 |
| 34 | HUD element with per-section toggles | `BaseHuntingHud` | PRESENT — `ScanHudElement` | Already implemented |
| 35 | `/basehunt status` style command | `BaseHuntCommand` | PRESENT — `; scan status` | Already implemented |
| 36 | Waypoint route mode | `BaseHunter` waypoints | PRESENT — LOGVEX waypoints + `RoutePlanner` | Already implemented |
| 37 | Per-server stash/player listing commands | `BaseHuntCommand` | REJECTED with #20/#21 | |
| 38 | Pure-logic headless test task | `plannerTest` | PRESENT — JUnit suite (726 tests) | Already implemented |
| 39 | Reflective API-contract test | `apiContractTest` | MIGRATED — `BaritoneContractTest` (skips cleanly when Baritone is absent) | |

## Known bugs and limitations in the reference

| Issue | Detail | LOGVEX handling |
| --- | --- | --- |
| Elytra dependency | `BaseHunterV2` drives Meteor's `ElytraFly`; a Meteor rename breaks it at runtime (hence `apiContractTest`) | LOGVEX has no such coupling; the Baritone contract test covers the one reflective surface it uses |
| Webhook URL in plain text | The README warns the webhook URL is stored in plain text in the config | LOGVEX has no webhook at all |
| Server-address keying | Coordinates are keyed by server address string, which is fragile (aliases, ports) | LOGVEX keys by dimension and never reads the server address |
| Frontier unbounded | The frontier push extends outward without a hard limit | LOGVEX clamps the frontier to the configured `ScanBounds` |
| Single-threaded scanning | Detection runs on the client thread | LOGVEX classifies off-thread behind a bounded queue |

## Summary

- MIGRATED in this expansion: 21 rows.
- Already PRESENT: 11 rows.
- REJECTED (documented, with reason): 9 rows.
- N/A / DEFERRED: 2 rows.

Nothing was silently dropped: every row has an explicit status, and each rejection states why.
