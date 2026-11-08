# Reference research

Research performed 2026-11-08 against seven repositories, using the GitHub REST API and raw
file reads. Every feature listed below was found in a file listing or source file; nothing is
inferred from marketing prose. Where a repository could not be verified, it says so.

Method: `GET /repos/{owner}/{repo}` for metadata and licence, `GET
/repos/{owner}/{repo}/git/trees/{branch}?recursive=1` to enumerate the real source tree, and
raw reads of the files that mattered. Module names below come from actual file paths.

## Licence summary (checked, not assumed)

| Repository | Licence file | SPDX | Can we reuse code? |
| --- | --- | --- | --- |
| logvex-sg/basehuntingclient-baritone | `LICENSE` (MIT, © 2026 logvex-sg) | MIT | Yes, with attribution |
| MeteorDevelopment/meteor-client | `LICENSE` | GPL-3.0 | Copyleft; ideas only |
| etianl/Trouser-Streak | **no LICENSE file** | none | No — all rights reserved by default |
| AntiCope/meteor-rejects | `LICENSE` | GPL-3.0 | Copyleft; ideas only |
| MeteorClientPlus/MeteorPlus | `LICENSE` | AGPL-3.0 | Copyleft; ideas only |
| maxsupermanhd/meteor-villager-roller | `LICENSE` | GPL-3.0 | Copyleft; ideas only |
| realnnpg/glazed | `LICENSE` ("All Rights Reserved", © 2026 Glazed) | NOASSERTION | No — explicitly proprietary |

LOGVEX CRYSTAL is MIT. That matters: MIT code may be reused with attribution, but
GPL/AGPL/ARR code may not be copied into an MIT project without changing LOGVEX CRYSTAL's
licence. **No code was copied from any repository.** Everything implemented here was
re-implemented from scratch against LOGVEX CRYSTAL's own APIs; the references contributed
ideas and algorithm shapes only. See `THIRD_PARTY_NOTICES.md`.

## Primary reference: basehuntingclient-baritone (MIT)

56 tree entries; 7 modules, 6 utility classes, 2 systems, a HUD element, a command and 2 test
classes. Meteor addon for 1.21.11; Baritone is `recommends`, resolved reflectively.

| Repository | Feature | Implementation approach | Licence | Relevance | Decision |
| --- | --- | --- | --- | --- | --- |
| basehunting | `BaseHunterV2` elytra autopilot | Flies itself via Meteor `ElytraFly` Bounce mode (no fireworks); nearest-neighbour tour over clustered targets; terrain-following altitude; frontier push when idle | MIT | High — autonomous long-range exploration | ADAPT (route/target ideas; elytra-flight is out of scope for LOGVEX) |
| basehunting | `FlightPlanner.cluster` | Flood fill over packed chunk keys at a Chebyshev radius, collapsing adjacent new chunks into one flyable target | MIT | High — cluster nearby discoveries | IMPLEMENT |
| basehunting | `FlightPlanner.buildTargets` | Scores clusters on size, distance and proximity to known interest; sorts descending | MIT | High — ranked discovery priority | IMPLEMENT |
| basehunting | `FlightPlanner` visited set + `noteProgress`/`millisSinceProgress` | Dedupes visited targets and detects "nothing found for a while" | MIT | High — frontier recovery | IMPLEMENT |
| basehunting | `FlightPlanner` frontier emission | Emits a waypoint beyond the furthest known chunk to push the sweep outward | MIT | High — keeps exploration progressing | IMPLEMENT |
| basehunting | `NewChunks` inhabited-time signal | Chunk with zero/low inhabited time is probably freshly generated | MIT | Medium — a "chunk age" signal | ADAPT (as a confidence signal, not a base-hunting detector) |
| basehunting | `NewChunks` flowing-liquid signal | Liquids generate as sources; anything flowing proves block updates happened | MIT | Medium — same | ADAPT |
| basehunting | `NewChunks` multi-signal confidence | Requires N of M signals to agree before reporting | MIT | High — the "confirmed vs uncertain" rule | IMPLEMENT |
| basehunting | `StashFinder` weighted storage scoring | Counts storage block entities per chunk, weights shulkers/chests above furnaces, threshold on score | MIT | High — storage structure classification | IMPLEMENT (as a classifier signal) |
| basehunting | `StashFinder` max-scans-per-second throttle | Bounds work per second to stay responsive while flying | MIT | High — bounded processing | ALREADY IMPLEMENTED (per-tick chunk cap) |
| basehunting | `StashManager` merge radius | Stashes within N blocks merge into one entry instead of duplicating | MIT | High — duplicate detection | IMPLEMENT |
| basehunting | `StashManager` server-keyed storage | Entries keyed by server address so servers never mix | MIT | Medium | REJECT (LOGVEX keys by dimension/world, not server address; no server identity is collected) |
| basehunting | `PlayerLogger` / logout-spot tracking | Records where other players are and where they logged out | MIT | — | REJECT — covert tracking of other players is explicitly out of scope for LOGVEX |
| basehunting | `AutoLog` (disconnect on danger) | Disconnects on low health / nearby player / totem pop | MIT | — | REJECT — not a world-intelligence feature; LOGVEX does not auto-disconnect |
| basehunting | `WebhookNotifier` Discord webhooks | Pushes events to a Discord webhook URL | MIT | — | REJECT — LOGVEX must not transmit scan data externally |
| basehunting | `ElytraController` / `ElytraManager` | Drives Meteor ElytraFly; swaps spare elytras by durability | MIT | Low — depends on Meteor | RESEARCH ONLY |
| basehunting | `BaritoneHelper` reflective availability probe | `Class.forName("baritone.api.BaritoneAPI")` behind a static flag so a missing Baritone cannot throw `NoClassDefFoundError` | MIT | High — optional-dependency safety | ALREADY IMPLEMENTED (LOGVEX `BaritoneReflection`/`ReflectiveBaritoneBridge`) |
| basehunting | `ServerUtils` | One place to ask "where am I right now" | MIT | Low | ALREADY IMPLEMENTED (LOGVEX `WorldInfo`) |
| basehunting | `plannerTest` / `apiContractTest` Gradle tasks | Pure-logic headless test plus a reflective API-surface guard | MIT | High — the contract-test idea | IMPLEMENT (as an optional Baritone contract test) |

