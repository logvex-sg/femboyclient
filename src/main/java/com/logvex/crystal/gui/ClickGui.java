package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.config.ConfigManager;
import com.logvex.crystal.config.FavoritesStore;
import com.logvex.crystal.cosmetic.GuiSounds;
import com.logvex.crystal.gui.widget.ColorPicker;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.module.ModuleQuery;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.ColorSetting;
import com.logvex.crystal.setting.DoubleSetting;
import com.logvex.crystal.setting.EnumSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.setting.KeybindSetting;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.setting.StringSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.theme.ThemePreset;
import com.logvex.crystal.theme.ThemeVariant;
import com.logvex.crystal.util.Animator;
import com.logvex.crystal.util.BestEffort;
import com.logvex.crystal.util.ColorUtil;
import com.logvex.crystal.util.KeyNames;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * The custom ClickGUI: a searchable, category-organised module browser with animated
 * pastel cards, an inline colour picker, a keybind editor, dropdowns, sliders and a
 * dedicated Appearance tab for the whole theme.
 *
 * <p>It is a normal {@link Screen} rendered on the vanilla pipeline (no direct GL), so it
 * is portable and cannot corrupt render state. Every mutation goes through the module /
 * setting / theme APIs, so what the GUI shows is exactly what the client runs, and theme
 * changes take effect immediately.
 */
public final class ClickGui extends Screen {

    /** Sidebar special pages beyond the module categories. */
    private enum Page {
        MODULES, APPEARANCE, FAVORITES, RECENT
    }

    private static final int SIDEBAR_WIDTH = 108;
    private static final int HEADER_HEIGHT = 30;
    private static final String SEARCH_GLYPH = "\u2315 ";
    private static final int FOOTER_HEIGHT = 20;
    // Header shortcut buttons: shared by the draw and click paths so they cannot drift.
    private static final int KEYBIND_BUTTON_W = 52;
    private static final int KEYBIND_BUTTON_INSET = 122;
    private static final int PROFILE_BUTTON_W = 44;
    private static final int PROFILE_BUTTON_INSET = 62;
    private static final int CARD_HEIGHT = 22;
    private static final int ROW_HEIGHT = 16;

    /** Appearance slider ids and labels, kept in one place so draw and click stay in sync. */
    private static final int[] SLIDER_IDS = {-1, -2, 1, 2, 3, 4, 5, 6};
    private static final String[] SLIDER_LABELS = {
            "Primary accent", "Secondary accent", "Background opacity", "Gradient intensity",
            "Glow intensity", "Corner radius", "UI scale", "Animation speed"
    };
    private static final int[] TOGGLE_IDS = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final String[] TOGGLE_LABELS = {
            "Decorations", "Cat cosmetics", "Reduced motion", "Blur",
            "Custom cursor", "GUI sounds", "Splash text", "Easter egg"
    };

    private final ModuleManager modules;
    private final ConfigManager config;
    private final HudManager hud;
    private final ThemeManager theme;

    private Category selectedCategory = Category.COMBAT;
    private Page page = Page.MODULES;
    private String search = "";
    private ModuleQuery.SortOrder sortOrder = ModuleQuery.SortOrder.DEFAULT;
    private Module expanded;
    private Module bindingModule;
    private Setting<?> bindingSetting;
    private ColorSetting editingColor;
    private ColorPicker.Channel editingChannel = ColorPicker.Channel.RED;
    private EnumSetting<?> openDropdown;
    private String status = "";
    private long statusAt;

    // Tooltip for the element under the cursor, rebuilt each frame.
    private String tooltipTitle;
    private String tooltipBody;

    // Right-click context menu on a module card.
    private Module contextModule;
    private int contextX;
    private int contextY;

    private double scroll;
    private int maxScroll;
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int listTop;
    private int listBottom;

    private final Animator openAnim = new Animator(0f, 0.22f);
    private final Animator hoverAnim = new Animator(0f, 0.3f);
    private final com.logvex.crystal.util.GradientBackground gradient = new com.logvex.crystal.util.GradientBackground();

    // Window position, as a pixel offset from the default margin. Persisted in the theme
    // settings so the panel reopens where the user left it, like a real client window.
    private int panelOffsetX;
    private int panelOffsetY;
    private boolean draggingPanel;
    private double dragGrabX;
    private double dragGrabY;

    public ClickGui(ModuleManager modules, ConfigManager config, HudManager hud, ThemeManager theme) {
        super(Text.literal("LOGVEX CRYSTAL"));
        this.modules = modules;
        this.config = config;
        this.hud = hud;
        this.theme = theme;
        this.panelOffsetX = theme.settings().guiX;
        this.panelOffsetY = theme.settings().guiY;
    }

