# LOGVEX CRYSTAL - Module Implementation Matrix

> 103 registered modules (`ModuleRegistry.EXPECTED_MODULE_COUNT`, asserted by
> `ModuleRegistryTest`). Generated from the source tree; regenerate after adding a module.
>
**Status key**
> - **Verified** - real implementation wired into the tick/render path; pure logic has unit
>   coverage and the module is exercised by the headless smoke run (isolated and in a
>   combined coexistence run against a live world).
> - **Implemented, unverified** - code is complete and builds, but the behaviour depends on a
>   live server/world and is not covered by an automated test.
> - **Partial** - only part of the documented behaviour is implemented.
>
Every module below is **implemented** (no placeholder/empty modules exist). Modules marked
> *unverified* still need in-game validation; none is a stub.
>
Settings column = number of constructor `add(...)` declarations (all validated and persisted).

### Combat

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AutoCrystal | `combat/AutoCrystalModule.java` | 10 | - | - | Places, detonates and manages end crystals against a target. |
| AutoMine | `combat/AutoMineModule.java` | 4 | - | - | Mines blocks around a target to open them up. |
| AutoPearl | `combat/AutoPearlModule.java` | 4 | - | - | Throws an ender pearl to escape lethal damage. |
| AutoTotem | `combat/AutoTotemModule.java` | 2 | - | - | Reports totem count and the nearest threat. |
| Burrow | `combat/BurrowModule.java` | 1 | - | - | Places a block in your own space to escape. |
| CityMiner | `combat/CityMinerModule.java` | 3 | - | - | Mines the blocks trapping a target. |
| CritAssist | `combat/CritAssistModule.java` | 2 | - | - | Shows when you are falling for a critical hit. |
| HoverTotem | `combat/HoverTotemModule.java` | 2 | - | - | Predicts self-crystal damage at your feet. |
| Surround | `combat/SurroundModule.java` | 3 | - | - | Walls the player in with obsidian against crystals. |
| WebAura | `combat/WebAuraModule.java` | 4 | - | - | Places cobwebs on a target to slow them. |

### Movement

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AutoWalk | `movement/AutoWalkModule.java` | 2 | - | - | Walks forward continuously in the facing direction. |
| FastStop | `movement/FastStopModule.java` | 1 | - | - | Stops movement immediately when keys are released. |
| Sprint | `movement/SprintModule.java` | 1 | - | - | Automatically sprint while moving. |

### Player

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AutoArmor | `player/AutoArmorModule.java` | 3 | - | - | Equips the best armour carried into empty or worn slots. |
| AutoEat | `player/AutoEatModule.java` | 3 | - | - | Eats food automatically when hunger is low. |
| AutoGapple | `player/AutoGappleModule.java` | 4 | - | - | Eats a golden apple when health is low. |
| AutoMend | `player/AutoMendModule.java` | 3 | - | - | Mends damaged armour with experience bottles when safe. |
| AutoRefill | `player/AutoRefillModule.java` | 7 | - | - | Tops up the hotbar with obsidian, crystals, gapples and pearls. |
| DurabilityAlert | `player/DurabilityAlertModule.java` | 5 | - | - | Warns when an item is nearly broken. |
| FastPlace | `player/FastPlaceModule.java` | 1 | - | - | Reduces the local item-use cooldown. |
| Offhand | `player/OffhandModule.java` | 3 | - | - | Keeps a chosen item in the offhand slot. |

### Render

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| BaritoneRoute | `render/BaritoneRouteModule.java` | 2 | - | - | Marks the active Baritone goal in the world. |
| BedEsp | `render/BedEspModule.java` | 4 | - | - | Highlights nearby beds. |
| BlockEsp | `render/BlockEspModule.java` | 5 | - | - | Highlights blocks matching a name fragment. |
| CrystalEsp | `render/CrystalEspModule.java` | 4 | - | - | Highlights end crystals with damage estimates. |
| EntityEsp | `render/EntityEspModule.java` | 9 | - | - | Outlines entities with health, armour and distance. |
| Fullbright | `render/FullbrightModule.java` | 1 | - | - | Raises local brightness. |
| HoleEsp | `render/HoleEspModule.java` | 3 | - | - | Highlights safe holes, colour coded by strength. |
| ItemEsp | `render/ItemEspModule.java` | 7 | - | - | Outlines dropped items with a name label. |
| MobEsp | `render/MobEspModule.java` | 6 | - | - | Outlines nearby mobs with a health label. |
| PlayerCosmetics | `render/PlayerCosmeticsModule.java` | 3 | - | - | Optional pastel accessories on players. |
| PlayerEsp | `render/PlayerEspModule.java` | 8 | - | - | Outlines other players with name and health labels. |
| PortalEsp | `render/PortalEspModule.java` | 4 | - | - | Highlights nether portal blocks. |
| ProjectileEsp | `render/ProjectileEspModule.java` | 5 | - | - | Outlines projectiles in flight. |
| Search | `render/SearchModule.java` | 4 | - | - | Finds nearby blocks matching a name fragment. |
| StorageEsp | `render/StorageEspModule.java` | 4 | - | - | Outlines nearby storage containers. |
| Tracers | `render/TracersModule.java` | 4 | - | - | Directional indicators toward players and crystals. |
| WaypointEsp | `render/WaypointEspModule.java` | 3 | - | - | Marks saved waypoints in the world. |