## Meteor Development meteor-client (GPL-3.0)

1160 tree entries; 196 files under `modules/` (render 47, movement 41, player 31, combat 30,
world 24, misc 19, 4 root). This is the architecture LOGVEX CRYSTAL's GUI already follows.

| Repository | Feature | Implementation approach | Licence | Relevance | Decision |
| --- | --- | --- | --- | --- | --- |
| meteor-client | Module/category/settings architecture | `Module` + `SettingGroup` + typed `Setting` builders, `Modules` registry | GPL-3.0 | High | ALREADY IMPLEMENTED (LOGVEX has its own `Module`/`Setting` system with the same shape) |
| meteor-client | `SearchTextSetting` / GUI search | Live filtering of the module list | GPL-3.0 | High | ALREADY IMPLEMENTED (ClickGUI search) |
| meteor-client | `BlockESP` / `StorageESP` style world render | Iterates loaded chunk sections, draws boxes for matching blocks | GPL-3.0 | Medium | ADAPT (LOGVEX already renders ESP from a cached snapshot; the scanner reuses that, it does not re-scan) |
| meteor-client | `ChunkDataEvent` / `ChunkDataEvent`-driven scanning | Scans a chunk once, when the server sends it, instead of polling | GPL-3.0 | High | IMPLEMENT (observe chunks once on load; never re-process unchanged chunks) |
| meteor-client | Waypoints system | Named points with dimension awareness | GPL-3.0 | Medium | ALREADY IMPLEMENTED |
| meteor-client | Config `System<T>` NBT persistence | Per-system save/load | GPL-3.0 | Medium | ALREADY IMPLEMENTED (LOGVEX uses versioned JSON via `ConfigIo`) |

## Trouser-Streak (no licence — all rights reserved)

167 tree entries; 94 module files. Predominantly griefing/exploit modules
(`ForceOPBook`, `BeeNBTCrash`, `MultiverseAnnihilator`, `OPplayerTPmodule`, dupe modules,
crash modules, `BaseFinder`, `NewerNewChunks`, `StashFinder`-like `StorageLooter`).

| Repository | Feature | Implementation approach | Licence | Relevance | Decision |
| --- | --- | --- | --- | --- | --- |
| Trouser-Streak | `BaseFinder` / `NewerNewChunks` | New-chunk detection to locate bases | none (ARR) | — | REJECT (licence; and base-targeting is out of scope) |
| Trouser-Streak | Crash / dupe / force-OP modules | Packet abuse | none (ARR) | — | REJECT (licence and policy) |
| Trouser-Streak | `PlayerAlarms` / `OnlinePlayerActivityDetector` | Detects other players' activity | none (ARR) | — | REJECT (surveillance of other players) |
| Trouser-Streak | General module catalogue breadth | 94 modules across categories | none (ARR) | Low | RESEARCH ONLY |

Nothing in this repository is reusable: it has no licence, and its feature set is
predominantly griefing. Recorded here only so the rejection is documented rather than silent.

## meteor-rejects (GPL-3.0)

255 tree entries; 64 module files plus 6 mixins. Notable: `AntiBot`, `AntiCrash`,
`AutoCraft`, `AutoFarm`, `ChestAura`, `CoordLogger`, `NewChunks`, `OreSim`, `SoundLocator`,
`TreeAura`.