    /** Installs the screen, guarded so it is never shown before fonts exist. */
    public void open() {
        if (!ScreenGuard.fontsReady()) {
            return;
        }
        openAnim.snap(0f);
        GuiSounds.open();
        net.minecraft.client.MinecraftClient.getInstance().setScreen(this);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String message) {
        this.status = message;
        this.statusAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        Palette palette = theme.palette();
        float deltaMs = com.logvex.crystal.util.FrameClock.deltaMillis();
        float speed = theme.settings().reducedMotion ? 0f : theme.settings().animationSpeed;
        openAnim.setTarget(1f);
        float open = openAnim.update(deltaMs, speed);

        // Dim + optional blur behind the panel, in real screen space.
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, (int) (150 * open)));
        // A slowly-drifting pastel wash behind the dim, so the theme's animated gradient is
        // actually visible. Scaled by the open animation so it fades in with the panel.
        if (open > 0.01f && theme.settings().gradientIntensity > 0f) {
            gradient.update(palette, speed, theme.settings().reducedMotion);
            // A low wash strength keeps the backdrop subtle, never a full-screen fill.
            float wash = 0.18f * open;
            RenderUtil.verticalGradient(context, 0, 0, width, height,
                    ColorUtil.scaleAlpha(gradient.topColor(), wash),
                    ColorUtil.scaleAlpha(gradient.bottomColor(), wash));
        }
        if (theme.settings().blur && !theme.settings().reducedMotion) {
            try {
                context.applyBlur();
            } catch (Throwable t) {
                // Blur is best-effort; some pipelines do not support it. Report once so a
                // persistently unsupported blur is visible, then stop retrying the log.
                BestEffort.report("gui-blur", "ClickGUI blur", t);
            }
        }

        // Everything below lays out in virtual coordinates so the UI-scale setting can
        // resize the whole interface without touching a single layout constant.
        float scale = uiScale();
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.scale(scale, scale);
        int virtualWidth = vw();
        int virtualHeight = vh();
        int virtualMouseX = (int) Math.round(mouseX / scale);
        int virtualMouseY = (int) Math.round(mouseY / scale);
        lastMouseX = virtualMouseX;
        lastMouseY = virtualMouseY;

        int margin = 18;
        int animOffset = (int) ((1f - open) * 14);
        panelLeft = margin + panelOffsetX;
        panelTop = margin + animOffset + panelOffsetY;
        panelRight = virtualWidth - margin + panelOffsetX;
        panelBottom = virtualHeight - margin - animOffset + panelOffsetY;
        if (panelBottom <= panelTop) {
            matrices.popMatrix();
            return;
        }

        RenderUtil.softShadow(context, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, r(10), 110);
        RenderUtil.roundedGradientPanel(context, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, r(10),
                palette.panel(), palette.panelAlt(), palette.border());
        drawHeader(context, palette);

        int sidebarX = panelLeft + 4;
        int contentX = panelLeft + SIDEBAR_WIDTH + 10;
        int contentRight = panelRight - 6;
        listTop = panelTop + HEADER_HEIGHT + 24;
        listBottom = panelBottom - FOOTER_HEIGHT;

        tooltipTitle = null;
        tooltipBody = null;
        drawSidebar(context, palette, virtualMouseX, virtualMouseY, sidebarX);
        switch (page) {
            case APPEARANCE -> drawAppearance(context, palette, contentX, contentRight, virtualMouseX, virtualMouseY);
            case FAVORITES -> {
                drawSearchBar(context, palette, contentX, contentRight);
                drawModuleList(context, palette, contentX, contentRight, virtualMouseX, virtualMouseY);
            }
            case RECENT -> {
                drawSearchBar(context, palette, contentX, contentRight);
                drawModuleList(context, palette, contentX, contentRight, virtualMouseX, virtualMouseY);
            }
            default -> {
                drawSearchBar(context, palette, contentX, contentRight);
                drawModuleList(context, palette, contentX, contentRight, virtualMouseX, virtualMouseY);
            }
        }
        drawFooter(context, palette, contentX);

        if (contextModule != null) {
            drawContextMenu(context, palette, virtualMouseX, virtualMouseY);
        }
        drawCursor(context, palette, virtualMouseX, virtualMouseY);
        matrices.popMatrix();

        // Tooltips and the context menu live in real screen space so they sit above
        // everything regardless of UI scale.
        if (tooltipTitle != null && contextModule == null) {
            drawTooltip(context, palette, mouseX, mouseY);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    /** Draws the hover tooltip for the element under the cursor, clamped to the screen. */
    private void drawTooltip(DrawContext context, Palette palette, int mouseX, int mouseY) {
        String[] bodyLines = tooltipBody == null ? new String[0] : tooltipBody.split("\n");
        int titleWidth = textRenderer.getWidth(tooltipTitle);
        int bodyWidth = 0;
        for (String line : bodyLines) {
            bodyWidth = Math.max(bodyWidth, textRenderer.getWidth(line));
        }
        int w = Math.max(titleWidth, bodyWidth) + 12;
        int h = bodyLines.length == 0 ? 18 : 24 + bodyLines.length * 10;
        int x = Math.min(mouseX + 12, width - w - 4);
        int y = Math.min(mouseY + 10, height - h - 4);
        RenderUtil.softShadow(context, x, y, w, h, r(4), 90);
        RenderUtil.roundedPanel(context, x, y, w, h, r(4), palette.panel(), palette.border());
        context.drawTextWithShadow(textRenderer, tooltipTitle, x + 6, y + 5, palette.primary());
        for (int i = 0; i < bodyLines.length; i++) {
            context.drawTextWithShadow(textRenderer, bodyLines[i], x + 6, y + 15 + i * 10, palette.textDim());
        }
    }

    /** Effective UI scale, guarded against a zero/negative value. */
    private float uiScale() {
        float s = theme.settings().uiScale;
        return s <= 0.1f ? 1f : s;
    }

    /** Layout width in virtual (pre-scale) coordinates. */
    private int vw() {
        return Math.round(width / uiScale());
    }

    /** Layout height in virtual (pre-scale) coordinates. */
    private int vh() {
        return Math.round(height / uiScale());
    }

    /** Scales a design-time corner radius by the user's corner-radius setting. */
    private int r(int base) {
        return theme.palette().radius(base);
    }

    /**
     * Draws the optional pastel cursor: a soft glow ring with a paw print, in the accent
     * colour. Purely cosmetic and only when the theme enables it, so it never obscures the
     * vanilla pointer when the user prefers the default.
     */
    private void drawCursor(DrawContext context, Palette palette, int mouseX, int mouseY) {
        if (!theme.settings().customCursor) {
            return;
        }
        int color = theme.settings().easterEgg ? rainbowColor(0f) : palette.primary();
        RenderUtil.glow(context, mouseX - 1, mouseY - 1, 3, 3, 1, ColorUtil.scaleAlpha(color, 0.5f));
        RenderUtil.paw(context, mouseX - 3, mouseY - 3, 1, color);
    }

    /**
     * The hue for the cosmetic rainbow easter egg, cycling with wall-clock time (slowed by
     * the animation-speed setting). Purely decorative; only read when the egg is enabled.
     */
    private int rainbowColor(float offset) {
        float speed = theme.settings().reducedMotion ? 0f : Math.max(0.1f, theme.settings().animationSpeed);
        float hue = (System.currentTimeMillis() % 6000L) / 6000f * 360f * speed + offset;
        return ColorUtil.hsv(hue, 0.55f, 1.0f);
    }

    /** Spawns the cosmetic paw-print burst at a GUI interaction point, if enabled. */
    private void pawBurst(double x, double y) {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            bootstrap.cosmetics().spawnPawBurst(x, y, theme.palette().primary());
        }
    }

    private void drawHeader(DrawContext context, Palette palette) {
        // The hidden easter egg tints the brand with a slow rainbow.
        int titleColor = theme.settings().easterEgg ? rainbowColor(0f) : palette.primary();
        int subtitleColor = theme.settings().easterEgg ? rainbowColor(120f) : palette.secondary();
        // Prefer the packaged crystal mark; fall back to the text wordmark when absent.
        boolean logoDrawn = com.logvex.crystal.assets.Assets.icon(context, "crystal",
                panelLeft + 20, panelTop + 13, 16, titleColor);
        int brandRight;
        if (logoDrawn) {
            context.drawTextWithShadow(textRenderer, "LOGVEX", panelLeft + 32, panelTop + 4, titleColor);
            context.drawTextWithShadow(textRenderer, "CRYSTAL", panelLeft + 32, panelTop + 14, subtitleColor);
            brandRight = panelLeft + 32 + textRenderer.getWidth("CRYSTAL");
        } else {
            context.drawTextWithShadow(textRenderer, "LOGVEX CRYSTAL", panelLeft + 10, panelTop + 8, titleColor);
            brandRight = panelLeft + 10 + textRenderer.getWidth("LOGVEX CRYSTAL");
        }
        if (theme.settings().decorations) {
            RenderUtil.catEars(context, brandRight + 4, panelTop + 4, 16, 6, palette.primary(), 0.4f);
        }
        String sub = switch (page) {
            case APPEARANCE -> "appearance";
            case FAVORITES -> "favourites";
            case RECENT -> "recent";
            default -> selectedCategory.displayName().toLowerCase(Locale.ROOT);
        };
        int subX = brandRight + 24;
        context.drawTextWithShadow(textRenderer, sub, subX, panelTop + 9, palette.textDim());
        // Active module counter, right of the page label.
        context.drawTextWithShadow(textRenderer, modules.enabledCount() + " active",
                subX + textRenderer.getWidth(sub) + 12, panelTop + 9, palette.secondary());
        // Header shortcut buttons: keybinds, profiles, then the close button at the far right.
        drawHeaderButton(context, palette, "keybinds", panelRight - KEYBIND_BUTTON_INSET, panelTop + 5, KEYBIND_BUTTON_W);
        drawHeaderButton(context, palette, "profiles", panelRight - PROFILE_BUTTON_INSET, panelTop + 5, PROFILE_BUTTON_W);
        context.drawTextWithShadow(textRenderer, "\u2715", panelRight - 18, panelTop + 5, palette.textDim());
        RenderUtil.horizontalGradient(context, panelLeft + 4, panelTop + HEADER_HEIGHT - 2,
                panelRight - panelLeft - 8, 2, palette.accentGradientStart(), palette.accentGradientEnd());
    }

    private void drawHeaderButton(DrawContext context, Palette palette, String label, int x, int y, int w) {
        RenderUtil.roundedPanel(context, x, y, w, 14, r(3), ColorUtil.scaleAlpha(palette.panelAlt(), 0.9f), palette.borderSoft());
        boolean icon = com.logvex.crystal.assets.Assets.icon(context, label, x + 7, y + 7, 10, palette.textDim());
        context.drawTextWithShadow(textRenderer, label, x + (icon ? 14 : 5), y + 4, palette.textDim());
    }

    private void drawSidebar(DrawContext context, Palette palette, int mouseX, int mouseY, int x) {
        int y = panelTop + HEADER_HEIGHT + 8;
        int w = SIDEBAR_WIDTH - 4;
        for (Category category : Category.values()) {
            int count = modules.byCategory(category).size();
            boolean active = page == Page.MODULES && category == selectedCategory;
            boolean iconDrawn = com.logvex.crystal.assets.Assets.categoryIcon(context, category.name(),
                    x + 12, y + 9, 11, category.tint());
            String label = (iconDrawn ? "" : category.icon() + " ") + category.displayName() + "  " + count;
            y = drawSidebarRow(context, palette, mouseX, mouseY, x, y, w, label,
                    category.tint(), active, iconDrawn ? 18 : 7);
        }
        y += 4;
        boolean favIcon = com.logvex.crystal.assets.Assets.icon(context, "star", x + 12, y + 9, 11, palette.primary());
        y = drawSidebarRow(context, palette, mouseX, mouseY, x, y, w,
                (favIcon ? "" : "\u2605 ") + "Favourites  " + favoriteCount(), palette.primary(),
                page == Page.FAVORITES, favIcon ? 18 : 7);
        boolean recIcon = com.logvex.crystal.assets.Assets.icon(context, "recent", x + 12, y + 9, 11, palette.secondary());
        y = drawSidebarRow(context, palette, mouseX, mouseY, x, y, w,
                (recIcon ? "" : "\u21BB ") + "Recent  " + recentCount(), palette.secondary(),
                page == Page.RECENT, recIcon ? 18 : 7);
        boolean themeIcon = com.logvex.crystal.assets.Assets.icon(context, "theme", x + 12, y + 9, 11, palette.tertiary());
        y = drawSidebarRow(context, palette, mouseX, mouseY, x, y, w,
                (themeIcon ? "" : "\u273F ") + "Appearance", palette.tertiary(),
                page == Page.APPEARANCE, themeIcon ? 18 : 7);

        int py = panelBottom - FOOTER_HEIGHT - 18;
        context.drawTextWithShadow(textRenderer, modules.size() + " modules \u2661", x + 4, py, palette.textDim());
    }

    private int favoriteCount() {
        FavoritesStore store = favoritesStore();
        return store == null ? 0 : store.favorites().size();
    }

    private int recentCount() {
        FavoritesStore store = favoritesStore();
        return store == null ? 0 : store.recent().size();
    }

    private FavoritesStore favoritesStore() {
        CrystalClient bootstrap = CrystalClient.get();
        return bootstrap == null ? null : bootstrap.favorites();
    }

    private int drawSidebarRow(DrawContext context, Palette palette, int mouseX, int mouseY, int x, int y, int w,
                               String label, int tint, boolean active) {
        return drawSidebarRow(context, palette, mouseX, mouseY, x, y, w, label, tint, active, 7);
    }

    private int drawSidebarRow(DrawContext context, Palette palette, int mouseX, int mouseY, int x, int y, int w,
                               String label, int tint, boolean active, int textInset) {
        int rowBottom = y + 18;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= rowBottom;
        if (active) {
            RenderUtil.roundedGradientPanel(context, x, y, w, 18, r(5),
                    ColorUtil.withAlpha(tint, 90), ColorUtil.withAlpha(palette.tertiary(), 70), palette.border());
        } else if (hovered) {
            RenderUtil.roundedRect(context, x, y, w, 18, r(5), palette.panelHover());
        }
        context.drawTextWithShadow(textRenderer, label, x + textInset, y + 5, active ? palette.text() : palette.textDim());
        return rowBottom + 3;
    }

    private void drawSearchBar(DrawContext context, Palette palette, int x, int right) {
        int y = panelTop + HEADER_HEIGHT + 4;
        int sortW = 74;
        int sortX = right - sortW;
        int w = sortX - 6 - x;
        RenderUtil.roundedPanel(context, x, y, w, 16, r(5), ColorUtil.scaleAlpha(palette.panelAlt(), 0.9f), palette.borderSoft());
        // Gradient border hint under the search field.
        RenderUtil.horizontalGradient(context, x + 2, y + 15, w - 4, 1, palette.accentGradientStart(), palette.accentGradientEnd());
        String text = search.isEmpty() ? "search modules..." : search;
        int color = search.isEmpty() ? palette.textDim() : palette.text();
        boolean searchIcon = com.logvex.crystal.assets.Assets.icon(context, "search", x + 11, y + 8, 10, color);
        context.drawTextWithShadow(textRenderer, (searchIcon ? "" : SEARCH_GLYPH) + text,
                x + (searchIcon ? 19 : 5), y + 4, color);
        // Sort control: cycles the ordering of the module list. Its own hit box is captured
        // in searchSortX so the click path uses exactly what was drawn.
        searchSortX = sortX;
        searchSortY = y;
        searchSortW = sortW;
        searchSortH = 16;
        boolean hovered = mouseXIn(sortX, sortX + sortW) && mouseYIn(y, y + 16);
        RenderUtil.roundedPanel(context, sortX, y, sortW, 16, r(5),
                hovered ? palette.panelHover() : ColorUtil.scaleAlpha(palette.panelAlt(), 0.9f), palette.borderSoft());
        context.drawTextWithShadow(textRenderer, "\u2195 " + sortOrder.label(), sortX + 6, y + 4,
                hovered ? palette.text() : palette.textDim());
    }

    /** Last mouse position seen during render, used by the hover test in the search bar. */
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;
    private int searchSortX;
    private int searchSortY;
    private int searchSortW;
    private int searchSortH;

    private boolean mouseXIn(int left, int right) {
        return lastMouseX >= left && lastMouseX <= right;
    }

    private boolean mouseYIn(int top, int bottom) {
        return lastMouseY >= top && lastMouseY <= bottom;
    }

    private void drawModuleList(DrawContext context, Palette palette, int x, int right, int mouseX, int mouseY) {
        context.enableScissor(x - 2, listTop, right, listBottom);
        List<Module> visible = visibleModules();
        int y = listTop - (int) scroll;
        for (Module module : visible) {
            if (y + CARD_HEIGHT > listTop - CARD_HEIGHT && y < listBottom) {
                drawModuleCard(context, palette, module, x, y, right, mouseX, mouseY);
            }
            y += CARD_HEIGHT + 2;
            if (module == expanded) {
                y = drawSettings(context, palette, module, x + 6, y, right - 4, mouseX, mouseY);
            }
        }
        int contentHeight = y - (listTop - (int) scroll);
        maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        context.disableScissor();
        drawScrollbar(context, palette, x, right);
    }

    private void drawScrollbar(DrawContext context, Palette palette, int x, int right) {
        int trackHeight = listBottom - listTop;
        if (maxScroll <= 0 || trackHeight <= 0) {
            return;
        }
        int barX = right - 3;
        int thumbHeight = Math.max(16, trackHeight * trackHeight / (trackHeight + maxScroll));
        int thumbY = listTop + (int) ((trackHeight - thumbHeight) * (scroll / maxScroll));
        RenderUtil.roundedRect(context, barX, listTop, 3, trackHeight, r(2), ColorUtil.scaleAlpha(palette.borderSoft(), 0.5f));
        RenderUtil.roundedRect(context, barX, thumbY, 3, thumbHeight, r(2), palette.primary());
    }

    private void drawModuleCard(DrawContext context, Palette palette, Module module, int x, int y, int right,
                                int mouseX, int mouseY) {
        int w = right - x - 4;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + CARD_HEIGHT;
        boolean enabled = module.isEnabled();
        // Animate a soft hover lift.
        hoverAnim.setTarget(hovered ? 1f : 0f);
        int fill = enabled ? ColorUtil.withAlpha(palette.primary(), 46) : (hovered ? palette.panelHover() : palette.card());
        RenderUtil.roundedPanel(context, x, y, w, CARD_HEIGHT, r(6), fill, enabled ? palette.border() : palette.borderSoft());
        if (enabled) {
            RenderUtil.glow(context, x, y, w, CARD_HEIGHT, r(6), palette.glow());
            RenderUtil.roundedRect(context, x + 2, y + 3, 3, CARD_HEIGHT - 6, r(2), palette.primary());
        }
        // Category icon in the card's pastel tint.
        context.drawTextWithShadow(textRenderer, module.category().icon(), x + 9, y + 7, module.category().tint());
        int nameX = x + 21;
        String bind = module.keybind().isBound() ? "  [" + KeyNames.name(module.keybind().key()) + "]" : "";
        int nameColor = enabled ? palette.primary() : palette.text();
        context.drawTextWithShadow(textRenderer, module.name() + bind, nameX, y + 7, nameColor);
        // Favourite star, lit when the module is a favourite.
        FavoritesStore store = favoritesStore();
        boolean favorite = store != null && store.isFavorite(module.name());
        context.drawTextWithShadow(textRenderer, favorite ? "\u2605" : "\u2606", right - 52, y + 7,
                favorite ? palette.primary() : palette.textDim());
        drawToggle(context, palette, right - 34, y + 5, enabled, hovered);
        if (!modules.canEnable(module) && !enabled) {
            context.drawTextWithShadow(textRenderer, "\u26A0", right - 68, y + 7, palette.warning());
        }
        if (hovered) {
            tooltipTitle = module.name();
            StringBuilder body = new StringBuilder(module.description() == null || module.description().isEmpty()
                    ? "left-click toggle \u00B7 right-click menu"
                    : module.description());
            String requirement = ModuleQuery.requirementLine(module, modules);
            if (!requirement.isEmpty()) {
                body.append("\n\u2022 ").append(requirement);
            }
            String reason = ModuleQuery.matchReason(module, search);
            if (!reason.isEmpty()) {
                body.append("\n\u2022 matched: ").append(reason);
            }
            tooltipBody = body.toString();
        }
    }

    /** Right-click menu for a module card: settings, favourite, bind, reset. */
    private void drawContextMenu(DrawContext context, Palette palette, int mouseX, int mouseY) {
        String[] items = contextItems();
        int w = 132;
        int rowH = 14;
        int h = items.length * rowH + 6;
        RenderUtil.softShadow(context, contextX, contextY, w, h, r(5), 100);
        RenderUtil.roundedPanel(context, contextX, contextY, w, h, r(5), palette.panel(), palette.border());
        for (int i = 0; i < items.length; i++) {
            int rowY = contextY + 3 + i * rowH;
            boolean hovered = mouseX >= contextX && mouseX <= contextX + w && mouseY >= rowY && mouseY <= rowY + rowH;
            if (hovered) {
                RenderUtil.roundedRect(context, contextX + 2, rowY, w - 4, rowH, r(3), palette.panelHover());
            }
            context.drawTextWithShadow(textRenderer, items[i], contextX + 7, rowY + 3,
                    hovered ? palette.text() : palette.textDim());
        }
    }

    private String[] contextItems() {
        boolean favorite = contextModule != null && favoritesStore() != null
                && favoritesStore().isFavorite(contextModule.name());
        return new String[]{
                "Settings",
                favorite ? "Unfavourite" : "Favourite",
                "Bind key",
                "Reset settings",
                "Disable"
        };
    }

    private void handleContextClick(double mouseX, double mouseY) {
        int rowH = 14;
        int index = (int) ((mouseY - contextY - 3) / rowH);
        Module module = contextModule;
        contextModule = null;
        if (module == null || index < 0) {
            return;
        }
        switch (index) {
            case 0 -> {
                expanded = expanded == module ? null : module;
                selectedCategory = module.category();
                page = Page.MODULES;
                GuiSounds.click();
            }
            case 1 -> {
                FavoritesStore store = favoritesStore();
                if (store != null) {
                    store.toggleFavorite(module.name());
                    persistFavorites();
                    GuiSounds.click();
                }
            }
            case 2 -> {
                bindingModule = module;
                bindingSetting = module.keybind();
                setStatus("Press a key to bind " + module.name());
            }
            case 3 -> {
                module.keybind().reset();
                for (SettingGroup group : module.groups()) {
                    for (Setting<?> setting : group.settings()) {
                        setting.reset();
                    }
                }
                setStatus("Reset " + module.name() + " to defaults");
                GuiSounds.click();
            }
            case 4 -> {
                if (module.isEnabled()) {
                    module.disable();
                }
                setStatus(module.name() + " disabled");
            }
            default -> {
            }
        }
    }

    private void persistFavorites() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            // Debounced: a burst of favourite toggles collapses into one write.
            bootstrap.requestSave();
        }
    }

    /** A pill toggle: pink when enabled, muted lavender when disabled, with an animated knob. */
    private void drawToggle(DrawContext context, Palette palette, int x, int y, boolean on, boolean hovered) {
        int w = 26;
        int h = 12;
        int track = on ? palette.primary() : palette.disabled();
        RenderUtil.roundedRect(context, x, y, w, h, h / 2, ColorUtil.withAlpha(track, on ? 220 : 150));
        int knobR = h / 2 - 1;
        int knobX = on ? x + w - knobR - 2 : x + knobR + 2;
        RenderUtil.dot(context, knobX, y + h / 2, knobR, ColorUtil.lighten(track, hovered ? 0.3f : 0.15f));
    }

    private int drawSettings(DrawContext context, Palette palette, Module module, int x, int y, int right,
                             int mouseX, int mouseY) {
        if (module.description() != null && !module.description().isEmpty()) {
            context.drawTextWithShadow(textRenderer, module.description(), x, y + 2, palette.textDim());
            y += ROW_HEIGHT;
        }
        for (SettingGroup group : module.groups()) {
            if (!group.hasVisibleSettings()) {
                continue;
            }
            context.drawTextWithShadow(textRenderer, "\u2726 " + group.name(), x, y + 2, palette.tertiary());
            y += ROW_HEIGHT;
            for (Setting<?> setting : group.settings()) {
                if (!setting.visible()) {
                    continue;
                }
                y = drawSettingRow(context, palette, setting, x + 6, y, right, mouseX, mouseY);
            }
        }
        return y + 6;
    }

    private int drawSettingRow(DrawContext context, Palette palette, Setting<?> setting, int x, int y, int right,
                               int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        RenderUtil.roundedRect(context, x, y, right - x, ROW_HEIGHT, r(3), hovered ? palette.panelHover() : palette.panelAlt());
        context.drawTextWithShadow(textRenderer, setting.name(), x + 5, y + 4, palette.text());
        if (hovered && setting.description() != null && !setting.description().isEmpty()) {
            tooltipTitle = setting.name();
            tooltipBody = setting.description();
        }
        int valueX = right - 84;
        // A small reset dot on the right edge of any setting that differs from its default.
        if (!setting.isDefault()) {
            RenderUtil.dot(context, right - 2, y + ROW_HEIGHT / 2, 1, palette.tertiary());
        }
        if (setting instanceof BooleanSetting bool) {
            drawToggle(context, palette, right - 34, y + 2, bool.value(), hovered);
        } else if (setting instanceof ColorSetting color) {
            RenderUtil.roundedRect(context, valueX, y + 2, 16, 12, r(3), color.argb());
            RenderUtil.roundedOutline(context, valueX, y + 2, 16, 12, r(3), palette.border());
            context.drawTextWithShadow(textRenderer, color.displayValue(), valueX + 20, y + 4, palette.textDim());
            if (setting == editingColor) {
                drawColorSliders(context, palette, color, x, y + ROW_HEIGHT + 1, right);
            }
        } else if (setting instanceof KeybindSetting keybind) {
            String label = bindingSetting == setting ? "<press>" : keybind.displayValue();
            context.drawTextWithShadow(textRenderer, label, valueX, y + 4, palette.warning());
        } else if (setting instanceof IntSetting integer) {
            context.drawTextWithShadow(textRenderer, integer.displayValue(), valueX, y + 4, palette.textDim());
            drawSlider(context, palette, y, right, integer.fraction(), hovered);
        } else if (setting instanceof DoubleSetting decimal) {
            context.drawTextWithShadow(textRenderer, decimal.displayValue(), valueX, y + 4, palette.textDim());
            drawSlider(context, palette, y, right, decimal.fraction(), hovered);
        } else if (setting instanceof EnumSetting<?> enumeration) {
            String value = enumeration.displayValue() + " \u25BE";
            context.drawTextWithShadow(textRenderer, value, valueX, y + 4, palette.secondary());
            if (openDropdown == enumeration) {
                drawDropdown(context, palette, enumeration, x + 60, y + ROW_HEIGHT, right);
            }
        } else if (setting instanceof StringSetting string) {
            context.drawTextWithShadow(textRenderer, string.displayValue(), valueX, y + 4, palette.textDim());
        }
        return y + ROW_HEIGHT + 1;
    }

    private void drawSlider(DrawContext context, Palette palette, int y, int right, double fraction, boolean hovered) {
        int barLeft = right - 84;
        int barRight = right - 5;
        int barY = y + ROW_HEIGHT - 3;
        RenderUtil.roundedRect(context, barLeft, barY, barRight - barLeft, 3, r(1), ColorUtil.scaleAlpha(palette.borderSoft(), 0.7f));
        int filled = barLeft + (int) Math.round((barRight - barLeft) * Math.max(0, Math.min(1, fraction)));
        if (filled > barLeft) {
            RenderUtil.horizontalGradient(context, barLeft, barY, filled - barLeft, 3,
                    palette.accentGradientStart(), palette.accentGradientEnd());
        }
        RenderUtil.dot(context, filled, barY + 1, hovered ? 3 : 2, palette.text());
    }

    private void drawColorSliders(DrawContext context, Palette palette, ColorSetting color, int x, int y, int right) {
        int sliderLeft = x + 60;
        int sliderWidth = right - sliderLeft - 8;
        ColorPicker.Channel[] channels = ColorPicker.Channel.values();
        String[] labels = {"R", "G", "B", "A"};
        for (int i = 0; i < channels.length; i++) {
            int sy = y + i * 10;
            int component = ColorPicker.component(color.argb(), channels[i]);
            context.drawTextWithShadow(textRenderer, labels[i], x + 4, sy, palette.textDim());
            RenderUtil.roundedRect(context, sliderLeft, sy, sliderWidth, 8, r(2), palette.panel());
            RenderUtil.horizontalGradient(context, sliderLeft, sy, sliderWidth, 8, 0xFF000000,
                    ColorPicker.channelGradient(color.argb(), channels[i]));
            int handleX = sliderLeft + (int) (sliderWidth * (component / 255.0));
            context.fill(handleX - 1, sy - 1, handleX + 2, sy + 9, palette.text());
        }
    }

    private void drawDropdown(DrawContext context, Palette palette, EnumSetting<?> setting, int x, int y, int right) {
        int w = Math.max(80, right - x);
        int h = setting.values().size() * 12 + 4;
        // Capture the popup top so the click resolver hits the row under the cursor.
        dropdownTop = y;
        RenderUtil.softShadow(context, x, y, w, h, r(4), 80);
        RenderUtil.roundedPanel(context, x, y, w, h, r(4), palette.panel(), palette.border());
        int rowY = y + 2;
        for (Object value : setting.values()) {
            boolean selected = value == setting.value();
            if (selected) {
                RenderUtil.roundedRect(context, x + 2, rowY, w - 4, 12, r(3), ColorUtil.withAlpha(palette.primary(), 60));
            }
            context.drawTextWithShadow(textRenderer, String.valueOf(value), x + 6, rowY + 2,
                    selected ? palette.primary() : palette.textDim());
            rowY += 12;
        }
    }

    private void drawFooter(DrawContext context, Palette palette, int x) {
        int y = panelBottom - FOOTER_HEIGHT + 5;
        context.drawTextWithShadow(textRenderer,
                "[LMB toggle] [RMB settings] [drag header to move] [B bind] [S save] [T theme] [Esc close]",
                x, y, palette.textDim());
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            context.drawTextWithShadow(textRenderer, status, x, y - 11, palette.primary());
        }
    }

    // ------------------------------------------------------------------ appearance tab

    private void drawAppearance(DrawContext context, Palette palette, int x, int right, int mouseX, int mouseY) {
        context.enableScissor(x - 2, listTop, right, listBottom);
        int y = listTop - (int) scroll;
        ThemeManager tm = theme;
        var s = tm.settings();

        context.drawTextWithShadow(textRenderer, "\u273F Theme preset", x, y + 2, palette.tertiary());
        y += 14;
        // Preset grid, two per row.
        ThemePreset[] presets = ThemePreset.values();
        int colW = (right - x - 6) / 2;
        for (int i = 0; i < presets.length; i++) {
            int px = x + (i % 2) * (colW + 6);
            int py = y + (i / 2) * 18;
            boolean selected = s.preset == presets[i];
            boolean hovered = mouseX >= px && mouseX <= px + colW && mouseY >= py && mouseY <= py + 15;
            RenderUtil.roundedPanel(context, px, py, colW, 15, r(4),
                    selected ? ColorUtil.withAlpha(presets[i].primary(), 70) : (hovered ? palette.panelHover() : palette.card()),
                    selected ? palette.border() : palette.borderSoft());
            RenderUtil.roundedRect(context, px + 4, py + 4, 7, 7, r(2), presets[i].primary());
            context.drawTextWithShadow(textRenderer, presets[i].label(), px + 15, py + 4,
                    selected ? palette.text() : palette.textDim());
        }
        y += ((presets.length + 1) / 2) * 18 + 8;

        // Slider rows, driven by the same id/label arrays the click handler walks.
        float[] fractions = {
                0f, 0f, s.backgroundOpacity, s.gradientIntensity,
                s.glowIntensity, s.cornerRadius / 12f, (s.uiScale - 0.75f) / 0.75f, s.animationSpeed / 2f
        };
        for (int i = 0; i < SLIDER_IDS.length; i++) {
            y = appearanceSlider(context, palette, SLIDER_LABELS[i], x, y, right, SLIDER_IDS[i], fractions[i], mouseX, mouseY);
        }

        y = appearanceToggle(context, palette, "Decorations", x, y, right, s.decorations, 10, mouseX, mouseY);
        y = appearanceToggle(context, palette, "Cat cosmetics", x, y, right, s.catCosmetics, 11, mouseX, mouseY);
        y = appearanceToggle(context, palette, "Reduced motion", x, y, right, s.reducedMotion, 12, mouseX, mouseY);
        y = appearanceToggle(context, palette, "Blur", x, y, right, s.blur, 13, mouseX, mouseY);
        y = appearanceToggle(context, palette, "Custom cursor", x, y, right, s.customCursor, 14, mouseX, mouseY);
        y = appearanceToggle(context, palette, "GUI sounds", x, y, right, s.guiSounds, 15, mouseX, mouseY);
        y = appearanceToggle(context, palette, "Splash text", x, y, right, s.splashText, 16, mouseX, mouseY);
        y = appearanceToggle(context, palette, "Easter egg", x, y, right, s.easterEgg, 17, mouseX, mouseY);
        y += 6;
        context.drawTextWithShadow(textRenderer, "\u2661 Variant", x, y + 2, palette.tertiary());
        y += 14;
        for (ThemeVariant variant : ThemeVariant.values()) {
            int px = x + variant.ordinal() * 90;
            boolean selected = s.variant == variant;
            RenderUtil.roundedPanel(context, px, y, 84, 15, r(4),
                    selected ? ColorUtil.withAlpha(palette.primary(), 70) : palette.card(),
                    selected ? palette.border() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, variant.label(), px + 6, y + 4,
                    selected ? palette.text() : palette.textDim());
        }
        y += 24;

        int contentHeight = y - (listTop - (int) scroll);
        maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        context.disableScissor();
        drawScrollbar(context, palette, x, right);
    }

    private int appearanceSlider(DrawContext context, Palette palette, String label, int x, int y, int right,
                                 int id, float value, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        RenderUtil.roundedRect(context, x, y, right - x, ROW_HEIGHT, r(3), hovered ? palette.panelHover() : palette.panelAlt());
        context.drawTextWithShadow(textRenderer, label, x + 5, y + 4, palette.text());
        int barLeft = right - 100;
        int barRight = right - 6;
        int barY = y + ROW_HEIGHT - 3;
        RenderUtil.roundedRect(context, barLeft, barY, barRight - barLeft, 3, r(1), ColorUtil.scaleAlpha(palette.borderSoft(), 0.7f));
        int filled = barLeft + (int) Math.round((barRight - barLeft) * Math.max(0, Math.min(1, value)));
        RenderUtil.horizontalGradient(context, barLeft, barY, Math.max(0, filled - barLeft), 3,
                palette.accentGradientStart(), palette.accentGradientEnd());
        RenderUtil.dot(context, filled, barY + 1, hovered ? 3 : 2, palette.text());
        return y + ROW_HEIGHT + 1;
    }

    private int appearanceToggle(DrawContext context, Palette palette, String label, int x, int y, int right,
                                 boolean value, int id, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= right && mouseY >= y && mouseY <= y + ROW_HEIGHT;
        RenderUtil.roundedRect(context, x, y, right - x, ROW_HEIGHT, r(3), hovered ? palette.panelHover() : palette.panelAlt());
        context.drawTextWithShadow(textRenderer, label, x + 5, y + 4, palette.text());
        drawToggle(context, palette, right - 34, y + 2, value, hovered);
        return y + ROW_HEIGHT + 1;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Input arrives in real screen coordinates; the layout is virtual, so convert.
        double mouseX = click.x() / uiScale();
        double mouseY = click.y() / uiScale();
        int button = click.button();
        // Header: shortcuts, close button, then drag-to-move.
        if (handleHeaderClick(mouseX, mouseY, button)) {
            return true;
        }
        // An open context menu swallows the next click.
        if (contextModule != null) {
            handleContextClick(mouseX, mouseY);
            return true;
        }
        if (handleSidebarClick(mouseX, mouseY)) {
            return true;
        }
        if (page == Page.APPEARANCE) {
            if (handleAppearanceClick(mouseX, mouseY, button)) {
                return true;
            }
        } else if (handleModuleClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    /** Handles the header bar: shortcut buttons, the close button and click-drag moving. */
    private boolean handleHeaderClick(double mouseX, double mouseY, int button) {
        int headerBottom = panelTop + HEADER_HEIGHT;
        if (mouseX < panelLeft || mouseX > panelRight || mouseY < panelTop || mouseY > headerBottom) {
            return false;
        }
        if (button == 0 && inRect(mouseX, mouseY, panelRight - 20, panelTop + 2, 16, 14)) {
            GuiSounds.close();
            close();
            return true;
        }
        if (button == 0 && inRect(mouseX, mouseY, panelRight - KEYBIND_BUTTON_INSET, panelTop + 5,
                KEYBIND_BUTTON_W, 14)) {
            openKeybindScreen();
            return true;
        }
        if (button == 0 && inRect(mouseX, mouseY, panelRight - PROFILE_BUTTON_INSET, panelTop + 5,
                PROFILE_BUTTON_W, 14)) {
            openProfileScreen();
            return true;
        }
        if (button == 0) {
            draggingPanel = true;
            dragGrabX = mouseX - panelLeft;
            dragGrabY = mouseY - panelTop;
            return true;
        }
        return false;
    }

    private void openKeybindScreen() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                    new KeybindScreen(modules, theme, this::markKeybindsChanged));
        }
    }

    private void markKeybindsChanged() {
        setStatus("Keybinds updated");
    }

    private void openProfileScreen() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(
                    new ProfileScreen(bootstrap.profiles(), theme));
        }
    }

    private boolean handleSidebarClick(double mouseX, double mouseY) {
        int x = panelLeft + 4;
        int y = panelTop + HEADER_HEIGHT + 8;
        int w = SIDEBAR_WIDTH - 4;
        for (Category category : Category.values()) {
            if (inRect(mouseX, mouseY, x, y, w, 18)) {
                selectedCategory = category;
                page = Page.MODULES;
                search = "";
                scroll = 0;
                GuiSounds.click();
                return true;
            }
            y += 21;
        }
        y += 4;
        if (inRect(mouseX, mouseY, x, y, w, 18)) {
            page = Page.FAVORITES;
            search = "";
            scroll = 0;
            GuiSounds.click();
            return true;
        }
        y += 21;
        if (inRect(mouseX, mouseY, x, y, w, 18)) {
            page = Page.RECENT;
            search = "";
            scroll = 0;
            GuiSounds.click();
            return true;
        }
        y += 21;
        if (inRect(mouseX, mouseY, x, y, w, 18)) {
            page = Page.APPEARANCE;
            scroll = 0;
            GuiSounds.click();
            return true;
        }
        return false;
    }

    private boolean handleModuleClick(double mouseX, double mouseY, int button) {
        int x = panelLeft + SIDEBAR_WIDTH + 10;
        int right = panelRight - 6;
        // Sort control in the search bar: cycles the module ordering.
        if (button == 0 && inRect(mouseX, mouseY, searchSortX, searchSortY, searchSortW, searchSortH)) {
            sortOrder = sortOrder.next();
            scroll = 0;
            GuiSounds.click();
            setStatus("sort: " + sortOrder.label());
            return true;
        }
        if (mouseX < x || mouseX > right || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        if (openDropdown != null) {
            // Clicking anywhere closes an open dropdown after resolving the pick.
            if (resolveDropdownClick(mouseX, mouseY, x)) {
                openDropdown = null;
                return true;
            }
            openDropdown = null;
        }
        int y = listTop - (int) scroll;
        for (Module module : visibleModules()) {
            if (mouseY >= y && mouseY <= y + CARD_HEIGHT) {
                if (button == 0 && inRect(mouseX, mouseY, right - 56, y + 2, 16, 16)) {
                    // The star toggles favourite status.
                    FavoritesStore store = favoritesStore();
                    if (store != null) {
                        store.toggleFavorite(module.name());
                        persistFavorites();
                        GuiSounds.click();
                    }
                } else if (button == 0) {
                    // Meteor convention: left click toggles the module.
                    toggleModule(module);
                } else if (button == 1) {
                    // Right click opens the context menu.
                    contextModule = module;
                    contextX = (int) mouseX;
                    contextY = (int) mouseY;
                    GuiSounds.click();
                }
                return true;
            }
            y += CARD_HEIGHT + 2;
            if (module == expanded) {
                int settingsTop = y;
                if (clickSettings(module, x + 6, settingsTop, right - 4, mouseX, mouseY)) {
                    return true;
                }
                y = settingsTop + settingsHeight(module);
            }
        }
        return false;
    }

    private boolean resolveDropdownClick(double mouseX, double mouseY, int x) {
        if (openDropdown == null) {
            return false;
        }
        // Recompute the dropdown position from the layout is complex; instead resolve by row
        // using the stored dropdown top captured at draw time.
        int rowIndex = (int) ((mouseY - dropdownTop) / 12);
        if (rowIndex >= 0 && rowIndex < openDropdown.values().size()) {
            openDropdown.setByIndex(rowIndex);
            GuiSounds.click();
        }
        return true;
    }

    /** Top y of the currently open dropdown, captured during draw for hit-testing. */
    private int dropdownTop;

    private void toggleModule(Module module) {
        module.toggle(modules);
        FavoritesStore store = favoritesStore();
        if (store != null) {
            store.markUsed(module.name());
        }
        if (module.isEnabled()) {
            GuiSounds.toggleOn();
        } else {
            GuiSounds.toggleOff();
        }
        pawBurst(panelLeft + SIDEBAR_WIDTH + 10 + 6, listTop + 6);
        setStatus(module.name() + (module.isEnabled() ? " enabled" : " disabled")
                + (module.lastError() != null ? " (" + module.lastError() + ")" : ""));
        announce(module);
    }

    /**
     * Optional cat-themed activation message, gated behind the theme's cat-cosmetics toggle
     * so it never appears for a user who turned the cute extras off. Deliberately silent
     * about failures (the status line already carries them).
     */
    private void announce(Module module) {
        if (module.lastError() != null || !theme.settings().catCosmetics) {
            return;
        }
        var notifications = com.logvex.crystal.client.CrystalClient.get() == null
                ? null : com.logvex.crystal.client.CrystalClient.get().notifications();
        if (notifications == null) {
            return;
        }
        notifications.cute(module.name() + (module.isEnabled() ? " \u2661 on" : " \u2661 off"));
    }

    private int settingsHeight(Module module) {
        int height = 0;
        if (module.description() != null && !module.description().isEmpty()) {
            height += ROW_HEIGHT;
        }
        for (SettingGroup group : module.groups()) {
            if (!group.hasVisibleSettings()) {
                continue;
            }
            height += ROW_HEIGHT;
            for (Setting<?> setting : group.settings()) {
                if (!setting.visible()) {
                    continue;
                }
                height += ROW_HEIGHT + 1;
                if (setting == editingColor) {
                    height += ColorPicker.Channel.values().length * 10;
                }
            }
        }
        return height + 6;
    }

    private boolean clickSettings(Module module, int x, int y, int right, double mouseX, double mouseY) {
        if (module.description() != null && !module.description().isEmpty()) {
            y += ROW_HEIGHT;
        }
        for (SettingGroup group : module.groups()) {
            if (!group.hasVisibleSettings()) {
                continue;
            }
            y += ROW_HEIGHT;
            for (Setting<?> setting : group.settings()) {
                if (!setting.visible()) {
                    continue;
                }
                if (mouseY >= y && mouseY <= y + ROW_HEIGHT) {
                    activateSetting(module, setting, mouseX, right);
                    return true;
                }
                y += ROW_HEIGHT + 1;
                if (setting == editingColor && setting instanceof ColorSetting color) {
                    if (clickColorSliders(color, x, y, right, mouseX, mouseY)) {
                        return true;
                    }
                    y += ColorPicker.Channel.values().length * 10;
                }
            }
        }
        return false;
    }

    private void activateSetting(Module module, Setting<?> setting, double mouseX, int right) {
        if (shiftDown()) {
            setting.reset();
            setStatus(setting.name() + " reset to default");
            GuiSounds.click();
            return;
        }
        if (setting instanceof BooleanSetting bool) {
            bool.toggle();
            GuiSounds.click();
        } else if (setting instanceof KeybindSetting) {
            bindingModule = module;
            bindingSetting = setting;
            setStatus("Press a key to bind " + module.name());
        } else if (setting instanceof ColorSetting color) {
            editingColor = editingColor == color ? null : color;
            editingChannel = ColorPicker.Channel.RED;
        } else if (setting instanceof IntSetting integer) {
            integer.setFraction((mouseX - (right - 84)) / 79.0);
        } else if (setting instanceof DoubleSetting decimal) {
            decimal.setFraction((mouseX - (right - 84)) / 79.0);
        } else if (setting instanceof EnumSetting<?> enumeration) {
            openDropdown = openDropdown == enumeration ? null : enumeration;
        } else if (setting instanceof StringSetting string) {
            setStatus("Use ; set " + module.name() + " " + string.name() + " <value>");
        }
    }

    private boolean shiftDown() {
        var window = client == null ? null : client.getWindow();
        return window != null && net.minecraft.client.util.InputUtil.isKeyPressed(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    private boolean clickColorSliders(ColorSetting color, int x, int y, int right, double mouseX, double mouseY) {
        int sliderLeft = x + 60;
        int sliderWidth = right - sliderLeft - 8;
        for (int i = 0; i < ColorPicker.Channel.values().length; i++) {
            int sy = y + i * 10;
            if (mouseY >= sy && mouseY <= sy + 8) {
                editingChannel = ColorPicker.Channel.values()[i];
                color.set(ColorPicker.withComponent(color.argb(), editingChannel,
                        ColorPicker.valueForX(mouseX, sliderLeft, sliderWidth)));
                return true;
            }
        }
        return false;
    }

    private boolean handleAppearanceClick(double mouseX, double mouseY, int button) {
        int x = panelLeft + SIDEBAR_WIDTH + 10;
        int right = panelRight - 6;
        if (mouseX < x || mouseX > right || mouseY < listTop || mouseY > listBottom) {
            return false;
        }
        var s = theme.settings();
        int y = listTop - (int) scroll;
        // Preset grid.
        ThemePreset[] presets = ThemePreset.values();
        int colW = (right - x - 6) / 2;
        y += 14;
        for (int i = 0; i < presets.length; i++) {
            int px = x + (i % 2) * (colW + 6);
            int py = y + (i / 2) * 18;
            if (inRect(mouseX, mouseY, px, py, colW, 15)) {
                theme.applyPreset(presets[i]);
                GuiSounds.toggleOn();
                setStatus("Theme: " + presets[i].label());
                return true;
            }
        }
        y += ((presets.length + 1) / 2) * 18 + 8;

        // Sliders: id -1 primary, -2 secondary, else ThemeSettings index. The click rows
        // must be walked in the same order the rows are drawn, or the wrong slider is hit.
        for (int id : SLIDER_IDS) {
            if (inRect(mouseX, mouseY, x, y, right - x, ROW_HEIGHT)) {
                float fraction = (float) Math.max(0, Math.min(1, (mouseX - (right - 100)) / 94.0));
                applyAppearanceSlider(id, fraction);
                return true;
            }
            y += ROW_HEIGHT + 1;
        }
        // Toggles 10..17.
        for (int id = 10; id <= 17; id++) {
            if (inRect(mouseX, mouseY, x, y, right - x, ROW_HEIGHT)) {
                toggleAppearance(id);
                GuiSounds.click();
                return true;
            }
            y += ROW_HEIGHT + 1;
        }
        y += 6 + 14;
        for (ThemeVariant variant : ThemeVariant.values()) {
            int px = x + variant.ordinal() * 90;
            if (inRect(mouseX, mouseY, px, y, 84, 15)) {
                theme.setVariant(variant);
                GuiSounds.toggleOn();
                return true;
            }
        }
        return false;
    }

    private void applyAppearanceSlider(int id, float fraction) {
        var s = theme.settings();
        switch (id) {
            case -1 -> {
                int r = (int) (fraction * 255);
                s.setPrimary(ColorUtil.of(r, (int) (255 - fraction * 80), 255, 255));
            }
            case -2 -> s.setSecondary(ColorUtil.of((int) (120 + fraction * 120), (int) (200 + fraction * 55), 255, 255));
            case 1 -> s.backgroundOpacity = fraction;
            case 2 -> s.gradientIntensity = fraction;
            case 3 -> s.glowIntensity = fraction;
            case 4 -> s.cornerRadius = Math.round(fraction * 12);
            case 5 -> s.uiScale = 0.75f + fraction * 0.75f;
            case 6 -> s.animationSpeed = fraction * 2f;
            default -> {
            }
        }
        theme.update();
    }

    private void toggleAppearance(int id) {
        var s = theme.settings();
        switch (id) {
            case 10 -> s.decorations = !s.decorations;
            case 11 -> s.catCosmetics = !s.catCosmetics;
            case 12 -> s.reducedMotion = !s.reducedMotion;
            case 13 -> s.blur = !s.blur;
            case 14 -> s.customCursor = !s.customCursor;
            case 15 -> s.guiSounds = !s.guiSounds;
            case 16 -> s.splashText = !s.splashText;
            case 17 -> s.easterEgg = !s.easterEgg;
            default -> {
            }
        }
        theme.update();
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingPanel) {
            double mouseX = click.x() / uiScale();
            double mouseY = click.y() / uiScale();
            panelOffsetX = (int) Math.round(mouseX - dragGrabX) - 18;
            panelOffsetY = (int) Math.round(mouseY - dragGrabY) - 18;
            clampPanelOffset();
            return true;
        }
        if (editingColor != null) {
            int x = panelLeft + SIDEBAR_WIDTH + 10 + 6 + 60;
            int right = panelRight - 6 - 4;
            int sliderWidth = right - x - 8;
            editingColor.set(ColorPicker.withComponent(editingColor.argb(), editingChannel,
                    ColorPicker.valueForX(click.x() / uiScale(), x, sliderWidth)));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingPanel) {
            draggingPanel = false;
            theme.settings().guiX = panelOffsetX;
            theme.settings().guiY = panelOffsetY;
            theme.update();
        }
        return super.mouseReleased(click);
    }

    /** Keeps the dragged window from being pulled entirely off-screen. */
    private void clampPanelOffset() {
        int maxX = Math.max(0, vw() - 120);
        int maxY = Math.max(0, vh() - 80);
        panelOffsetX = Math.max(-vw() + 120, Math.min(maxX, panelOffsetX));
        panelOffsetY = Math.max(-vh() + 80, Math.min(maxY, panelOffsetY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 16));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (bindingSetting != null) {
            if (bindingSetting instanceof KeybindSetting keybind) {
                keybind.set(key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE ? KeybindSetting.UNBOUND : key);
                setStatus("Bound " + bindingModule.name() + " to " + KeyNames.name(keybind.key()));
            }
            bindingSetting = null;
            bindingModule = null;
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (!search.isEmpty()) {
                search = "";
                return true;
            }
            close();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
            }
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_B && expanded != null) {
            bindingModule = expanded;
            bindingSetting = expanded.keybind();
            setStatus("Press a key to bind " + expanded.name());
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            saveConfig();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_T) {
            page = page == Page.APPEARANCE ? Page.MODULES : Page.APPEARANCE;
            scroll = 0;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (page == Page.APPEARANCE) {
            return super.charTyped(input);
        }
        String typed = input.asString();
        if (!typed.isEmpty() && typed.charAt(0) >= ' ') {
            search += typed;
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }

    private void saveConfig() {
        try {
            config.save();
            setStatus("Saved configuration");
        } catch (IOException e) {
            setStatus("Save failed: " + e.getMessage());
        }
    }

    private List<Module> visibleModules() {
        FavoritesStore store = favoritesStore();
        List<String> recent = store == null ? List.of() : store.recent();
        java.util.function.Predicate<Module> pageFilter = switch (page) {
            case FAVORITES -> module -> store != null && store.isFavorite(module.name());
            case RECENT -> module -> recent.contains(module.name());
            default -> module -> module.category() == selectedCategory;
        };
        // Search spans every category; the page filter only applies when the box is empty.
        // The recent page keeps most-recent-first order unless the user asked for another sort.
        ModuleQuery.SortOrder effective = page == Page.RECENT && sortOrder == ModuleQuery.SortOrder.DEFAULT
                ? ModuleQuery.SortOrder.DEFAULT : sortOrder;
        List<Module> result = ModuleQuery.apply(modules.all(), search, pageFilter, effective, recent);
        if (page == Page.RECENT && sortOrder == ModuleQuery.SortOrder.DEFAULT && search.isBlank()) {
            List<String> recents = recent;
            result.sort((a, b) -> Integer.compare(recents.indexOf(a.name()), recents.indexOf(b.name())));
        }
        return result;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void close() {
        GuiSounds.close();
        super.close();
    }

    /** True while a keybind capture is pending, so the client can suppress its own handling. */
    public boolean isBinding() {
        return bindingSetting != null;
    }

    public HudManager hud() {
        return hud;
    }

    /** True while the GUI is showing the Appearance tab (used by the smoke harness). */
    public boolean isAppearance() {
        return page == Page.APPEARANCE;
    }

    /**
     * True once this screen can actually draw.
     *
     * <p>{@code Screen} snapshots {@code MinecraftClient.textRenderer} when it is constructed,
     * so a screen built before fonts exist holds a null renderer and {@code render()} silently
     * draws nothing. This lets callers detect that state.
     */
    public boolean isRenderable() {
        return textRenderer != null;
    }

    /** Switches the GUI to the Appearance tab (used by the Theme module and commands). */
    public void showAppearance() {
        page = Page.APPEARANCE;
        scroll = 0;
    }

    /** Switches the GUI to a module category. */
    public void showCategory(Category category) {
        this.selectedCategory = category;
        this.page = Page.MODULES;
        this.scroll = 0;
    }

    /** Switches the GUI to the Favourites page. */
    public void showFavorites() {
        this.page = Page.FAVORITES;
        this.search = "";
        this.scroll = 0;
    }

    /** Switches the GUI to the Recently-used page. */
    public void showRecent() {
        this.page = Page.RECENT;
        this.search = "";
        this.scroll = 0;
    }
}
