package com.logvex.crystal.module.world;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Navigator: reveals the compass HUD readout and reports the active route.
 *
 * <p>It owns no movement. The navigation engine follows a route as a cursor over your position;
 * this module only decides whether the compass panel is visible and keeps the player informed.
 * That separation is deliberate - turning the module off hides the readout but never touches a
 * route, and following a route never moves you.
 */
public final class NavigatorModule extends Module {

    private final BooleanSetting showCompass =
            add("Compass", "Show the compass and route readout.", true);
    private final BooleanSetting arrivalNotify =
            add("ArrivalNotify", "Announce arrival at a route's end.", true);

    public NavigatorModule() {
        super(Category.WORLD, "Navigator",
                "Compass and route readout; navigation planning stays separate from movement.");
    }

    @Override
    protected void onEnable() {
        context().hudManager().setElementVisible("compass", showCompass.value());
    }

    @Override
    protected void onDisable() {
        context().hudManager().setElementVisible("compass", false);
    }

    @Override
    protected void onTick() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null || bootstrap.navigation() == null) {
            return;
        }
        // Keep the compass visibility in sync with the setting while the module is on.
        context().hudManager().setElementVisible("compass", showCompass.value());
        if (arrivalNotify.value()) {
            return;
        }
        // When arrival notifications are off, clear the latch so a re-follow still works.
        bootstrap.navigation().state().clearArrival();
    }

    /** The line shown in the module status, from the shared navigation engine. */
    public String routeStatus(MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null || bootstrap.navigation() == null) {
            return "no route";
        }
        return bootstrap.navigation().describe(client);
    }
}