| Repository | Feature | Implementation approach | Licence | Relevance | Decision |
| --- | --- | --- | --- | --- | --- |
| meteor-rejects | `NewChunks` | Chunk-age detection | GPL-3.0 | Medium | RESEARCH ONLY (idea already covered by the MIT reference) |
| meteor-rejects | `CoordLogger` | Logs coordinates of events | GPL-3.0 | Low | ALREADY IMPLEMENTED (LOGVEX chest logger records the player's own finds) |
| meteor-rejects | `AntiCrash` | Guards against malformed server packets | GPL-3.0 | Medium | RESEARCH ONLY (out of scope for this expansion) |
| meteor-rejects | `SoundLocator` | Locates events by sound | GPL-3.0 | Low | REJECT (no new world-intelligence value) |
| meteor-rejects | `AutoFarm` / `TreeAura` | Automated harvesting | GPL-3.0 | Low | REJECT (automation, not mapping; LOGVEX automation already exists separately) |

## MeteorPlus (AGPL-3.0)

312 tree entries; 123 module files. Almost entirely exploit/bypass modules:
`FlyPlus`/`JesusPlus`/`SpeedPlus`/`NoFallPlus` with per-anticheat modes, `KillAuraPlus`,
`XrayBruteforce`, `BedrockStorageBruteforce`.

| Repository | Feature | Implementation approach | Licence | Relevance | Decision |
| --- | --- | --- | --- | --- | --- |
| MeteorPlus | Mode-selection setting pattern | `XMode`/`XModes` enums plus a mode-dependent settings UI | AGPL-3.0 | Medium | ADAPT (idea: a mode enum that reveals only relevant settings — LOGVEX already supports `EnumSetting` + `visible` predicates) |
| MeteorPlus | `ZonesModule` / `ZoneData` | User-defined world zones with persistence | AGPL-3.0 | Medium | IMPLEMENT (as scan regions/bounds persistence) |
| MeteorPlus | `HologramModule` / `HologramData` | Structured per-entry data with a list model | AGPL-3.0 | Medium | ADAPT (idea: list-backed observation model) |
| MeteorPlus | `XrayBruteforce` / `BedrockStorageBruteforce` | Server-side brute forcing | AGPL-3.0 | — | REJECT (bypassing access controls) |
| MeteorPlus | Anticheat bypass modes | Evasion | AGPL-3.0 | — | REJECT (policy) |
| MeteorPlus | `MapIntegration` / `WhereIsIt` | Third-party map integration | AGPL-3.0 | Low | RESEARCH ONLY |

## meteor-villager-roller (GPL-3.0)

38 tree entries; exactly one module (`VillagerRoller`). Breaks and replaces a villager's work
block until a desired trade appears.

| Repository | Feature | Implementation approach | Licence | Relevance | Decision |
| --- | --- | --- | --- | --- | --- |
| villager-roller | `VillagerRoller` | Places/breaks a workstation repeatedly, checks offers, stops at target | GPL-3.0 | Low | RESEARCH ONLY — see the note below |

Note on villager workflows: the mission asks for "legitimate single-player or authorized-world
management workflows". The reference's actual behaviour is an automated trade reroll. LOGVEX
already exposes villager information through its entity/trade views, and the world scanner
classifies villages. Re-implementing an automated reroll would add a griefing-adjacent
automation without mapping value, so it is deliberately not implemented. This is a documented
rejection, not a silent skip.

## glazed (proprietary — All Rights Reserved)

184 tree entries; 79 module files. Mostly DonutSMP-specific economy/PvP automation
(`AHSell`, `AutoSell`, `FakePay`, `SpawnerOrder`) plus a large ESP collection and three
base finders (`PremiumTunnelBaseFinder`, `RTPBaseFinder`, `RTPEndBaseFinder`,
`RTPNetherBaseFinder`) and `esp/RegionMap`.

| Repository | Feature | Implementation approach | Licence | Relevance | Decision |
| --- | --- | --- | --- | --- | --- |
| glazed | `esp/RegionMap` | Renders an explored-region map | ARR | High (concept) | IMPLEMENT (own implementation: the dashboard Live Map) |
| glazed | `esp/ChunkFinder`, `AdvancedStashFinder` | Chunk/stash discovery ESP | ARR | Medium (concept) | IMPLEMENT (already: scanner map + discovery list) |
| glazed | `RTPBaseFinder` family | Random-teleport base finding | ARR | — | REJECT (licence; base targeting) |
| glazed | Economy modules (`AutoSell`, `FakePay`, `AHSell`) | Server-economy automation | ARR | — | REJECT (licence; not world intelligence) |
| glazed | `PlayerDetection`, `TabDetector`, `AdminList` | Detects other players/staff | ARR | — | REJECT (surveillance) |

## Cross-cutting findings

1. **Cluster before you travel.** Two independent references (the MIT planner and the glazed
   region map) converge on grouping nearby discoveries and visiting a cluster once. Implemented
   as `DiscoveryClusterer`.
2. **Score, do not just list.** Every serious finder ranks what it found. Implemented as
   `ClusterScorer` with an explicit, documented weight table.
3. **Several weak signals beat one strong one.** The MIT `NewChunks` requires N of M signals.
   Implemented as `VerificationStatus` (CONFIRMED / LIKELY / UNCERTAIN) derived from signal
   agreement.
4. **Throttle.** Both the MIT stash finder and LOGVEX cap work per unit time. Kept.
5. **Pure logic is testable.** The MIT repository ships a headless planner test and a reflective
   API-contract test. Both patterns are adopted.
6. **Nothing here justifies external transmission.** Webhooks, server-address keying and player
   tracking appear repeatedly in the references and are rejected every time for LOGVEX.
