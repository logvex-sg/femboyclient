# LOGVEX CRYSTAL

A cute, pastel **crystal-PvP / anarchy** client for Minecraft **1.21.11** on Fabric.

LOGVEX CRYSTAL is a standalone client mod (not an addon or a theme pack). It wraps a
serious, responsive anarchy toolkit — crystal combat aids, world-space ESP and survival
utilities — in an original kawaii interface: pastel pink, baby blue, lavender and soft
purple on deep midnight gradients, with rounded panels, cat ears, paw prints, hearts and
stars.

All artwork, palettes and icons are original to this project. No third-party client's
proprietary interface or theme data is copied.

## What it is (and is not)

* **Is:** a client-side mod. It reads world state, draws an interface, and can act on the
  world the same way the player can — placing blocks, using items and attacking — through the
  vanilla interaction manager, so the server validates every action for reach and legality
  exactly as it does for a human click.
* **Is not:** a client that manipulates the server outside the game's rules. It performs no
  external process interaction, injects nothing into other programs, and never sends anything
  to the server that the vanilla client would not.
* Combat and player modules are **automation of ordinary actions**, not server exploits: they
  select hotbar slots, click inventory slots, place/break blocks and swing, all through the
  same code paths as manual play. Read the individual module descriptions for their limits.
* Commands are handled **locally**. Anarchy servers reject unknown `/` commands, so a line
  starting with `;` (the legacy `.lvx` / `.crystal` still work) is intercepted before it is
  sent and never reaches the server.

## Building

Requires only a **JDK** on your `PATH`; Gradle provisions everything else.

```bash
./gradlew build          # compiles, runs the unit tests, produces the jars
./gradlew test           # unit tests only
./gradlew runClient      # launch a dev client
```

Fabric Loom 1.18.4 must run the build on **Java 25**, while the mod compiles against the
**Java 21** toolchain Minecraft 1.21.11 targets. Both are declared in the build, so even if
your default `java` is 17 or 21, `gradle/gradle-daemon-jvm.properties` makes Gradle download
and use a Java 25 JVM for itself, and the Foojay resolver in `settings.gradle` fetches the
Java 21 toolchain. No manual `JAVA_HOME` juggling is needed.

If your network blocks the JDK download (or you are offline), fetch a JDK 25 once with the
helper and point Gradle at it:

```bash
./scripts/setup-jdk.sh
export JAVA_HOME="$HOME/.local/logvex-jdk-25"
```

Output jars land in `build/libs/`:

* `logvex-crystal-1.21.11-<version>.jar` — the mod
* `logvex-crystal-1.21.11-<version>-sources.jar` — sources

### Headless GUI smoke test

A dev-only, opt-in driver exercises the ClickGUI, Appearance tab, theme switching, the HUD
editor and config persistence without a human at the keyboard:

```bash
xvfb-run -a ./gradlew runClient -Psmoke=440
```

It fails loudly (and stops the client) if a screen does not install, a palette fails to
rebuild, or a config file does not round-trip.

## Commands

All commands are local-only. Prefix `;` or `.crystal`.

| Command | Purpose |
| --- | --- |
| `; gui` / `; clickgui` | Open the ClickGUI |
| `; category <name>` | Open the ClickGUI on a category |
| `; appearance` / `; favorites` / `; recent` | Open the ClickGUI on that page |
| `; keybinds` | Open the keybind editor |
| `; profile <list\|save\|overwrite\|load\|delete\|starters\|gui>` | Manage configuration profiles |
| `; favorite <module>` | Star/unstar a module |
| `; ai <status\|gui\|on\|off\|local\|model\|crashes\|analyse\|baritone\|report\|reports\|send\|mailto>` | Optional local AI assistant |
| `; center <status\|perf\|compat [module]\|actions\|run <id>\|save\|reload>` | Unified status, compatibility and performance hub |
| `; toggle <module>` | Enable/disable a module |
| `; bind <module> <key>` | Set a module keybind |
| `; set <module> <setting> <value>` | Change any module setting |
| `; preset <name>` / `theme` / `variant <dark\|light>` | Theme selection |
| `; accent <#RRGGBB\|reset>` / `secondary ...` | Accent colours |
| `; opacity\|gradient\|glow\|scale\|animation <0..1>` | Appearance sliders |
| `; radius <0..12>` | Corner rounding |
| `; decorations\|cat\|motion\|blur\|cursor\|sounds\|splash <on\|off>` | Feature toggles |
| `; easteregg <on\|off>` | Toggle the hidden cosmetic |
| `; waypoint <add\|remove\|list\|clear> [name] [x y z]` | Manage waypoints |
| `; friend <add\|remove\|list> [name]` | Manage the friend list |
| `; hud <element> <on\|off>` / `hudedit` / `gui` | HUD control |
| `; scan <start\|pause\|resume\|stop\|reset\|status\|gui\|export\|profile>` | World scanner control |
| `; save` / `load` / `list [category]` / `version` | Config & info |
| `; assets [list\|reload]` | Report the asset pack, or clear the texture cache |

