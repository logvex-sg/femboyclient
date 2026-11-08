# LOGVEX CRYSTAL - Module Implementation Matrix

> 71 registered modules (`ModuleRegistry.EXPECTED_MODULE_COUNT`, asserted by
> `ModuleRegistryTest`). Generated from the source at commit `5da1ad7`; count refreshed
> in the client-intelligence pass.
>
> **Status key**
> - **Verified** - real implementation wired into the tick/render path; pure logic has unit
>   coverage and the module is exercised by the headless smoke run.
> - **Implemented, unverified** - code is complete and builds, but the behaviour depends on a
>   live server/world and is not covered by an automated test.
> - **Partial** - only part of the documented behaviour is implemented.
>
> Every module below is **implemented** (no placeholder/empty modules exist). Modules marked
> *unverified* still need in-game validation; none is a stub.
>
> Settings column = number of constructor `add(...)` declarations (all validated and persisted).

### Combat

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AutoCrystal | `combat/AutoCrystalModule.java` | 10 | - | - | Places, detonates and manages end crystals against a target. |
| AutoTotem | `combat/AutoTotemModule.java` | 2 | - | - | Reports totem count and the nearest threat. |
| HoverTotem | `combat/HoverTotemModule.java` | 2 | - | - | Predicts self-crystal damage at your feet. |
| CritAssist | `combat/CritAssistModule.java` | 2 | - | - | Shows when you are falling for a critical hit. |
| Surround | `combat/SurroundModule.java` | 3 | - | - | Walls the player in with obsidian against crystals. |
| Burrow | `combat/BurrowModule.java` | 1 | - | - | Places a block in your own space to escape. |
| AutoMine | `combat/AutoMineModule.java` | 4 | - | - | Mines blocks around a target to open them up. |
| CityMiner | `combat/CityMinerModule.java` | 3 | - | - | Mines the blocks trapping a target. |
| WebAura | `combat/WebAuraModule.java` | 4 | - | - | Places cobwebs on a target to slow them. |
| AutoPearl | `combat/AutoPearlModule.java` | 4 | - | - | Throws an ender pearl to escape lethal damage. |

### Movement

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| Sprint | `movement/SprintModule.java` | 1 | - | - | Automatically sprint while moving. |
| AutoWalk | `movement/AutoWalkModule.java` | 2 | - | - | Walks forward continuously in the facing direction. |
| FastStop | `movement/FastStopModule.java` | 1 | - | - | Stops movement immediately when keys are released. |

### Player

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AutoEat | `player/AutoEatModule.java` | 3 | - | - | Eats food automatically when hunger is low. |
| FastPlace | `player/FastPlaceModule.java` | 1 | - | - | Reduces the local item-use cooldown. |
| Offhand | `player/OffhandModule.java` | 3 | - | - | Keeps a chosen item in the offhand slot. |
| AutoArmor | `player/AutoArmorModule.java` | 3 | - | - | Equips the best armour carried into empty or worn slots. |
| AutoRefill | `player/AutoRefillModule.java` | 7 | - | - | Tops up the hotbar with obsidian, crystals, gapples and pearls. |
| AutoGapple | `player/AutoGappleModule.java` | 4 | - | - | Eats a golden apple when health is low. |
| AutoMend | `player/AutoMendModule.java` | 3 | - | - | Mends damaged armour with experience bottles when safe. |

### Render

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| Tracers | `render/TracersModule.java` | 4 | - | - | Directional indicators toward players and crystals. |
| Search | `render/SearchModule.java` | 4 | - | - | Finds nearby blocks matching a name fragment. |
| Fullbright | `render/FullbrightModule.java` | 1 | - | - | Raises local brightness. |
| EntityEsp | `render/EntityEspModule.java` | 9 | - | - | Outlines entities with health, armour and distance. |
| CrystalEsp | `render/CrystalEspModule.java` | 4 | - | - | Highlights end crystals with damage estimates. |
| StorageEsp | `render/StorageEspModule.java` | 4 | - | - | Outlines nearby storage containers. |
| HoleEsp | `render/HoleEspModule.java` | 3 | - | - | Highlights safe holes, colour coded by strength. |
| WaypointEsp | `render/WaypointEspModule.java` | 3 | - | - | Marks saved waypoints in the world. |
| PlayerEsp | `render/PlayerEspModule.java` | 8 | - | - | Outlines other players with name and health labels. |
| MobEsp | `render/MobEspModule.java` | 6 | - | - | Outlines nearby mobs with a health label. |
| ItemEsp | `render/ItemEspModule.java` | 7 | - | - | Outlines dropped items with a name label. |
| ProjectileEsp | `render/ProjectileEspModule.java` | 5 | - | - | Outlines projectiles in flight. |
| ChestEsp | `render/ChestEspModule.java` | 1 | - | - | Outlines nearby storage containers with a type label. |
| EnderChestEsp | `render/EnderChestEspModule.java` | 0 | - | - | Outlines ender chests. |
| ShulkerEsp | `render/ShulkerEspModule.java` | 0 | - | - | Outlines shulker boxes. |
| SpawnerEsp | `render/SpawnerEspModule.java` | 1 | - | - | Outlines spawners (and optionally beacons, signs and lecterns). |
| BedEsp | `render/BedEspModule.java` | 4 | - | - | Highlights nearby beds. |
| PortalEsp | `render/PortalEspModule.java` | 4 | - | - | Highlights nether portal blocks. |
| BlockEsp | `render/BlockEspModule.java` | 5 | - | - | Highlights blocks matching a name fragment. |
| PlayerCosmetics | `render/PlayerCosmeticsModule.java` | 3 | - | - | Optional pastel accessories on players. |
| BaritoneRoute | `render/BaritoneRouteModule.java` | 2 | - | - | Marks the active Baritone goal in the world (beam + distance label). |

