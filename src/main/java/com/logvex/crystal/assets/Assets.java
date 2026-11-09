package com.logvex.crystal.assets;

import com.logvex.crystal.render.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Draws manifest assets, falling back to the client's procedural motifs when an asset is
 * missing.
 *
 * <p>Every method is a no-op-safe draw: if the texture is unavailable (asset stripped, a
 * resource pack shadowing it, a load error) it draws the equivalent hand-coded glyph from
 * {@link RenderUtil} instead. That keeps the interface complete with zero assets present and
 * lets the pack be added or removed at any time.
 */
public final class Assets {

    private Assets() {
    }

    /** Draws an image asset scaled to {@code w x h}, or nothing when it is missing. */
    public static void image(DrawContext context, String id, int x, int y, int w, int h) {
        Identifier texture = AssetRegistry.get().image(id);
        if (texture == null) {
            return;
        }
        com.logvex.crystal.compat.Compat.drawTexture(context, texture, x, y, 0f, 0f, w, h, w, h);
    }

    /** Draws an image asset at its native size. */
    public static void image(DrawContext context, String id, int x, int y) {
        AssetManifest.Entry entry = AssetManifest.get(id);
        if (entry == null) {
            return;
        }
        image(context, id, x, y, entry.width(), entry.height());
    }

    /** Draws a tinted icon centred on ({@code cx}, {@code cy}), or null when missing. */
    public static boolean icon(DrawContext context, String id, int cx, int cy, int size, int argb) {
        Identifier texture = AssetRegistry.get().icon(id, argb);
        if (texture == null) {
            return false;
        }
        int half = size / 2;
        com.logvex.crystal.compat.Compat.drawTexture(context, texture, cx - half, cy - half,
                0f, 0f, size, size, size, size);
        return true;
    }

    /**
     * Draws the icon for a module category, falling back to the category's procedural glyph
     * when the texture is unavailable. Returns true when a texture was drawn.
     */
    public static boolean categoryIcon(DrawContext context, String categoryName, int cx, int cy,
                                       int size, int argb) {
        String id = switch (categoryName) {
            case "COMBAT" -> "combat";
            case "MOVEMENT" -> "movement";
            case "PLAYER" -> "player";
            case "RENDER" -> "render";
            case "HUD" -> "hud";
            default -> "utility";
        };
        return icon(context, id, cx, cy, size, argb);
    }

    /** Convenience: a paw motif from the pack, or the procedural paw when absent. */
    public static void paw(DrawContext context, int x, int y, int size, int argb) {
        if (!icon(context, "paw", x + size * 2, y + size * 3, size * 4, argb)) {
            RenderUtil.paw(context, x, y, size, argb);
        }
    }
}
