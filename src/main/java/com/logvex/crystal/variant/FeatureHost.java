package com.logvex.crystal.variant;

import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.theme.ThemeManager;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * The narrow surface a {@link ClientFeature} may use. It is deliberately small: a feature gets
 * the client's public services and the ability to open a screen and raise a notification, and
 * nothing else. Passing this instead of the whole client keeps the core's internals private and
 * makes the feature boundary explicit.
 */
public interface FeatureHost {

    /** The logger name to use for feature diagnostics. */
    String loggerName();

    /** Which build is running. */
    Variant variant();

    /** This client's mod version. */
    String modVersion();

    /** The running Minecraft version. */
    String minecraftVersion();

    /** Raises a notification (warning level when {@code warning} is true). */
    void notify(String message, boolean warning);

    /** Opens a screen on the client thread, if fonts are ready. */
    void openScreen(Screen screen);

    /** The module manager, so a feature can add its own modules. */
    ModuleManager modules();

    /** The theme manager, so a feature's UI matches the active palette. */
    ThemeManager theme();

    /** The HUD manager, so a feature can add its own HUD elements. */
    com.logvex.crystal.gui.HudManager hud();

    /** The command manager, so a feature can add its own commands. */
    com.logvex.crystal.command.CommandManager commands();

    /** The config directory, for a feature's own persisted state. */
    Path configDirectory();

    /** Convenience: the running client, for feature code that must reach a client service. */
    default MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    /** Convenience: the feature's own config file under the client config directory. */
    default Path featureConfig(String name) {
        return ConfigPaths.directory().resolve(name);
    }
}
