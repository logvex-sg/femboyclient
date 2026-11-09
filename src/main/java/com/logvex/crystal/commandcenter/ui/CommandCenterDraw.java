package com.logvex.crystal.commandcenter.ui;

import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * The shared drawing vocabulary for the Command Center: buttons, cards, section headers,
 * wrapped text, sparklines, gauges and badges.
 *
 * <p>Every screen in the client already uses {@link RenderUtil} for the low-level shapes; this
 * class is the layer above it, so the Command Center's workspaces share one look and one place
 * to change it. It is deliberately stateless: every method takes the context, the palette and
 * the text renderer it needs, so a workspace can call it from any draw pass without hidden
 * coupling.
 */
public final class CommandCenterDraw {

    /** The standard control height, so rows and buttons line up across every workspace. */
    public static final int ROW_H = 18;
    public static final int BUTTON_H = 18;

    private CommandCenterDraw() {
    }

    /** A themed button. Returns nothing; the caller decides what a click does. */
    public static void button(DrawContext context, TextRenderer renderer, Palette palette, int x, int y,
                              int width, String label, boolean enabled, boolean hovered, boolean primary) {
        int fill;
        int border;
        if (!enabled) {
            fill = palette.card();
            border = palette.borderSoft();
        } else if (primary) {
            fill = hovered ? palette.panelHover() : palette.primary();
            border = palette.primary();
        } else {
            fill = hovered ? palette.panelHover() : palette.panel();
            border = palette.border();
        }
        RenderUtil.roundedPanel(context, x, y, width, BUTTON_H, palette.radius(4), fill, border);
        int textColor = !enabled ? palette.textDim()
                : primary ? palette.onPrimary() : palette.text();
        int textX = x + (width - renderer.getWidth(label)) / 2;
        context.drawTextWithShadow(renderer, label, textX, y + 5, textColor);
    }

    /** A card/panel with an optional accent header bar and a title. */
    public static void card(DrawContext context, TextRenderer renderer, Palette palette, int x, int y,
                            int width, int height, String title, String subtitle) {
        RenderUtil.softShadow(context, x, y, width, height, palette.radius(6), 60);
        RenderUtil.roundedPanel(context, x, y, width, height, palette.radius(6), palette.card(),
                palette.borderSoft());
        int tx = x + 8;
        int ty = y + 6;
        if (title != null && !title.isEmpty()) {
            context.drawTextWithShadow(renderer, title, tx, ty, palette.primary());
            ty += 11;
        }
        if (subtitle != null && !subtitle.isEmpty()) {
            context.drawTextWithShadow(renderer, subtitle, tx, ty, palette.textDim());
        }
    }

    /** A small pill badge, e.g. a health status or a category tag. */
    public static void badge(DrawContext context, TextRenderer renderer, Palette palette, int x, int y,
                             String label, int tint) {
        int w = renderer.getWidth(label) + 8;
        RenderUtil.roundedPanel(context, x, y, w, 11, palette.radius(3),
                ColorUtil.scaleAlpha(tint, 0.22f), tint);
        context.drawTextWithShadow(renderer, label, x + 4, y + 2, tint);
    }

    /**
     * Draws wrapped text, returning the y after the last line. Words longer than the width are
     * placed on their own line rather than clipped, so a long identifier stays readable.
     */
    public static int wrapped(DrawContext context, TextRenderer renderer, int x, int y, int maxWidth,
                              String text, int color) {
        if (text == null || text.isEmpty()) {
            return y;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (renderer.getWidth(candidate) > maxWidth && !current.isEmpty()) {
                    context.drawTextWithShadow(renderer, current.toString(), x, y, color);
                    y += 10;
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                context.drawTextWithShadow(renderer, current.toString(), x, y, color);
                y += 10;
            }
        }
        return y;
    }

    /** How many wrapped lines {@link #wrapped} would draw, without drawing them. */
    public static int wrappedHeight(TextRenderer renderer, int maxWidth, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 0;
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (renderer.getWidth(candidate) > maxWidth && !current.isEmpty()) {
                    lines++;
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines++;
            }
        }
        return lines * 10;
    }

    /** A horizontal gauge (a labelled progress bar) for a 0..1 fraction. */
    public static void gauge(DrawContext context, TextRenderer renderer, Palette palette, int x, int y,
                             int width, float fraction, String label, int tint) {
        float clamped = Math.max(0f, Math.min(1f, fraction));
        RenderUtil.roundedPanel(context, x, y, width, 10, palette.radius(3), palette.card(),
                palette.borderSoft());
        int filled = Math.round((width - 2) * clamped);
        if (filled > 0) {
            RenderUtil.roundedRect(context, x + 1, y + 1, filled, 8, palette.radius(3), tint);
        }
        if (label != null && !label.isEmpty()) {
            context.drawTextWithShadow(renderer, label, x + 4, y + 1, palette.text());
        }
    }

    /**
     * A sparkline chart of a metric snapshot: a filled baseline, a polyline of the samples and
     * min/max labels. Cheap - it draws one vertical bar per retained sample column.
     */
    public static void sparkline(DrawContext context, TextRenderer renderer, Palette palette, int x, int y,
                                 int width, int height, MetricSeries.Snapshot snapshot, int tint) {
        RenderUtil.roundedPanel(context, x, y, width, height, palette.radius(4), palette.card(),
                palette.borderSoft());
        if (snapshot == null || snapshot.isEmpty()) {
            context.drawTextWithShadow(renderer, "no data yet", x + 4, y + height / 2 - 4, palette.textDim());
            return;
        }
        double[] values = snapshot.values();
        double min = snapshot.min();
        double max = snapshot.max();
        double span = max - min;
        if (span <= 0) {
            span = Math.max(1.0, Math.abs(max));
        }
        int inner = height - 12;
        int barWidth = Math.max(1, width / Math.max(1, values.length));
        for (int i = 0; i < values.length; i++) {
            float t = (float) ((values[i] - min) / span);
            int barHeight = Math.max(1, Math.round(t * inner));
            int bx = x + 1 + i * barWidth;
            if (bx + barWidth > x + width - 1) {
                break;
            }
            context.fill(bx, y + height - 3 - barHeight, bx + barWidth, y + height - 3,
                    ColorUtil.scaleAlpha(tint, 0.85f));
        }
        String maxLabel = String.format("%.0f", max);
        String minLabel = String.format("%.0f", min);
        context.drawTextWithShadow(renderer, maxLabel, x + width - 4 - renderer.getWidth(maxLabel),
                y + 2, palette.textDim());
        context.drawTextWithShadow(renderer, minLabel, x + 4, y + height - 10, palette.textDim());
    }

    /** A vertical scroll indicator for a list region. */
    public static void scrollbar(DrawContext context, Palette palette, int x, int y, int height,
                                 int firstVisible, int total, int visibleCount) {
        if (total <= visibleCount) {
            return;
        }
        int track = height;
        int thumb = Math.max(12, track * visibleCount / total);
        int maxFirst = Math.max(1, total - visibleCount);
        int offset = (int) ((float) firstVisible / maxFirst * (track - thumb));
        RenderUtil.roundedRect(context, x, y, 2, track, 1, palette.borderSoft());
        RenderUtil.roundedRect(context, x, y + Math.max(0, Math.min(track - thumb, offset)), 2, thumb, 1,
                palette.primary());
    }
}