## World scanner (local only)

`; scan` drives an autonomous, local world scanner: it walks the client's own loaded
chunks, classifies what it observes, and keeps a persistent map of the result. It is a real
state machine, not a wrapper around a placeholder.

- **States**: `IDLE → INITIALIZING → MAPPING/EXPLORING → ANALYZING → COMPLETED`, plus
  `PAUSED` and `ERROR`. The HUD and dashboard show the live state.
- **Sampling**: only chunks the client already has loaded are read; the scanner never loads
  a chunk or bypasses server-side protections. Unloaded chunks are counted and retried later,
  never recorded as observed.
- **Flight**: when creative flight is available and permitted the scanner uses it, adopting an
  existing flight and never switching off a flight the user started. Movement goes through
  Baritone when it is present; otherwise the scanner still samples the loaded window around
  the player.
- **Classification**: a deterministic, offline classifier assigns each chunk a category
  (natural, building, storage, farm, redstone, underground, abandoned, mob farm, mine, portal,
  other) with a confidence score. Weak evidence is downgraded to `other` rather than guessed,
  and a user correction always wins over later automatic classification.
- **Performance**: classification runs off the client thread behind a bounded queue, the map
  is indexed by dimension and chunk, and per-tick sampling is capped. Nothing blocks the game
  thread.
- **Safety**: only local observation. No covert collection of other players' coordinates or
  inventories, no external transmission, no bypassing of access controls.

Controls: `; scan start|pause|resume|stop|reset`, `; scan status`, `; scan gui` for the
dashboard, `; scan export` to flush the session database, and
`; scan profile <name>` / `; scan profile save <name>` for reusable configurations.

## ClickGUI

The ClickGUI is the control surface for the entire client — every module, setting,
keybind, HUD element, appearance knob and config profile. Open it with `; gui` or the
`Right Shift` keybind.

* **Pages:** Modules, Appearance, Favourites and Recent, selected from the sidebar.
* **Modules:** rounded cards per module with animated toggles, a favourite star, a
  right-click context menu (toggle, bind, favourite, open the category) and hover tooltips.
  Settings expand inline; sliders, dropdowns, keybind capture, colour pickers and toggles
  are all editable in place. Shift-click a setting to reset it to its default.
