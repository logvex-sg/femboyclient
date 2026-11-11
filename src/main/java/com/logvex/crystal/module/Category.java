package com.logvex.crystal.module;

import com.logvex.crystal.util.ColorUtil;

/**
 * Top-level grouping shown as tabs in the ClickGUI. Order here is the display order.
 *
 * <p>Each category carries an original kawaii glyph and a pastel tint used by the sidebar
 * and card headers, so navigation reads as part of the theme rather than generic text.
 */
public enum Category {

    COMBAT("Combat", "\u2694", 0xFFFF7FB0),
    MOVEMENT("Movement", "\u2748", 0xFF8FE3FF),
    PLAYER("Player", "\u2661", 0xFFFFA6C9),
    AUTOMATION("Automation", "\u2699", 0xFF9AD0C2),
    WORLD("World", "\u2728", 0xFFB7E3B0),
    RENDER("Render", "\u2726", 0xFFD4B0F0),
    HUD("HUD", "\u2764", 0xFFFF9EC4),
    MISC("Misc", "\u2727", 0xFFA0D8FF);

    private final String displayName;
    private final String icon;
    private final int tint;

    Category(String displayName, String icon, int tint) {
        this.displayName = displayName;
        this.icon = icon;
        this.tint = ColorUtil.withAlpha(tint, 255);
    }

    public String displayName() {
        return displayName;
    }

    /** A small decorative glyph prefixed to the category name in the sidebar. */
    public String icon() {
        return icon;
    }

    /** Pastel tint for this category's header and accent. */
    public int tint() {
        return tint;
    }
}