### HUD

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| Watermark | `hud/WatermarkModule.java` | 0 | - | - | Shows the LOGVEX CRYSTAL watermark. |
| ModuleList | `hud/ModuleListModule.java` | 0 | - | - | Shows the list of enabled modules. |
| TargetHud | `hud/TargetHudModule.java` | 0 | - | - | Shows the pastel target HUD. |
| CrystalDamage | `hud/CrystalDamageModule.java` | 0 | - | - | Shows crystal damage indicators. |
| Coords | `hud/CoordsModule.java` | 0 | - | - | Shows the coordinates readout. |
| Performance | `hud/PerformanceModule.java` | 0 | - | - | Shows FPS and ping indicators. |
| Biome | `hud/BiomeModule.java` | 0 | - | - | Shows the current biome. |
| Time | `hud/TimeModule.java` | 0 | - | - | Shows the in-game time of day. |
| Light | `hud/LightModule.java` | 0 | - | - | Shows the light level at the player. |
| Network | `hud/NetworkModule.java` | 0 | - | - | Shows the dimension and ping. |
| Entities | `hud/EntitiesModule.java` | 0 | - | - | Shows nearby entity counts. |
| Armor | `hud/ArmorModule.java` | 0 | - | - | Shows armour points and durability. |
| Potion | `hud/PotionModule.java` | 0 | - | - | Shows active potion effects. |
| Speed | `hud/SpeedModule.java` | 0 | - | - | Shows the player's speed in blocks per second. |
| Keystrokes | `hud/KeystrokesModule.java` | 0 | - | - | Shows a WASD/mouse keystroke overlay. |
| CrosshairInfo | `hud/CrosshairModule.java` | 0 | - | - | Shows the block under the crosshair. |
| SessionStats | `hud/SessionModule.java` | 0 | - | - | Shows session time and distance walked. |
| EntityCounter | `hud/EntityCountModule.java` | 0 | - | - | Shows a count of nearby entities by category. |

### Misc

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| AntiAfk | `misc/AntiAfkModule.java` | 3 | - | - | Small periodic actions to avoid idle kicks. |
| AutoReconnect | `misc/AutoReconnectModule.java` | 2 | - | - | Reconnects to the last server after a disconnect. |
| Theme | `misc/ThemeModule.java` | 5 | - | - | Live appearance controls for the Crystal interface. |
| HudEditor | `misc/HudEditorModule.java` | 0 | - | - | Open the drag-and-drop HUD layout editor. |
| ChestLogger | `misc/ChestLoggerModule.java` | 10 | - | - | Counts chests in loaded chunks and clusters them locally. |
| Diagnostics | `misc/DiagnosticsModule.java` | 3 | - | - | Reports client health: errors, files, performance, version. |
| CommandCenter | `misc/CommandCenterModule.java` | 1 | - | - | Reveals the compact Command Center HUD: status, compatibility and performance roll-up. |
| Baritone | `misc/BaritoneModule.java` | 2 | - | - | Baritone navigation diagnostics, controls and HUD readout. Optional: works without Baritone. |
| AIAssistant | `misc/AIAssistantModule.java` | 21 | - | - | Optional local AI: crash intelligence, Baritone help and bug reports. |

### Player

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| DurabilityAlert | `player/DurabilityAlertModule.java` | 5 | - | - | Warns when an item is nearly broken. |

### World

| Module | File | Settings | Deps | Conflicts | Description |
| --- | --- | ---: | --- | --- | --- |
| WorldInfo | `world/WorldInfoModule.java` | 22 | - | - | A configurable world-information panel and the ; world command. |