* **Search + sort:** the search box matches a module's name, description, category or any
  setting name, and spans every category while it holds text. The sort button next to it
  cycles the ordering (default / name / category / enabled-first). Hovering a card explains
  why it matched ("matched: setting: Health") and what it needs ("needs X", "conflicts
  with Y").
* **Appearance:** theme preset grid, accent colour pickers, opacity/gradient/glow/radius/
  scale/animation sliders, decorative + cat-cosmetic toggles, reduced motion, blur, custom
  cursor, GUI sounds, splash text, light/dark variant and the easter-egg toggle. Every
  change applies live, with no restart, and persists to disk.
* **Profiles:** save, load, rename, duplicate, delete, export and import named
  configuration snapshots (see below).

Module keybinds are handled by a small mixin on the vanilla keyboard: a bound key toggles
its module while no screen is open, so it never steals input from the GUI or chat.

## Chest coordinate logger (local only)

The `ChestLogger` module counts chests and trapped chests in the chunks the client already
has loaded, groups nearby ones into clusters, and raises a **local** notification when a
cluster reaches the threshold (30 chests by default). Each find records the dimension, the
cluster centre and bounds, the chest count, the discovery time, and the names of players
the client can already see nearby.

This feature is deliberately **local-only**. There is no webhook, no upload, and no network
code of any kind in it: it never transmits positions anywhere, and it has no hidden or
remotely triggered mode. It reads only block-entity *types* in loaded chunks (never chest
contents), and it never forces chunks to load. Settings: scan interval, threshold, cluster
radius, vertical tolerance, minimum cluster size, duplicate radius, cooldown, player radius,
notification toggle and chunk radius.

## Combat and player automation

The **Combat** category drives the crystal-PvP loop and the blocks around it; the **Player**
category keeps the inventory ready for it. Every action is an ordinary game action sent
through the vanilla `ClientPlayerInteractionManager`, so the server validates reach and
legality exactly as for manual play — these modules play the game, they do not exploit it.

* **AutoCrystal** — places obsidian, places a crystal, detonates it. A self-damage guard
  refuses any action that would kill you or exceed your configured self-damage limit.
* **Surround** / **Burrow** — walls you in with obsidian, or places a block in your own space.
* **AutoMine** / **CityMiner** — mines the blocks around a target through the vanilla
  block-breaking progress.
* **WebAura** — places cobwebs on a target to slow them.
* **AutoPearl** — throws an ender pearl when the predicted crystal damage would be lethal.
* **Offhand**, **AutoArmor**, **AutoRefill**, **AutoGapple**, **AutoMend** — keep a totem in
  the offhand, wear the best armour, stock the hotbar, eat a gapple and mend with bottles.

Each module only uses items the player already carries and only acts on what the client can
already see. See `combat/CrystalPlanner` for the shared surround/city geometry.

## ESP and world modules

The **World** category groups read-only world tools. A shared world-query layer caches the
block entities of loaded chunks once per tick (`world/WorldQuery`), and the ESP filter and
block-label helpers are shared by every render module, so the ESP set stays small and
consistent. The block-entity ESP modules never force chunks to load and never read
container contents — only the block-entity type and position.

* **Render:** PlayerEsp, MobEsp, ItemEsp, ProjectileEsp, ChestEsp, EnderChestEsp, ShulkerEsp,
  SpawnerEsp, BedEsp, PortalEsp and BlockEsp, on a shared block-entity ESP base.
* **World readouts:** `WorldInfo` supplies biome, dimension, time, light level and ping to
  the HUD; `BlockScanner` finds nearby blocks for the ESP modules.

## HUD

HUD elements are toggled by matching modules and laid out by the HUD editor (position,
scale and opacity per element). Alongside the watermark, module list, target HUD and crystal
damage indicator, the readouts include biome, time, light level, dimension/ping, entity
counts, armour durability, potion effects, speed and a WASD keystroke overlay. Compact
readouts are built from a shared `SimpleHudElement`, so they share the same rounded panel,
spacing and opacity handling.

## Themes

Presets: **Default, Femboy, Sakura Pink, Cotton Candy, Lavender Dream, Midnight Kitty,
Soft Boy, Sakura Femboy, Sakura Cyber**, each in a **dark** or **light** pastel variant.
Every appearance knob — accent colours, background opacity, gradient/glow intensity, corner
radius, UI scale, animation speed, decorations, cat cosmetics, reduced motion, blur, custom
cursor, GUI sounds, splash text — is persisted to `config/logvex-crystal/appearance.json`
and applied live, with no restart.

## Configuration profiles

A profile is a named snapshot of all three configuration layers (modules, HUD layout and
appearance). Manage them from the **Profiles** screen in the ClickGUI or the `; profile`
command:

```
; profile save <name>       # snapshot the current setup (refuses to clobber)
; profile overwrite <name>  # replace an existing profile
; profile load <name>       # apply a saved profile or a starter
; profile delete <name>
; profile list              # saved profiles and the starter names
; profile starters
; profile gui               # open the Profiles screen
```

Rename, duplicate, export and import are available from the **Profiles** screen.

Four starter profiles ship with the client — **Combat**, **Anarchy Survival**, **Minimal
HUD** and **Pastel Aesthetics**. They are templates you load on demand and are never applied
automatically. Profile files are schema-versioned, written atomically, and a corrupt file is
moved aside (`*.json.corrupt`) rather than silently dropped; a profile written by a newer
build is refused rather than misread. Export documents use the `.export.json` suffix and are
kept out of the profile list.

## Optional local AI assistant

An opt-in mini-AI (`AIAssistant` module, `; ai` command, `; ai gui` screen) that runs entirely
on your machine. It never sends anything anywhere on its own: a local model is only loaded
when you explicitly ask for an analysis, and a bug report is only ever sent when you approve
it. Disabled by default; the deterministic parts (crash classification, Baritone diagnostics,
report building) work with no model at all.

* **Crash intelligence:** an uncaught-exception hook fingerprints and classifies a crash, the
  deterministic `CrashAnalyzer` always explains it, and the local model can optionally add a
  one-paragraph note (clearly labelled as model-generated; the confirmed facts and fixes stay
  authoritative).
* **Baritone diagnostics:** a deterministic advisor plus an optional model note, both driven
  from the privacy-filtered Baritone status.
* **Reports:** a prepared report is built from a privacy-filtered summary, queued locally and
  only sent after you approve it (by SMTP, or as a `mailto:` link). Every field is redacted
  first — home paths, IPs, coordinates, tokens and configured names never leave the machine.
* **Models:** a small, permissively-licensed catalogue; a model is downloaded on demand,
  verified, and unloaded after an idle timeout.

The subsystem respects the spec's resource limits: inference runs on one or two daemon threads
with a bounded queue, never on the game thread, and a request is dropped rather than queued
without limit. All preferences persist to `ai.json`. The Phase 7 design, model evaluation,
privacy, performance and testing write-ups live in [`docs/ai/`](docs/ai/).

## Player cosmetics

An optional, purely decorative set of accessories drawn on players: **cat ears**, **cat ears
with a pastel bow** or a **halo**. Enable the `PlayerCosmetics` module and pick a style; the
geometry is drawn by a Fabric feature renderer attached to the vanilla player renderer, so it
changes nothing about the player entity, its hitbox or any server interaction. The idle sway
is slow and subtle and never affects readability during combat.

The cosmetic is gated live by the theme's **decorations** and **cat cosmetics** toggles, and
**reduced motion** freezes the sway. Its texture is an original 64×64 skin-style sheet
(`textures/entity/cosmetic/player_accessories.png`) generated by the same Python toolchain.

## Assets

The interface ships with an original, reproducible PNG pack (79 files, ~47 KiB) generated by
the Python toolchain in `tools/`:

```
tools/assetlib.py    canvas primitives (blur, rounded shapes, SVG export)
tools/icons.py       the shape library and the icon name map
tools/branding.py    wordmark, logo, cat ears, sparkles
tools/gen_assets.py  driver: writes the pack + a contact sheet
```

Regenerate with `python3 tools/gen_assets.py`; the contact sheet lands in
`docs/assets-contact-sheet.png`. `AssetManifest` is the single source of truth for the pack,
`AssetRegistry` loads and caches textures lazily from the mod's own resources, and `Assets`
draws them — every call falls back to the equivalent procedural motif in `RenderUtil` when a
texture is missing, so the client is complete with or without the pack. Tinted icons follow
the active accent and are re-tinted on a theme change.

## Configuration files

Written under `<game>/config/logvex-crystal/`:

| File | Contents |
| --- | --- |
| `appearance.json` | Theme + appearance settings |
| `modules.json` | Module enabled state, keybinds and settings |
| `hud.json` | HUD element layout (position, scale, opacity) |
| `friends.json` | Friend list |
| `waypoints.json` | Saved waypoints |
| `favorites.json` | Favourite modules and recently-used list |
| `ai.json` | Mini-AI preferences (model, limits, redactions, SMTP) |
| `ai/crashes.json` | Persisted crash history |
| `ai/reports.json` | Queued bug reports |
| `ai/models/*.gguf` | Downloaded local models |
| `profiles/*.json` | Named configuration profiles |
| `scan-sessions.json` | World-scanner sessions, discoveries and scan profiles |

All stores are schema-versioned, written atomically, skip malformed entries on load, and
refuse a newer schema so a future version's data is never silently downgraded.

## Project layout

```
src/main/java/com/logvex/crystal/
  client/     entrypoint and composition root
  module/     feature modules by category (combat, movement, player, world, render, hud, misc)
  render/     drawing helpers, screen projection, world-space ESP
  world/      cached block-entity snapshot, world readouts and block scanning
  gui/        ClickGUI, HUD manager, HUD editor, keybind editor, profile manager, AI assistant
  assets/     asset manifest, lazy texture registry and fallback draw helpers
  theme/      presets, settings, engine, immutable palette
  ai/         optional mini-AI: crash intelligence, Baritone advice, reports, local models
  config/     JSON stores, configuration profiles, favourites, starters
  mixin/      keyboard keybind dispatch, splash/title tweaks
  combat/     crystal damage model and target tracking
  cosmetic/   paw-print particles and UI sounds, plus the optional player accessories
  command/    the ; command system
  social/     friend list
  scan/       local chest counting + clustering, and the autonomous world scanner
  util/       maths, animation, waypoints
src/test/java/  pure-logic unit tests (no Minecraft runtime needed)
```

The unit-test source set deliberately exercises only Minecraft-independent logic (the theme
engine, colour maths, the crystal-damage model, config codecs, ESP projection and hole
detection), so the suite runs without a client.

## License

MIT. See [LICENSE](LICENSE).
