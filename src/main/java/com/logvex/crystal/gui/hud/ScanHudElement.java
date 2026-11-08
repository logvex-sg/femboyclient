package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.world.WorldScannerModule;
import com.logvex.crystal.scan.world.ScanSnapshot;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The world-scanner HUD panel: scan state and progress, current dimension, explored area,
 * discovery count, scan duration, agent/flight status and processing throughput.
 *
 * <p>It owns no logic: it asks the {@link WorldScannerModule} for a {@link ScanSnapshot} and draws
 * it. The panel is hidden while the module is disabled or no run is active, so it never occupies
 * HUD space for a user who is not scanning.
 */
public final class ScanHudElement implements HudElement {

    @Override
    public String id() {
        return "scan";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    private WorldScannerModule module() {
        CrystalClient bootstrap = CrystalClient.get();
        return bootstrap == null ? null : bootstrap.modules().byClass(WorldScannerModule.class);
    }

    private String[] lines(MinecraftClient client) {
        WorldScannerModule module = module();
        if (module == null || !module.isEnabled()) {
            return new String[0];
        }
        ScanSnapshot snapshot = module.snapshot();
        if (snapshot == null) {
            return new String[0];
        }
        if (snapshot.state().name().equals("IDLE") && snapshot.exploredChunks() == 0) {
            return new String[0];
        }
        return new String[]{
                "scan " + snapshot.state().label() + " " + Math.round(snapshot.progress() * 100) + "%",
                "dim " + shortDimension(snapshot.dimension()),
                "explored " + snapshot.exploredChunks() + " chunks",
                "finds " + snapshot.discoveries(),
                "time " + formatDuration(snapshot.durationMillis()),
                "flight " + snapshot.flightStatus(),
                "move " + snapshot.movementStatus(),
                "perf " + String.format("%.0f", snapshot.samplesPerSecond()) + " chunks/s \u00b7 "
                        + (snapshot.averageTickNanos() / 1000) + " \u00b5s/tick"
        };
    }

    private static String shortDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "-";
        }
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return;
        }
        String[] lines = lines(client);
        if (lines.length == 0) {
            return;
        }
        Palette palette = bootstrap.theme().palette();
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length, rightAligned, true);
        for (String line : lines) {
            HudText.line(context, client.textRenderer, line, x, lineY, rightAligned, palette.text());
            lineY += HudText.LINE;
        }
    }

    @Override
    public int height(MinecraftClient client) {
        return lines(client).length * HudText.LINE + 6;
    }
}
