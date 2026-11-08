package com.logvex.crystal.gui;

import com.logvex.crystal.module.world.WorldScannerModule;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.scan.world.ScanSnapshot;
import com.logvex.crystal.scan.world.ScanState;
import com.logvex.crystal.scan.world.StructureCategory;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The world-scanner dashboard: a live, Meteor-inspired panel over the scanner.
 *
 * <p>It shows the current scan state, dimension, explored area, discovery count, duration,
 * agent/flight status and processing throughput, and offers the controls the mission asks for -
 * start, pause, resume, stop, reset - plus a map legend by structure category and a recent
 * observations list. Every control is wired to a real {@link WorldScannerModule} method; there is
 * no decorative button.
 *
 * <p>The screen reads a {@link ScanSnapshot} once per frame, so it never sees a half-updated
 * scanner, and it draws with the same themed primitives as the rest of the client.
 */
public final class ScanDashboardScreen extends Screen {

    private static final int BUTTON_H = 16;
    private static final int ROW_H = 12;

    private final ThemeManager theme;
    private final WorldScannerModule module;

    // Hit boxes captured during render so clicks stay in step with what was drawn.
    private int btnX;
    private int btnY;
    private int btnW;
    private String status = "";
    private long statusAt;

    public ScanDashboardScreen(ThemeManager theme, WorldScannerModule module) {
        super(Text.literal("World Scanner"));
        this.theme = theme;
        this.module = module;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String message) {
        this.status = message == null ? "" : message;
        this.statusAt = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null || module == null) {
            return;
        }
        Palette palette = theme.palette();
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, 170));

        int left = 30;
        int top = 24;
        int right = width - 30;
        int bottom = height - 24;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2727 World Scanner", left + 14, top + 10, palette.primary());
        context.drawTextWithShadow(textRenderer, "autonomous mapping \u00b7 local only \u00b7 Esc closes",
                left + 14, top + 22, palette.textDim());

        ScanSnapshot snapshot = module.snapshot();
        int infoTop = top + 40;
        renderInfo(context, palette, left + 14, infoTop, snapshot);

        // Control buttons.
        btnY = bottom - 30;
        btnX = left + 14;
        btnW = 62;
        int bx = btnX;
        boolean running = module.isScanning();
        boolean paused = snapshot != null && snapshot.state() == ScanState.PAUSED;
        drawButton(context, palette, bx, btnY, btnW, "Start", !running, mouseX, mouseY);
        bx += btnW + 6;
        drawButton(context, palette, bx, btnY, btnW, "Pause", running && !paused, mouseX, mouseY);
        bx += btnW + 6;
        drawButton(context, palette, bx, btnY, btnW, "Resume", paused, mouseX, mouseY);
        bx += btnW + 6;
        drawButton(context, palette, bx, btnY, btnW, "Stop", running, mouseX, mouseY);
        bx += btnW + 6;
        drawButton(context, palette, bx, btnY, 70, "Reset", true, mouseX, mouseY);

        int panelTop = infoTop;
        int panelBottom = btnY - 12;
        int midX = (left + right) / 2;
        renderLegend(context, palette, midX + 10, panelTop, right - 14, panelBottom);
        renderRecent(context, palette, left + 14, panelTop + 92, midX - 10, panelBottom);

        String footer = !status.isEmpty() && System.currentTimeMillis() - statusAt < 4000
                ? status
                : "map " + module.mapper().totalSize() + " chunks \u00b7 "
                        + module.database().sessions().size() + " sessions";
        context.drawTextWithShadow(textRenderer, footer, left + 14, bottom - 16,
                !status.isEmpty() ? palette.primary() : palette.textDim());

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderInfo(DrawContext context, Palette palette, int x, int y, ScanSnapshot snapshot) {
        int col2 = x + 200;
        if (snapshot == null) {
            context.drawTextWithShadow(textRenderer, "scanner not initialised", x, y, palette.textDim());
            return;
        }
        int leftY = y;
        int rightY = y;
        context.drawTextWithShadow(textRenderer, "state " + snapshot.state().label(), x, leftY, palette.text());
        leftY += ROW_H;
        context.drawTextWithShadow(textRenderer, "dimension " + shortDimension(snapshot.dimension()), x, leftY,
                palette.text());
        leftY += ROW_H;
        context.drawTextWithShadow(textRenderer, "explored " + snapshot.exploredChunks() + " chunks", x, leftY,
                palette.text());
        leftY += ROW_H;
        context.drawTextWithShadow(textRenderer, "discoveries " + snapshot.discoveries(), x, leftY, palette.text());
        leftY += ROW_H;
        context.drawTextWithShadow(textRenderer, "duration " + formatDuration(snapshot.durationMillis()), x, leftY,
                palette.text());

        context.drawTextWithShadow(textRenderer, "agent " + module.scanner().agent().visitedCount() + " visited", col2,
                rightY, palette.text());
        rightY += ROW_H;
        context.drawTextWithShadow(textRenderer, "flight " + snapshot.flightStatus(), col2, rightY, palette.text());
        rightY += ROW_H;
        context.drawTextWithShadow(textRenderer, "movement " + snapshot.movementStatus(), col2, rightY, palette.text());
        rightY += ROW_H;
        context.drawTextWithShadow(textRenderer, "queued " + snapshot.pendingClassifications(), col2, rightY,
                palette.text());
        rightY += ROW_H;
        context.drawTextWithShadow(textRenderer, "perf " + String.format("%.1f", snapshot.samplesPerSecond())
                + " chunks/s \u00b7 " + (snapshot.averageTickNanos() / 1000) + " \u00b5s/tick", col2, rightY,
                palette.text());

        // Progress bar under the info block.
        int barY = y + ROW_H * 5 + 4;
        int barW = 380;
        RenderUtil.roundedPanel(context, x, barY, barW, 8, palette.radius(3), palette.card(), palette.borderSoft());
        int filled = (int) (barW * snapshot.progress());
        if (filled > 0) {
            RenderUtil.roundedRect(context, x, barY, filled, 8, palette.radius(3), palette.primary());
        }
        context.drawTextWithShadow(textRenderer, Math.round(snapshot.progress() * 100) + "%", x + barW + 8, barY - 2,
                palette.textDim());
    }

    private void renderLegend(DrawContext context, Palette palette, int x, int top, int right, int bottom) {
        context.drawTextWithShadow(textRenderer, "map legend", x, top, palette.secondary());
        int y = top + 12;
        Map<StructureCategory, Integer> counts = module.mapper().categoryCounts(shortDimension(
                module.snapshot() == null ? "" : module.snapshot().dimension()));
        if (counts.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "no chunks mapped yet", x, y, palette.textDim());
            return;
        }
        for (Map.Entry<StructureCategory, Integer> entry : counts.entrySet()) {
            if (y + ROW_H > bottom) {
                break;
            }
            RenderUtil.roundedRect(context, x, y + 2, 8, 8, palette.radius(2), categoryColor(palette, entry.getKey()));
            context.drawTextWithShadow(textRenderer, entry.getKey().label() + "  " + entry.getValue(), x + 14, y,
                    palette.text());
            y += ROW_H;
        }
    }

    private void renderRecent(DrawContext context, Palette palette, int x, int top, int right, int bottom) {
        context.drawTextWithShadow(textRenderer, "recent observations", x, top - 12, palette.secondary());
        List<com.logvex.crystal.scan.world.MappedChunk> recent = module.mapper().recent(
                shortDimension(module.snapshot() == null ? "" : module.snapshot().dimension()), 12);
        if (recent.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "nothing observed yet", x, top, palette.textDim());
            return;
        }
        int y = top;
        for (com.logvex.crystal.scan.world.MappedChunk chunk : recent) {
            if (y + ROW_H > bottom) {
                break;
            }
            String text = String.format("%d,%d  %s  %d%%", chunk.sample().chunkX(), chunk.sample().chunkZ(),
                    chunk.category().label(), Math.round(chunk.confidence() * 100));
            context.drawTextWithShadow(textRenderer, text, x, y,
                    chunk.manual() ? palette.primary() : palette.text());
            y += ROW_H;
        }
    }

    private int categoryColor(Palette palette, StructureCategory category) {
        return switch (category) {
            case BUILDING, STORAGE -> palette.primary();
            case FARM -> palette.enabled();
            case REDSTONE, PORTAL -> palette.danger();
            case MOB_FARM, UNDERGROUND, MINE, ABANDONED -> palette.secondary();
            case NATURAL -> palette.tertiary();
            case OTHER -> palette.textDim();
        };
    }

    private void drawButton(DrawContext context, Palette palette, int x, int y, int w, String label,
                            boolean enabled, int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + BUTTON_H;
        int fill = !enabled ? palette.card() : hovered ? palette.panelHover() : palette.panel();
        RenderUtil.roundedPanel(context, x, y, w, BUTTON_H, palette.radius(4), fill,
                enabled ? palette.primary() : palette.borderSoft());
        int textX = x + (w - textRenderer.getWidth(label)) / 2;
        context.drawTextWithShadow(textRenderer, label, textX, y + 4, enabled ? palette.text() : palette.textDim());
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
    public boolean mouseClicked(Click click, boolean doubled) {
        if (module == null) {
            return super.mouseClicked(click, doubled);
        }
        int x = (int) click.x();
        int y = (int) click.y();
        if (y >= btnY && y <= btnY + BUTTON_H) {
            int index = (x - btnX) / (btnW + 6);
            if (index >= 0 && index <= 4) {
                switch (index) {
                    case 0 -> {
                        module.start();
                        setStatus("start requested");
                    }
                    case 1 -> {
                        module.pause();
                        setStatus("paused");
                    }
                    case 2 -> {
                        module.resume();
                        setStatus("resumed");
                    }
                    case 3 -> {
                        module.stop();
                        setStatus("stopped");
                    }
                    case 4 -> {
                        module.resetSession();
                        setStatus("session reset");
                    }
                    default -> {
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }
}
