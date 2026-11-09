package com.logvex.crystal.gui.hud;

import com.logvex.crystal.compat.Compat;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.notification.NotificationLayout;
import com.logvex.crystal.notification.NotificationManager.Notification;
import com.logvex.crystal.notification.NotificationManager;
import com.logvex.crystal.notification.NotificationSettings;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * The cute notification toasts. Each notice slides in from the anchored edge, shows its
 * severity glyph and text, and fades out. The stack can be placed in any screen corner and
 * scaled by the notification settings; when cat cosmetics are on, a paw print is drawn
 * beside the toast. Kept to a small fixed stack so it never covers the play area.
 *
 * <p>The element reports no width/height so the HUD layout editor treats it as a
 * non-draggable, full-screen overlay: it anchors to a real corner of the viewport rather
 * than to the auto-stacked HUD columns.
 */
public final class NotificationHudElement implements HudElement {

    @Override
    public String id() {
        return "notifications";
    }

    @Override
    public Column column() {
        return Column.RIGHT;
    }

    /** A full-viewport overlay, not a draggable panel. */
    @Override
    public boolean draggable() {
        return false;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        var bootstrap = CrystalClient.get();
        if (bootstrap == null) {
            return;
        }
        NotificationManager notifications = bootstrap.notifications();
        NotificationSettings settings = notifications.settings();
        List<Notification> active = notifications.active();
        if (active.isEmpty()) {
            return;
        }
        Palette palette = bootstrap.theme().palette();
        NotificationSettings.Position position = settings.position == null
                ? NotificationSettings.Position.TOP_RIGHT : settings.position;
        float scale = Math.max(0.5f, Math.min(2.0f, settings.scale));
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        int shown = Math.min(active.size(), Math.max(1, settings.maxVisible));
        int rowHeight = 16;
        int gap = 2;
        int margin = 4;
        int start = active.size() - shown;
        int index = 0;
        for (int i = start; i < active.size(); i++) {
            Notification notification = active.get(i);
            float alpha = notifications.alpha(notification);
            long lifetime = Math.max(1L, settings.durationMillis);
            float reveal = com.logvex.crystal.notification.ToastMotion.reveal(
                    System.currentTimeMillis() - notification.postedAtMillis(), lifetime);
            int levelColor = levelColor(notification, palette);
            int unscaledWidth = client.textRenderer.getWidth(notification.text()) + 26;
            int width = Math.round(unscaledWidth * scale);
            int height = Math.round(rowHeight * scale);
            int slide = Math.round(com.logvex.crystal.notification.ToastMotion.slide(20, reveal));
            int inward = NotificationLayout.leftAligned(position) ? -slide : slide;
            int rowX = NotificationLayout.rowX(position, screenWidth, width, margin) + inward;
            int rowY = NotificationLayout.rowY(position, screenHeight, index, height, gap, margin);

            var matrices = context.getMatrices();
            Compat.matrixPush(matrices);
            try {
                if (scale != 1f) {
                    Compat.matrixTranslate(matrices, rowX, rowY);
                    Compat.matrixScale(matrices, scale);
                    Compat.matrixTranslate(matrices, -rowX, -rowY);
                }
                drawRow(context, client, palette, notification, alpha, levelColor,
                        rowX, rowY, unscaledWidth, rowHeight);
            } finally {
                Compat.matrixPop(matrices);
            }
            index++;
        }
    }

    private void drawRow(DrawContext context, MinecraftClient client, Palette palette, Notification notification,
                         float alpha, int levelColor, int rowX, int rowY, int width, int rowHeight) {
        int fill = ColorUtil.scaleAlpha(palette.panel(), alpha);
        RenderUtil.softShadow(context, rowX, rowY, width, rowHeight, palette.radius(4), 60);
        RenderUtil.roundedPanel(context, rowX, rowY, width, rowHeight, palette.radius(4),
                fill, ColorUtil.scaleAlpha(palette.borderSoft(), alpha));
        // Accent bar on the left edge in the severity colour.
        RenderUtil.roundedRect(context, rowX + 1, rowY + 2, 2, rowHeight - 4, palette.radius(1),
                ColorUtil.scaleAlpha(levelColor, alpha));
        boolean badge = com.logvex.crystal.assets.Assets.icon(context, "status_" + badgeName(notification),
                rowX + 11, rowY + 8, 10, levelColor);
        if (!badge) {
            context.drawTextWithShadow(client.textRenderer, notification.level().glyph(), rowX + 6, rowY + 4,
                    ColorUtil.scaleAlpha(levelColor, alpha));
        }
        context.drawTextWithShadow(client.textRenderer, notification.text(), rowX + 17, rowY + 4,
                ColorUtil.scaleAlpha(palette.text(), alpha));
        if (catCosmeticsOn()) {
            boolean paw = com.logvex.crystal.assets.Assets.icon(context, "paw", rowX + width - 9,
                    rowY + 8, 10, ColorUtil.scaleAlpha(palette.tertiary(), alpha * 0.85f));
            if (!paw) {
                RenderUtil.paw(context, rowX + width - 9, rowY + 4, 1,
                        ColorUtil.scaleAlpha(palette.tertiary(), alpha * 0.8f));
            }
        }
    }

    private static boolean catCosmeticsOn() {
        var bootstrap = CrystalClient.get();
        return bootstrap != null && bootstrap.theme().settings().catCosmetics;
    }

    private String badgeName(Notification notification) {
        return switch (notification.level()) {
            case SUCCESS, CUTE -> "success";
            case WARN -> "warn";
            case ERROR -> "error";
            case INFO -> "info";
        };
    }

    private int levelColor(Notification notification, Palette palette) {
        return switch (notification.level()) {
            case SUCCESS -> palette.primary();
            case WARN -> palette.warning();
            case ERROR -> palette.danger();
            case CUTE -> palette.primary();
            case INFO -> palette.secondary();
        };
    }

    @Override
    public int height(MinecraftClient client) {
        return 0;
    }

    @Override
    public int width(MinecraftClient client) {
        return 0;
    }
}