### HUD

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| Armor | `hud/ArmorModule.java` | 0 | - | - | Shows armour points and durability. |
| Biome | `hud/BiomeModule.java` | 0 | - | - | Shows the current biome. |
| ChestEsp | `render/ChestEspModule.java` | 1 | - | - | Outlines nearby storage containers with a type label. |
| Clock | `hud/ClockModule.java` | 0 | - | - | Shows the local time of day. |
| CombatStats | `hud/CombatStatsModule.java` | 0 | - | - | Shows crystal damage and lethality. |
| Coords | `hud/CoordsModule.java` | 0 | - | - | Shows the coordinates readout. |
| CrosshairInfo | `hud/CrosshairModule.java` | 0 | - | - | Shows the block under the crosshair. |
| CrystalDamage | `hud/CrystalDamageModule.java` | 0 | - | - | Shows crystal damage indicators. |
| Direction | `hud/DirectionModule.java` | 0 | - | - | Shows the facing direction and yaw. |
| EnderChestEsp | `render/EnderChestEspModule.java` | 0 | - | - | Outlines ender chests. |
| Entities | `hud/EntitiesModule.java` | 0 | - | - | Shows nearby entity counts. |
| EntityCounter | `hud/EntityCountModule.java` | 0 | - | - | Shows a count of nearby entities by category. |
| InventoryStatus | `hud/InventoryStatusModule.java` | 0 | - | - | Shows the inventory fill and free slots. |
| ItemCounts | `hud/ItemCountModule.java` | 0 | - | - | Shows crystal/obsidian/totem/pearl counts. |
| Keystrokes | `hud/KeystrokesModule.java` | 0 | - | - | Shows a WASD/mouse keystroke overlay. |
| Light | `hud/LightModule.java` | 0 | - | - | Shows the light level at the player. |
| ModuleList | `hud/ModuleListModule.java` | 0 | - | - | Shows the list of enabled modules. |
| Network | `hud/NetworkModule.java` | 0 | - | - | Shows the dimension and ping. |
| Performance | `hud/PerformanceModule.java` | 0 | - | - | Shows FPS and ping indicators. |
| PositionHistory | `hud/PositionHistoryModule.java` | 0 | - | - | Shows recent sampled positions. |
| Potion | `hud/PotionModule.java` | 0 | - | - | Shows active potion effects. |
| ServerInfo | `hud/ServerInfoModule.java` | 0 | - | - | Shows the server address and ping. |
| SessionStats | `hud/SessionModule.java` | 0 | - | - | Shows session time and distance walked. |
| ShulkerEsp | `render/ShulkerEspModule.java` | 0 | - | - | Outlines shulker boxes. |
| SpawnerEsp | `render/SpawnerEspModule.java` | 1 | - | - | Outlines spawners (and optionally beacons, signs and lecterns). |
| Speed | `hud/SpeedModule.java` | 0 | - | - | Shows the player's speed in blocks per second. |
| TargetHud | `hud/TargetHudModule.java` | 0 | - | - | Shows the pastel target HUD. |
| Time | `hud/TimeModule.java` | 0 | - | - | Shows the in-game time of day. |
| TotemCount | `hud/TotemCountModule.java` | 0 | - | - | Shows the held totem count. |
| Watermark | `hud/WatermarkModule.java` | 0 | - | - | Shows the LOGVEX CRYSTAL watermark. |

