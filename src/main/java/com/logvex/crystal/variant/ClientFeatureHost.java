package com.logvex.crystal.variant;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.config.ConfigPaths;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.theme.ThemeManager;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * The client's implementation of {@link FeatureHost}: a thin adapter over the core services, so a
 * {@link ClientFeature} never needs a reference to {@link CrystalClient} itself.
 */
public final class ClientFeatureHost implements FeatureHost {

    private final CrystalClient client;
    private final Variant variant;

    public ClientFeatureHost(CrystalClient client, Variant variant) {
        this.client = client;
        this.variant = variant;
    }

    @Override
    public String loggerName() {
        return "LogvexCrystal";
    }

    @Override
    public Variant variant() {
        return variant;
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer("logvex-crystal")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    @Override
    public String minecraftVersion() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc == null ? "" : mc.getGameVersion();
    }

    @Override
    public void notify(String message, boolean warning) {
        if (client.notifications() == null) {
            return;
        }
        if (warning) {
            client.notifications().warn(message);
        } else {
            client.notifications().cute(message);
        }
    }

    @Override
    public void openScreen(Screen screen) {
        if (screen != null && com.logvex.crystal.gui.ScreenGuard.fontsReady()) {
            MinecraftClient.getInstance().setScreen(screen);
        }
    }

    @Override
    public ModuleManager modules() {
        return client.modules();
    }

    @Override
    public ThemeManager theme() {
        return client.theme();
    }

    @Override
    public com.logvex.crystal.gui.HudManager hud() {
        return client.hud();
    }

    @Override
    public com.logvex.crystal.command.CommandManager commands() {
        return client.commands();
    }

    @Override
    public Path configDirectory() {
        return ConfigPaths.directory();
    }
}
