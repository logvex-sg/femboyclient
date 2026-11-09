package com.logvex.crystal.commandcenter.quick;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.commandcenter.ClientCommandCenterHost;
import com.logvex.crystal.commandcenter.CommandCenterService;
import com.logvex.crystal.gui.ScreenGuard;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.notification.NotificationLevel;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;

/**
 * The production {@link QuickActionHandler}: it maps each {@link QuickActionType} onto the
 * client operation that already implements it.
 *
 * <p>Like {@link ClientCommandCenterHost}, it is deliberately a thin forwarder with no policy
 * of its own - the engine decides when an action runs, this class only decides how. It reuses
 * the same seams (the module manager, the profile service, the Command Center host, the GUI)
 * so a quick action can never do something the user could not also do by hand.
 */
public final class ClientQuickActionHandler implements QuickActionHandler {

    private final CrystalClient client;

    public ClientQuickActionHandler(CrystalClient client) {
        this.client = client;
    }

    @Override
    public String execute(QuickAction action) {
        switch (action.type()) {
            case OPEN_WORKSPACE:
                openWorkspace(action.argument());
                return "opened the " + action.argument() + " workspace";
            case SHOW_DIAGNOSTIC: {
                String message = action.message().isBlank() ? action.name() : action.message();
                client.notify(message, NotificationLevel.WARN);
                return "showed a diagnostic";
            }
            case OPEN_CLICKGUI:
                if (ScreenGuard.fontsReady() && client.clickGui() != null) {
                    client.clickGui().open();
                    return "opened the module GUI";
                }
                return "the GUI is not ready yet";
            case TOGGLE_MODULE: {
                Module module = client.modules().byName(action.argument());
                if (module == null) {
                    return "no module named " + action.argument();
                }
                module.toggle(client.modules());
                return (module.isEnabled() ? "enabled " : "disabled ") + module.name();
            }
            case ENABLE_MODULE: {
                Module module = client.modules().byName(action.argument());
                if (module == null) {
                    return "no module named " + action.argument();
                }
                module.enable(client.modules());
                return "enabled " + module.name();
            }
            case DISABLE_MODULE: {
                Module module = client.modules().byName(action.argument());
                if (module == null) {
                    return "no module named " + action.argument();
                }
                module.disable();
                return "disabled " + module.name();
            }
            case SWITCH_PROFILE:
                return client.profiles().apply(action.argument())
                        ? "switched to profile " + action.argument()
                        : "no profile named " + action.argument();
            case RUN_ACTION: {
                CommandCenterService service = client.commandCenter();
                if (service == null) {
                    return "the Command Center is unavailable";
                }
                var result = service.run(action.argument());
                return result.message();
            }
            case CLEAR_PERFORMANCE_HISTORY:
                if (client.performanceHistory() != null) {
                    client.performanceHistory().clear();
                    return "cleared the performance history";
                }
                return "no performance history";
            default:
                return "unsupported action type";
        }
    }

    @Override
    public void requestConfirmation(QuickAction action) {
        client.notify("\u2661 " + action.name() + " is waiting \u2014 confirm it in the Command Center",
                NotificationLevel.INFO);
    }

    private void openWorkspace(String name) {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft == null) {
            return;
        }
        com.logvex.crystal.commandcenter.ui.CommandCenterPreferences.Workspace workspace =
                com.logvex.crystal.commandcenter.ui.CommandCenterPreferences.Workspace.parse(
                        name == null ? "" : name.toUpperCase(Locale.ROOT));
        minecraft.setScreen(new com.logvex.crystal.gui.CommandCenterScreen(client, workspace));
    }
}
