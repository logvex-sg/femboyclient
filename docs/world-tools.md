# World Tools and building toolkit

The World Tools system is the client's local construction and world-inspection surface. It is
deliberately *local*: it plans, previews, and reports on the world the client can already see.
It never loads a chunk to inspect it, never reads another player's storage, and never sends
coordinates anywhere.

## Building toolkit

The building side is a plan you edit, not a script that builds for you. Every operation is local
planning: a schematic is placed into the plan, moved, rotated, mirrored, removed, undone and
redone. Nothing touches the world until *you* place blocks.

```
world/build/
  Schematic          an immutable named set of (x, y, z, itemId) blocks
  BlueprintLibrary   the plan: schematics + placements, undo/redo, preview, material totals
  BlueprintStore     JSON persistence (atomic write, versioned, corruption tolerant)
  EditHistory        the undo/redo stack (undoDepth/redoDepth/nextUndoLabel)
  BuildPlanner       pure math: readiness, missing, checklist, stacks-missing
module/world/BuildPlannerModule   the module that owns the library and exposes the HUD lines
```

* **Schematic viewer / multi-layer viewer** — a `Schematic` reports `sizeX/sizeY/sizeZ`,
  `blockCount()` and a per-layer slice, so a tall build is inspected one layer at a time.
* **Placement assistant** — `BlueprintLibrary.place(name, x, y, z)` anchors a schematic at a
  coordinate; `effectiveSchematic(placement)` returns the rotated/mirrored blocks at that anchor.
* **Placement preview** — `previewBlocks(placement, centerX, centerZ, radius, cap)` returns the
  planned blocks inside a bounded radius, capped, so a preview never walks a whole schematic
  every frame. The module caches the result behind an `UpdateClock`.
* **Rotation and mirror** — `rotate(id)` steps 90° clockwise; `mirror(id)` flips X. Both are
  undoable.
* **Progress tracker** — `BuildPlanner.readiness(schematic, have)` is the satisfied fraction and
  `checklist(...)` is the per-item target/missing table, shown in the panel and the HUD.
* **Missing material calculator / collection checklist** — `BuildPlanner.missing(...)` returns the
  per-item deficit and `stacksMissing(...)` rounds it up to stacks.
* **Blueprint library / build history** — the library is the blueprint library; `EditHistory` is
  the build history and drives undo/redo.
* **Block palette / grid / alignment** — the placement anchor is the construction grid origin;
  the panel shows the coordinate and the footprint so placement is dimension-aware.
  `ConstructionGrid` computes the grid lines (aligned to spacing multiples, capped at
  `MAX_LINES`) and the two axis guides through a placement's origin; the **Construction grid**
  tool toggles the overlay, which the planner emits as thin world boxes each frame behind the
  same `UpdateClock` as the block preview.

## World utilities

`WorldInfoModule` builds the readout lines; the World Tools screen toggles its sections. Each
line comes from the shared cached world-query layer (`world/WorldQuery`), not a fresh scan per
frame:

* Block information, block-state inspector, block resistance.
* Light-level overlay and chunk-border display.
* Region coordinate display and biome information.
* Entity information and counters.
* World time, weather and dimension.
* Distance calculator and waypoint distance (`CoordinateMath.distance`, `format`).
* Nether/Overworld coordinate conversion (`CoordinateMath.toNether/toOverworld`), rounded
  symmetrically so a round trip returns the original coordinate.

## Storage and materials

Storage inspection only ever operates on inventories the player can legitimately access: the
player's own inventory, and a container whose screen the player has open.

```
storage/StorageSnapshot   a read-only view of an accessible inventory: stacks, totals, duplicates
storage/StorageScanner    builds a snapshot from the player inventory and the open container
automation/inventory/InventoryActions   the accessor that reads a live handler
```

* Shulker/container contents viewer — `InventoryActions.containerOccupied(handler)` and the
  snapshot report the stacks in the open container.
* Storage summary / material totals — the **Storage summary** tool calls
  `StorageScanner.scan(client, protected)` and reports stack and item totals per item id;
  `BlueprintLibrary.plannedMaterials()` sums the planned blocks per item.
* Duplicate-stack detection — the **Duplicate stacks** tool reports the item ids
  `StorageSnapshot.duplicates()` finds in more than one stack.
* Protected-item rules — `AutoTrashAutomation.protectedSummary()`/`protectedItems()` and
  `TrashRules` define what sorting and trash must never touch; protected stacks are flagged in
  the snapshot so the GUI can grey them out rather than suggesting a merge the user forbade.
* Searchable item database — `ItemDatabase` is an offline, Minecraft-independent catalogue of
  item ids with names, categories and max stack sizes. The **Item database** tool searches it
  using the screen's search box (by id or name) and reports the match count and an example row.
* Container labeling — `ContainerLabels` stores a user-assigned label per container, keyed by
  dimension and block position, in `container-labels.json`. The **Container label** tool labels
  the currently open container with the search-box text. Only containers the player has opened
  are ever keyed; there is no discovery of containers the player cannot see.

`StorageScanner` walks exactly two things: the 36-slot player inventory, always, and the slots of
a container screen the server has already opened for the player. It never loads a chunk, never
reads another player's storage, and the walk is bounded by the handler's slot count, so a scan is
cheap enough to run on demand from a tool action rather than every frame.

## GUI

`; tools` (aliases `; worldtools`, `; wt`) opens the **World Tools** screen: a search box, three
area tabs (Building, World, Storage), a scrollable list of actionable tools, and a live preview
panel on the right showing the schematic/placement counts, the planned block count, the per
placement footprint and preview size, and the undo/redo depth. Clicking a tool runs it and the
result is shown in the status line.

## Commands

| Command | Effect |
| --- | --- |
| `; tools` | Open the World Tools screen |
| `; build list` | Summarise the plan and list placements |
| `; build schematics` | List the loaded schematics |
| `; build place <name> [x y z]` | Anchor a schematic (defaults to your position) |
| `; build move <id> <dx> <dy> <dz>` | Move a placement |
| `; build rotate <id>` / `; build mirror <id>` | Rotate 90° / mirror a placement |
| `; build remove <id>` | Remove a placement |
| `; build undo` / `; build redo` | Undo/redo the last plan edit |
| `; build save` | Persist the plan |
| `; build clear` | Remove every placement (schematics stay) |
| `; build materials` | Per-item totals with the missing deficit from your inventory |
| `; world` | Print the world readout lines |

## Performance

All world analysis is bounded and cached:

* The preview is capped by a block budget and rebuilt on an interval, never per frame.
* World readouts read the shared per-tick cache rather than re-scanning.
* The plan is a list, not a world scan; materials are computed from the plan, not the world.
* Overlays avoid re-allocating on every frame by reusing the cached preview list.
