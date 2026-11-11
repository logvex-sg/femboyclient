package com.logvex.crystal.module;

import com.logvex.crystal.module.combat.AutoCrystalModule;
import com.logvex.crystal.module.combat.AutoMineModule;
import com.logvex.crystal.module.combat.AutoPearlModule;
import com.logvex.crystal.module.combat.AutoTotemModule;
import com.logvex.crystal.module.combat.BurrowModule;
import com.logvex.crystal.module.combat.CityMinerModule;
import com.logvex.crystal.module.combat.CritAssistModule;
import com.logvex.crystal.module.combat.HoverTotemModule;
import com.logvex.crystal.module.combat.SurroundModule;
import com.logvex.crystal.module.combat.WebAuraModule;
import com.logvex.crystal.module.hud.CoordsModule;
import com.logvex.crystal.module.hud.CrystalDamageModule;
import com.logvex.crystal.module.hud.ModuleListModule;
import com.logvex.crystal.module.hud.PerformanceModule;
import com.logvex.crystal.module.hud.TargetHudModule;
import com.logvex.crystal.module.hud.WatermarkModule;
import com.logvex.crystal.module.misc.AntiAfkModule;
import com.logvex.crystal.module.misc.AutoReconnectModule;
import com.logvex.crystal.module.render.CrystalEspModule;
import com.logvex.crystal.module.render.EntityEspModule;
import com.logvex.crystal.module.render.FullbrightModule;
import com.logvex.crystal.module.render.HoleEspModule;
import com.logvex.crystal.module.render.StorageEspModule;
import com.logvex.crystal.module.render.WaypointEspModule;
import com.logvex.crystal.module.render.PlayerEspModule;
import com.logvex.crystal.module.render.MobEspModule;
import com.logvex.crystal.module.render.ItemEspModule;
import com.logvex.crystal.module.render.ProjectileEspModule;
import com.logvex.crystal.module.render.ChestEspModule;
import com.logvex.crystal.module.render.EnderChestEspModule;
import com.logvex.crystal.module.render.ShulkerEspModule;
import com.logvex.crystal.module.render.SpawnerEspModule;
import com.logvex.crystal.module.render.BedEspModule;
import com.logvex.crystal.module.render.PortalEspModule;
import com.logvex.crystal.module.render.BlockEspModule;
import com.logvex.crystal.module.render.PlayerCosmeticsModule;
import com.logvex.crystal.module.movement.AutoWalkModule;
import com.logvex.crystal.module.movement.FastStopModule;
import com.logvex.crystal.module.movement.SprintModule;
import com.logvex.crystal.module.player.AutoArmorModule;
import com.logvex.crystal.module.player.AutoEatModule;
import com.logvex.crystal.module.player.AutoGappleModule;
import com.logvex.crystal.module.player.AutoMendModule;
import com.logvex.crystal.module.player.AutoRefillModule;
import com.logvex.crystal.module.player.FastPlaceModule;
import com.logvex.crystal.module.player.OffhandModule;
import com.logvex.crystal.module.render.SearchModule;
import com.logvex.crystal.module.render.TracersModule;

/**
 * Single place where every module is registered.
 *
 * <p>Registration order is the order the ClickGUI lists modules within a category, so it
 * is grouped by feature rather than alphabetical. A test asserts the expected count so a
 * module cannot be silently dropped.
 */
public final class ModuleRegistry {

    /** Number of concrete modules registered; asserted by the registry test. */
    public static final int EXPECTED_MODULE_COUNT = 94;

    private ModuleRegistry() {
    }

