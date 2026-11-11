package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.navigation.NavigationService;
import com.logvex.crystal.util.waypoint.Navigation;
import net.minecraft.client.MinecraftClient;

/**
 * The compass readout: the direction to the nearest waypoint in the current dimension, plus the
 * active route's progress when one is being followed.
 *
 * <p>It reads the shared {@link NavigationService} and the waypoint store, both of which are
 * already cached, so the element does no scanning of its own. When no route is active and no
 * waypoint is in range it renders nothing rather than an empty box.
 */
public final class CompassHudElement implements HudElement {

    public static final String ID = "compass";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, MinecraftClient client,
                       float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        var palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        String[] lines = lines(client);
        if (lines.length == 0) {
            return;
        }
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length,
                rightAligned, true);
        for (String line : lines) {
            HudText.line(context, client.textRenderer, line, x, lineY, rightAligned, palette.text());
            lineY += HudText.LINE;
        }
    }

    private String[] lines(MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        if (client.player == null || client.world == null || bootstrap == null
                || bootstrap.navigation() == null) {
            return new String[0];
        }
        NavigationService navigation = bootstrap.navigation();
        java.util.List<String> lines = new java.util.ArrayList<>();
        var state = navigation.state();
        if (state.following() || state.arrived()) {
            lines.add("route " + navigation.describe(client));
        }
        var pos = client.player.getEntityPos();
        String dimension = client.world.getRegistryKey().getValue().toString();
        var visible = bootstrap.waypoints().nearest(dimension, pos.x, pos.y, pos.z);
        if (!visible.isEmpty()) {
            var waypoint = visible.get(0);
            Navigation.Bearing bearing = Navigation.bearing(pos.x, pos.z, waypoint.x(), waypoint.z());
            String relative = Navigation.relative(client.player.getYaw(), bearing);
            lines.add(bearing.label() + " \u00b7 " + waypoint.name() + " (" + relative + ")");
        }
        return lines.toArray(new String[0]);
    }

    @Override
    public int height(MinecraftClient client) {
        return lines(client).length * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 110;
    }
}
