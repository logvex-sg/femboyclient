package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.world.WorldScannerModule;
import com.logvex.crystal.scan.world.DiscoveryDatabase;
import com.logvex.crystal.scan.world.ScanSnapshot;
import com.logvex.crystal.scan.world.WorldIntelligence;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The world-intelligence HUD readout: recorded discoveries, confirmed versus uncertain, region
 * progress and cluster-target status.
 *
 * <p>It complements {@link ScanHudElement}, which shows the live scan run. This one shows what the
 * intelligence layer has learned and kept, so a player can see the durable result rather than just
 * the current run. It reads only cached counters, so it never triggers classification or I/O while
 * rendering, and it hides itself until the intelligence layer has recorded something.
 */
public final class IntelligenceHudElement implements HudElement {

    public static final String ID = "world-intelligence";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    private WorldIntelligence intelligence() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return null;
        }
        WorldScannerModule module = bootstrap.modules().byClass(WorldScannerModule.class);
        return module == null ? null : module.intelligence();
    }

    private String[] lines(MinecraftClient client) {
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            return new String[0];
        }
        DiscoveryDatabase database = intelligence.database();
        int total = database.size();
        int discoveries = database.discoveries(intelligence.dimension()).size();
        int uncertain = total - discoveries;
        if (total == 0) {
            return new String[0];
        }
        String dimension = shortDimension(intelligence.dimension());
        String[] lines = new String[]{
                "\u2727 intelligence",
                "dim " + dimension,
                "recorded " + total,
                "finds " + discoveries + " \u00b7 guesses " + uncertain,
                "regions " + intelligence.regions().touchedRegions(intelligence.dimension())
                        + " \u00b7 complete " + intelligence.regions().completeRegions(intelligence.dimension()),
                "session " + intelligence.observationsThisSession() + " obs (" + intelligence.newThisSession()
                        + " new)"
        };
        ScanSnapshot snapshot = intelligence.scanner().snapshot();
        if (snapshot != null && snapshot.state().name().equals("EXPLORING")) {
            lines = append(lines, "moving \u00b7 " + intelligence.baritone().statusLine());
        }
        return lines;
    }

    private static String[] append(String[] base, String extra) {
        String[] result = new String[base.length + 1];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = extra;
        return result;
    }

    private static String shortDimension(String dimension) {
        if (dimension == null || dimension.isEmpty()) {
            return "-";
        }
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
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