    public static void registerAll(ModuleManager manager) {
        // Combat
        manager.register(new AutoCrystalModule());
        manager.register(new AutoTotemModule());
        manager.register(new HoverTotemModule());
        manager.register(new CritAssistModule());
        manager.register(new SurroundModule());
        manager.register(new BurrowModule());
        manager.register(new AutoMineModule());
        manager.register(new CityMinerModule());
        manager.register(new WebAuraModule());
        manager.register(new AutoPearlModule());
        // Movement
        manager.register(new SprintModule());
        manager.register(new AutoWalkModule());
        manager.register(new FastStopModule());
        // Player
        manager.register(new AutoEatModule());
        manager.register(new FastPlaceModule());
        manager.register(new OffhandModule());
        manager.register(new AutoArmorModule());
        manager.register(new AutoRefillModule());
        manager.register(new AutoGappleModule());
        manager.register(new AutoMendModule());
        // Render
        manager.register(new TracersModule());
        manager.register(new SearchModule());
        manager.register(new FullbrightModule());
        manager.register(new EntityEspModule());
        manager.register(new CrystalEspModule());
        manager.register(new StorageEspModule());
        manager.register(new HoleEspModule());
        manager.register(new WaypointEspModule());
        manager.register(new PlayerEspModule());
        manager.register(new MobEspModule());
        manager.register(new ItemEspModule());
        manager.register(new ProjectileEspModule());
        manager.register(new ChestEspModule());
        manager.register(new EnderChestEspModule());
        manager.register(new ShulkerEspModule());
        manager.register(new SpawnerEspModule());
        manager.register(new BedEspModule());
        manager.register(new PortalEspModule());
        manager.register(new BlockEspModule());
        manager.register(new PlayerCosmeticsModule());
        // HUD
        manager.register(new WatermarkModule());
        manager.register(new ModuleListModule());
        manager.register(new TargetHudModule());
        manager.register(new CrystalDamageModule());
        manager.register(new CoordsModule());
        manager.register(new PerformanceModule());
        manager.register(new com.logvex.crystal.module.hud.BiomeModule());
        manager.register(new com.logvex.crystal.module.hud.TimeModule());
        manager.register(new com.logvex.crystal.module.hud.LightModule());
        manager.register(new com.logvex.crystal.module.hud.NetworkModule());
        manager.register(new com.logvex.crystal.module.hud.EntitiesModule());
        manager.register(new com.logvex.crystal.module.hud.ArmorModule());
        manager.register(new com.logvex.crystal.module.hud.PotionModule());
        manager.register(new com.logvex.crystal.module.hud.SpeedModule());
        manager.register(new com.logvex.crystal.module.hud.KeystrokesModule());
        manager.register(new com.logvex.crystal.module.hud.CrosshairModule());
        manager.register(new com.logvex.crystal.module.hud.SessionModule());
        manager.register(new com.logvex.crystal.module.hud.EntityCountModule());
        // Misc
        manager.register(new AntiAfkModule());
        manager.register(new AutoReconnectModule());
        manager.register(new com.logvex.crystal.module.misc.ThemeModule());
        manager.register(new com.logvex.crystal.module.misc.HudEditorModule());
        manager.register(new com.logvex.crystal.module.misc.ChestLoggerModule());
        manager.register(new com.logvex.crystal.module.misc.DiagnosticsModule());
        manager.register(new com.logvex.crystal.module.misc.CommandCenterModule());
        manager.register(new com.logvex.crystal.module.misc.BaritoneModule());
        manager.register(new com.logvex.crystal.module.render.BaritoneRouteModule());
        manager.register(new com.logvex.crystal.module.player.DurabilityAlertModule());
        manager.register(new com.logvex.crystal.module.misc.AIAssistantModule());

        // World inspection.
        manager.register(new com.logvex.crystal.module.world.WorldInfoModule());
        manager.register(new com.logvex.crystal.module.world.WorldScannerModule());

        // World interaction and building toolkit.
        manager.register(new com.logvex.crystal.module.world.BuildPlannerModule());

        // Automation - inventory.
        manager.register(new com.logvex.crystal.module.automation.AutoTotemAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoArmorAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoWeaponAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoToolAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoRefillAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoReplenishAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoEatAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoPotionAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoSortAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoTrashAutomation());
        manager.register(new com.logvex.crystal.module.automation.ShulkerOrganizerAutomation());
        manager.register(new com.logvex.crystal.module.automation.HotbarPresetsAutomation());
        manager.register(new com.logvex.crystal.module.automation.ContainerTransferAutomation());
        manager.register(new com.logvex.crystal.module.automation.RestockPlannerAutomation());
        // Automation - player assistance.
        manager.register(new com.logvex.crystal.module.automation.AutoSprintAutomation());
        manager.register(new com.logvex.crystal.module.automation.SafeWalkAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoJumpAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoSwimAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoRespawnAutomation());
        manager.register(new com.logvex.crystal.module.automation.AutoGgAutomation());
        manager.register(new com.logvex.crystal.module.automation.AfkGuardAutomation());
        manager.register(new com.logvex.crystal.module.automation.SessionStatsAutomation());
    }
}