### Misc

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AIAssistant | `misc/AIAssistantModule.java` | 21 | - | - | Optional local AI: crash intelligence, Baritone help and diagnostics. |
| AntiAfk | `misc/AntiAfkModule.java` | 3 | - | - | Small periodic actions to avoid idle kicks. |
| AutoReconnect | `misc/AutoReconnectModule.java` | 2 | - | - | Reconnects to the last server after a disconnect. |
| Baritone | `misc/BaritoneModule.java` | 2 | - | - | Baritone navigation diagnostics, controls and HUD readout. |
| ChestLogger | `misc/ChestLoggerModule.java` | 10 | - | - | Counts chests in loaded chunks and clusters them locally. |
| CommandCenter | `misc/CommandCenterModule.java` | 1 | - | - | Unified client status, compatibility and performance hub. |
| Diagnostics | `misc/DiagnosticsModule.java` | 3 | - | - | Reports client health: errors, files, performance, version. |
| HudEditor | `misc/HudEditorModule.java` | 0 | - | - | Open the drag-and-drop HUD layout editor. |
| Theme | `misc/ThemeModule.java` | 5 | - | - | Live appearance controls for the Crystal interface. |

### World

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| BuildPlanner | `world/BuildPlannerModule.java` | 10 | - | - | Schematic viewer, placement preview, material calculator and build progress. |
| Navigator | `world/NavigatorModule.java` | 2 | - | - | Compass and route readout; navigation planning stays separate from movement. |
| WorldInfo | `world/WorldInfoModule.java` | 23 | - | - | A configurable world-information panel and the ; world command. |
| WorldScanner | `world/WorldScannerModule.java` | 12 | - | - | Autonomous world mapping: route planning, structure classification and a persistent map. |

### Automation

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AfkGuard | `automation/AfkGuardAutomation.java` | 4 | - | - | Detects idleness and optionally prevents kicks. |
| AutoArmor+ | `automation/AutoArmorAutomation.java` | 4 | - | - | Equips the best armour carried by its real armour attribute. |
| AutoEat+ | `automation/AutoEatAutomation.java` | 5 | - | - | Eats the best food by nutrition, pulling from the backpack when needed. |
| AutoGG | `automation/AutoGgAutomation.java` | 3 | - | - | Sends a configurable message after a match or death. |
| AutoJump | `automation/AutoJumpAutomation.java` | 3 | - | - | Jumps one-block steps while moving. |
| AutoPotion | `automation/AutoPotionAutomation.java` | 4 | - | - | Drinks a potion for a chosen effect when it lapses. |
| AutoRefill+ | `automation/AutoRefillAutomation.java` | 7 | - | - | Tops up the hotbar from the backpack with a configurable list. |
| AutoReplenish | `automation/AutoReplenishAutomation.java` | 3 | - | - | Tops up a nearly-empty held stack from the backpack. |
| AutoRespawn | `automation/AutoRespawnAutomation.java` | 2 | - | - | Respawns automatically after the death screen. |
| AutoSort | `automation/AutoSortAutomation.java` | 4 | - | - | Sorts the inventory by bucket, name, count or durability. |
| AutoSprint | `automation/AutoSprintAutomation.java` | 4 | - | - | Sprints automatically with a hunger floor. |
| AutoSwim | `automation/AutoSwimAutomation.java` | 4 | - | - | Swims upward when submerged to avoid drowning. |
| AutoTool+ | `automation/AutoToolAutomation.java` | 3 | - | - | Selects the best tool for the targeted block. |
| AutoTotem+ | `automation/AutoTotemAutomation.java` | 2 | - | - | Keeps a totem in the offhand, refilling from the hotbar or backpack. |
| AutoTrash | `automation/AutoTrashAutomation.java` | 2 | - | - | Drops listed junk from the backpack, with protection. |
| AutoWeapon+ | `automation/AutoWeaponAutomation.java` | 3 | - | - | Holds the best weapon by real attack damage. |
| ContainerTransfer | `automation/ContainerTransferAutomation.java` | 3 | - | - | Moves stacks between the inventory and an open container. |
| HotbarPresets | `automation/HotbarPresetsAutomation.java` | 0 | - | - | Applies named hotbar layouts as one transaction. |
| RestockPlanner | `automation/RestockPlannerAutomation.java` | 1 | - | - | Plans and executes a restock against a target item list. |
| SafeWalk | `automation/SafeWalkAutomation.java` | 3 | - | - | Sneaks at the edge of a drop so you cannot fall. |
| SessionStats+ | `automation/SessionStatsAutomation.java` | 2 | - | - | Tracks playtime and distance for this session. |
| ShulkerOrganizer | `automation/ShulkerOrganizerAutomation.java` | 2 | - | - | Sorts the open shulker box or container by bucket. |

