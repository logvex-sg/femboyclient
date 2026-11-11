# Navigation and world intelligence

The navigation engine is a planner plus a position cursor. It records where you have been,
plans routes over coordinates you supply, and reports progress. It deliberately never moves the
player: movement execution is kept separate so you stay in control, and there is no hidden
auto-walk. Baritone is only used if the project already has that integration and you drive it
yourself.

## Architecture

```
navigation/
  WaypointRegistry    category/group/notes metadata per waypoint + bookmarks
  RoutePlan           saved routes and the route maths (length, remaining, progress, preview)
  NavigationState     the live cursor: which route, how far, arrival, history
  NavigationService   the client-facing engine: ticks the cursor, tracks position/death
module/world/NavigatorModule   reveals the compass readout
gui/hud/CompassHudElement      the compass + route readout
```

The core (`WaypointRegistry`, `RoutePlan`, `NavigationState`) is Minecraft-free, so the maths and
persistence are unit tested without a client. `NavigationService` is the only class that touches
`MinecraftClient`, and it only reads the player position.

## Waypoints and organisation

Waypoints keep their existing `WaypointStore` (name, coordinates, colour, dimension). The new
`WaypointRegistry` attaches *organisation* to a waypoint by name:

* **Categories** — a waypoint is `home`, `stash`, `portal`, `general`, or anything you set.
* **Groups** — a free-form grouping such as `overworld` / `nether`.
* **Notes** — a short reminder.
* **Bookmarks** — named coordinates that are not full waypoints (quick marks, last position).

The two stores are independent: deleting a waypoint leaves harmless metadata behind, and deleting
metadata never touches a coordinate.

## Routes

A route is an ordered list of block nodes in a dimension. `RoutePlan` computes:

* **Length** and **remaining distance** from the nearest node forward.
* **Progress** as the fraction of nodes passed.
* **Obstacle-aware preview** via `RoutePlan.preview(nodes, blocked)`, where the caller supplies
  the "is this block in the way" predicate over loaded blocks. The preview never loads a chunk.

`NavigationState` follows a route as a cursor. Reaching a node is a radius test (default 4
blocks), so a fast traversal that clips a node still advances. Arriving at the final node latches
once and is recorded in the route history. A route can be paused, resumed, cancelled and
re-followed.

## Death and position tracking

`NavigationService` samples your position on an interval (bounded to a fixed history) and detects
the moment of death from the previous position, so the death marker is the spot you fell rather
than the respawn point. `; bookmark death` reports it, `; bookmark last` reports the last sample.

## Commands

| Command | Effect |
| --- | --- |
| `; nav list` | List saved routes with node count, length and dimension |
| `; nav add <name> <x z> [x z ...]` | Build a route from coordinate pairs at your current Y |
| `; nav remove <name>` | Delete a route |
| `; nav start <name>` | Follow a route (tracks progress only; never moves you) |
| `; nav stop` | Cancel the active route |
| `; nav pause` / `; nav resume` | Pause/resume the cursor |
| `; nav progress` | Show the active route's progress line |
| `; nav history` | Show the route history (completed and cancelled) |
| `; bookmark add <name> [x y z]` | Save a bookmark at your position or given coordinates |
| `; bookmark remove <name>` | Delete a bookmark |
| `; bookmark list` | List bookmarks |
| `; bookmark death` | Report the recorded death location |
| `; bookmark last` | Report the last recorded position |

## World awareness

The `WorldInfo` module (see `docs/world-tools.md`) already reports biome, dimension, time, light,
targeted block/entity and local entity counts from the shared cached query layer. Navigation adds
the compass readout, which shows the bearing and relative direction to the nearest waypoint in
the current dimension, plus the active route's progress.

## Performance

* The position history is bounded (240 samples, one every 250 ms).
* Route maths is O(nodes); a route is a list, not a world scan.
* The compass reads the already-cached waypoint store and navigation state.
* No world analysis runs on the render thread: the cursor advances on the client tick.
