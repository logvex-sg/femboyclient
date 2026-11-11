# HUD and visual customization

LOGVEX CRYSTAL's HUD is a set of independent elements that register themselves with a shared
manager, plus an editor that lays them out. Nothing here draws its own panel outside the manager,
so one change to the theme, the scaling or the layout reaches every element at once.

## Elements

Every element implements `gui/HudElement` and declares a stable `id()`. The manager stacks them
against the left or right edge; an element is a pure renderer and never mutates state, which is
what lets the whole HUD run on the render thread without coordination.

| Element id | Shown as | Default |
| --- | --- | --- |
| `watermark` | Logo / brand | visible |
| `module-list` | Active modules | visible |
| `target` | Target information | visible |
| `crystal-damage` | Crystal damage / status | visible |
| `coords` | Coordinates | visible |
| `performance` | FPS / frame time | visible |
| `notifications` | Notifications | visible |
| `biome` | Biome | visible |
| `time` | World time and day | visible |
| `light` | Block and sky light | visible |
| `network` | Dimension and ping | visible |
| `entities` | Entity counters | visible |
| `armor` | Armor status and durability | visible |
| `potion` | Potion effects | visible |
| `speed` | Movement speed | visible |
| `crosshair` | Custom crosshair | visible |
| `session` | Session statistics / playtime | visible |
| `entitycount` | Entity count panel | visible |
| `direction` | Facing direction and yaw | visible |
| `totems` | Held totem count | visible |
| `clock` | Local wall-clock time | visible |
| `server` | Server host and ping | visible |
| `inventory` | Inventory fill and free slots | visible |
| `position-history` | Recent sampled positions | visible |
| `item-counts` | Crystal/obsidian/totem/pearl counts | visible |
| `combat-stats` | Crystal damage and lethality | visible |
| `worldinfo` | World information | visible |
| `keystrokes` | Keystrokes grid (held keys) | visible |
| `keybind-list` | Module keybind list | visible |
| `baritone` | Baritone status | hidden (opt-in) |
| `ai-assistant` | AI assistant | hidden (opt-in) |
| `command-center` | Command Center roll-up | hidden (opt-in) |
| `scan` | World-scanner progress | hidden (opt-in) |
| `world-intelligence` | Intelligence readout | hidden (opt-in) |
| `compass` | Compass | hidden (opt-in) |

Opt-in elements start hidden so they do not occupy HUD space until the matching module reveals
them. Toggle any element with `; hud <id> <on|off>`, or right-click it in the editor.

## Editor

`; hud edit` (or the **HudEditor** module / `; hudedit`) opens the layout editor.

| Input | Action |
| --- | --- |
| Drag | Move an element (grid snapping + alignment guides) |
| Right-click | Hide / show the element |
| Scroll | Scale the element |
| Shift-scroll | Fade the element (opacity) |
| Drag bottom-right grip | Resize |
| `B` | Toggle background |
| `N` | Toggle border |
| `[` / `]` | Send backward / bring forward (layer) |
| `R` | Reset the hovered element's placement |
| `Shift+R` | Reset every element's placement |
| `P` | Preview mode (hide the editor chrome) |
| `L` | Toggle the saved-layouts side panel |
| `Ctrl+Z` / `Ctrl+Y` | Undo / redo the last layout edit |
| `Esc` | Save and close |

Every structural edit takes an undo snapshot (bounded to 50 steps), so an accidental drag or
reset is one keystroke away from being reverted.

## Layout profiles (named layouts)

A layout can be saved under a name and switched later. Named layouts live in
`<config>/logvex-crystal/hud-layouts/<name>.json` and use the exact same schema as the active
`hud.json`, so a layout can be moved between the two without translation.

```
; hud save <name>       save the current layout under <name>
; hud load <name>       apply a saved layout to the live HUD
; hud list              list saved layouts
; hud delete <name>     remove a saved layout
; hud export <name>     write <name>.export.json for sharing
; hud import             import every *.export.json in the layout folder
```

Saving refuses to overwrite an existing layout unless asked; importing never overwrites — a name
clash gets a numeric suffix. Both behaviours are covered by `HudLayoutLibraryTest`.

The same operations are available from the editor without leaving it: press `L` to open the
saved-layouts panel, where each row has a **Load** button, an export/import row and a
**Save as…** field (type a name, `Enter` to commit, `Esc` to cancel).

## Visual settings

Themes and appearance are shared across the whole interface, not per-element: theme selection,
custom accent colours, background opacity, corner radius, gradient intensity, glow, UI scale,
animation speed, and reduced-motion are all in `ThemeSettings` (`; theme`, `; accent`, `; opacity`,
`; radius`, `; gradient`, `; glow`, `; scale`, `; animation`, `; motion`). Per-element overrides
(opacity, scale, background, border, layer) live in the layout and are edited in the editor.

## Performance

Elements are cached rather than recomputed every frame:

* The HUD manager throttles value refreshes; a readout that reads world state re-evaluates on an
  interval, not on every render pass.
* Entity and world readouts walk a bounded set and never allocate a collection per frame.
* The editor only re-renders guide lines while a drag is in flight.

The **Performance** workspace and `; perf` report frame time, the slow tail (p95) and per-module
tick cost, so a HUD element that starts costing too much shows up as a module-tick regression.

## Commands

```
; hud <element> <on|off> | toggle | edit
; hud save <name> | load <name> | list | delete <name> | export <name> | import
; hudedit            open the layout editor
```
