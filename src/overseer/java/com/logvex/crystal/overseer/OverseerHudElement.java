package com.logvex.crystal.overseer;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The Overseer fleet readout: how many instances are paired and how many are reachable, with a
 * one-line summary of the most recent instance. It is only registered by the Overseer feature, so
 * the standard build has no such panel.
 */
final class OverseerHudElement implements HudElement {

    private static final int LINE = 10;

    private final OverseerFeature feature;

    OverseerHudElement(OverseerFeature feature) {
        this.feature = feature;
    }

    @Override
    public String id() {
        return "overseer_fleet";
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null || client.textRenderer == null) {
            return;
        }
        var controller = feature.controller();
        int paired = controller == null ? 0 : controller.instances().size();
        int online = controller == null ? 0 : controller.onlineCount();
        boolean listening = controller != null && controller.running();
        String[] lines = {
                "overseer " + (listening ? "listening" : "off"),
                "fleet " + online + "/" + paired + " online"
        };
        int width = width(client);
        int height = lines.length * LINE + 6;
        RenderUtil.softShadow(context, x, y, width, height, palette.radius(4), 60);
        RenderUtil.roundedPanel(context, x, y, width, height, palette.radius(4), palette.panel(), palette.borderSoft());
        RenderUtil.horizontalGradient(context, x + 2, y + 1, width - 4, 2,
                palette.accentGradientStart(), palette.accentGradientEnd());
        context.drawTextWithShadow(client.textRenderer, lines[0], x + 3, y + 4,
                listening ? palette.primary() : palette.textDim());
        context.drawTextWithShadow(client.textRenderer, lines[1], x + 3, y + 4 + LINE,
                online > 0 ? palette.secondary() : palette.textDim());
        if (bootstrap.theme().settings().decorations) {
            RenderUtil.paw(context, x + width + 4, y + 3, 1, palette.tertiary());
        }
    }

    @Override
    public int height(MinecraftClient client) {
        return 2 * LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        return 96;
    }
}
