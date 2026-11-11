# Automation subsystem

LOGVEX CRYSTAL ships a modular player-automation framework. It exists to perform *ordinary
game actions* — eating, swapping a totem, restocking the hotbar, sorting a chest — through the
same `ClientPlayerInteractionManager` a human uses, so the server validates reach and legality
exactly as it would for manual play. Nothing here is a server exploit, and nothing reads or
touches another player's storage.

## Architecture

```
automation/
  AutomationModule      base class: lifecycle, cooldown, status line, inventory guard
  AutomationService     the hub: pause/resume, emergency stop, queue, shared transaction manager
  AutomationEngine      rule engine (trigger + condition + action), event-driven
  AutomationContext     the world/player façade every module and action sees
  AfkDetector           input-idle tracking used by AFK Guard and Session Stats
  inventory/            the shared inventory transaction layer
  task/                 the cooperative task queue
module/automation/      the concrete modules (see below)
gui/AutomationPanelScreen   the Automation panel
```

Every module extends `AutomationModule`, which is an ordinary `Module` (category `AUTOMATION`)
plus:

* `onTick()` — called on the client tick only while the module is enabled.
* `canAct()` / `canActUnscreened()` — the "am I allowed to touch the inventory right now" gate:
  a screen must not be open (unless the module wants a container), a container must not be open
  unless the module targets one, and the shared transaction manager must be free.
* `inventoryBusy()` — true while any other module holds the inventory arbiter.
* `statusLine()` — a short live string shown in the panel next to the module row.

## Task queue

`automation/task` is a cooperative queue; it never blocks the client thread.

* **Sequential & conditional** — a task's `step(ctx, progress)` returns `RUNNING`, `COMPLETED`,
  `FAILED` or `CANCELLED`. A task may declare prerequisites and resource requirements.
* **Priorities** — `TaskPriority { CRITICAL, HIGH, NORMAL, LOW, BACKGROUND }`; the queue runs the
  highest-priority ready task each tick.
* **Progress** — `progress.done/total`, surfaced as a fraction in the panel.
* **Cancellation** — `cancel(id)` and `emergencyStop()` mark the running task cancelled and clear
  the queue.
* **Failure recovery** — a task may declare a `recover` callback; a failed task is retried up to
  its retry budget, otherwise recorded in history.
* **History** — every finished task lands in `queue.history()` with its outcome and duration.

## Shared inventory transactions

Competing clicks are the classic automation bug: two modules each try to move items and the
result is an item in the wrong slot. LOGVEX CRYSTAL funnels every inventory mutation through
`InventoryTransactionManager`:

* One transaction runs at a time. A module that wants the inventory asks the manager; if it is
  busy the module simply waits for the next tick.
* A transaction is a list of `InventoryTransaction.Step`s executed one per tick, so a multi-move
  operation (refill the hotbar, dump junk into a shulker) cannot interleave with another module.
* `hotbarSwap`, `moveStack`, `dropStack`, `quickMove` are the only primitives; each maps to one
  vanilla click, so the server sees exactly the clicks a player would produce.
* A screen-guard and a cooldown are applied by the manager, not by each module.

## Modules

### Inventory
`AutoTotem` (offhand totem, auto-replace on pop), `AutoArmor` (wear best armour by a
configurable priority), `AutoWeapon` (select the best weapon for the target), `AutoTool` (select
the right tool for the block being mined), `AutoRefill` / `AutoReplenish` (top up the hotbar and
restock consumables from the backpack), `AutoEat` (eat by a food priority when hungry),
`AutoPotion` (drink a supported normal potion when its effect is absent), `AutoSort` (sort the
backpack with user rules), `AutoTrash` (drop listed junk, with an explicit protection list),
`ShulkerOrganizer` (sort shulker contents), `HotbarPresets` (save/apply named hotbar layouts),
`ContainerTransfer` (assisted move between your inventory and an open container),
`RestockPlanner` (compute and execute what the hotbar is missing).

### Player assistance
`AutoSprint`, `SafeWalk` (stop at ledges), `AutoJump`, `AutoSwim`, `AutoRespawn`, `AutoGG`
(send a configurable message after a kill), `AfkGuard` (react to input-idle), `SessionStats`
(per-session counters). `AutoWalk` and `AutoReconnect` live in the Movement and Misc categories
and are unchanged.

## Controls

* `; automation` / `; auto` — the command surface (queue, pause, resume, stop, restock, rules).
* `; automation gui` / `; gui` — the **Automation panel**: hub status, current task with a
  progress bar, the shared-transaction line, the module list with live status, per-module
  settings, pause/resume/emergency-stop/clear-history buttons.
* The panel and the command surface act on the same `AutomationService`, so pausing in the GUI
  pauses the engine the commands drive.

## Safety

* Only items the player already carries are used, and only slots the module is allowed to touch.
* `AutoTrash` never drops enchanted, renamed, protected or container items.
* Every module is off by default; enabling is explicit.
* The emergency stop cancels the queue, clears the running transaction and latches until cleared.
