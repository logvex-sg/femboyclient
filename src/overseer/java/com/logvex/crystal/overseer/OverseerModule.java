package com.logvex.crystal.overseer;

import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;

/**
 * The Overseer control module. It exists only in the Overseer edition and is the switch the
 * administration feature reads: when it is on, the controller listens and the fleet HUD is shown.
 *
 * <p>Keeping the state in a module (rather than in the feature) means the user toggles it from the
 * normal ClickGUI, its settings persist with the rest of the config, and the feature stays a thin
 * wiring layer.
 */
public final class OverseerModule extends Module {

    private final IntSetting port = add("Port", "TCP port the controller listens on (0 picks a free port).", 0, 0, 65535);
    private final BooleanSetting showHud = add("Hud", "Show the fleet status on the HUD.", true);
    private final BooleanSetting announce = add("Announce", "Notify when an instance connects or disconnects.", true);

    public OverseerModule() {
        super(Category.MISC, "Overseer",
                "Overseer edition: administers paired Crystal instances over the local network.");
    }

    /** The configured listen port (0 means "let the OS choose"). */
    public int port() {
        return port.value();
    }

    /** Whether the fleet readout is shown on the HUD. */
    public boolean showHud() {
        return showHud.value();
    }

    /** Whether connect/disconnect events raise a notification. */
    public boolean announce() {
        return announce.value();
    }
}
