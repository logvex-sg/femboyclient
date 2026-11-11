package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * A dedicated HUD layout editor.
 *
 * <p>Drag any element to reposition it (with alignment guides and grid snapping), right-click
 * to hide/show it, scroll to scale, shift-scroll to fade, and drag the bottom-right grip to
 * resize. Layout changes write straight into the shared {@link HudLayout}, so the effect is
 * visible immediately and is persisted with the module config.
 *
 * <p>Every structural edit takes an undo snapshot, so Ctrl+Z / Ctrl+Y step through the edit
 * history. {@code P} toggles a preview mode that hides the editor chrome for a clean look.
 */
public final class HudEditorScreen extends Screen {

    /** Which drag is in flight. */
    private enum Drag { NONE, MOVE, RESIZE }

    private static final int GRIP = 6;
    private static final int GUIDE_THRESHOLD = 5;

    private final HudManager hud;
    private final com.logvex.crystal.theme.ThemeManager theme;
    private final HudLayoutHistory history = new HudLayoutHistory();

    private Drag drag = Drag.NONE;
    private String active;
    private int dragOffsetX;
    private int dragOffsetY;
    private double mouseX;
    private double mouseY;
    private boolean preview;
    private HudGuides.Result guides = HudGuides.Result.EMPTY;
    private java.util.Map<String, int[]> anchors = java.util.Map.of();

    /** Whether the saved-layouts side panel is open. */
    private boolean layoutPanel;
    /** Whether the "save layout as" name entry is capturing keystrokes. */
    private boolean namingLayout;
    private String nameBuffer = "";
    private String panelStatus = "";
    /** Cached layout names, refreshed whenever the panel opens or the set changes. */
    private java.util.List<String> layoutNames = java.util.List.of();
    /** Screen-space rectangles of the panel rows, rebuilt each frame for hit-testing. */
    private java.util.Map<String, int[]> panelRows = java.util.Map.of();

    public HudEditorScreen(HudManager hud, com.logvex.crystal.theme.ThemeManager theme) {
        super(Text.literal("HUD Editor"));
        this.hud = hud;
        this.theme = theme;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Asks the client to persist the layout through the debounced autosave. */
    private static void markDirty() {
        CrystalClient bootstrap = CrystalClient.get();
        if (bootstrap != null) {
            bootstrap.requestSave();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        Palette palette = theme.palette();
        anchors = hud.anchors(client, width);
        applyDrag(mouseX, mouseY);
        if (!preview) {
            hud.renderEditorOverlay(context, client, palette, mouseX, mouseY);
            renderGuides(context, palette);
            renderOverlapWarnings(context, palette);
            renderResizeGrip(context, palette);
            renderChrome(context, palette);
            renderLayoutPanel(context, palette);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    /** Applies the in-flight drag/resize, with grid snapping and alignment guides. */
    private void applyDrag(int mouseX, int mouseY) {
        if (drag == Drag.NONE || active == null) {
            guides = HudGuides.Result.EMPTY;
            return;
        }
        HudElement element = hud.element(active);
        if (element == null) {
            return;
        }
        HudLayout.Entry entry = hud.layout().entry(active);
        if (drag == Drag.MOVE) {
            entry.customPosition = true;
            int w = Math.max(40, hud.widthOf(element, entry, client));
            int h = Math.max(12, hud.heightOf(element, entry, client));
            int x = HudSnap.snap(mouseX - dragOffsetX, 4);
            int y = HudSnap.snap(mouseY - dragOffsetY, 4);
            // Alignment guides: snap to another element's edges/centres when close.
            guides = HudGuides.find(new HudSnap.Rect(x, y, w, h), otherRects(active), GUIDE_THRESHOLD);
            if (guides.snapX() != null) {
                x = guides.snapX();
            }
            if (guides.snapY() != null) {
                y = guides.snapY();
            }
            entry.x = HudSnap.clamp(x, 0, Math.max(0, width - w));
            entry.y = HudSnap.clamp(y, 0, Math.max(0, height - h));
        } else {
            int w = Math.max(24, HudSnap.snap(mouseX - entry.x, 2));
            int h = Math.max(12, HudSnap.snap(mouseY - entry.y, 2));
            entry.width = Math.min(w, Math.max(24, width - entry.x));
            entry.height = Math.min(h, Math.max(12, height - entry.y));
            entry.customPosition = true;
        }
    }

    /** Draws the alignment guide lines for the current drag. */
    private void renderGuides(DrawContext context, Palette palette) {
        int colour = ColorUtil.withAlpha(palette.primary(), 180);
        for (int x : guides.vertical()) {
            context.fill(x, 0, x + 1, height, colour);
        }
        for (int y : guides.horizontal()) {
            context.fill(0, y, width, y + 1, colour);
        }
    }

    /** Draws a resize grip at the bottom-right of the hovered element. */
    private void renderResizeGrip(DrawContext context, Palette palette) {
        String hovered = hoveredElement((int) mouseX, (int) mouseY);
        if (hovered == null) {
            return;
        }
        int[] b = boundsOf(hud.element(hovered));
        int gx = b[0] + b[2];
        int gy = b[1] + b[3];
        context.fill(gx - GRIP, gy - 1, gx, gy, ColorUtil.withAlpha(palette.primary(), 220));
        context.fill(gx - 1, gy - GRIP, gx, gy, ColorUtil.withAlpha(palette.primary(), 220));
    }

    private void renderChrome(DrawContext context, Palette palette) {
        RenderUtil.roundedPanel(context, 8, 8, 320, 58, 6, ColorUtil.withAlpha(palette.panel(), 220), palette.border());
        context.drawTextWithShadow(textRenderer, "\u2661 HUD editor", 14, 12, palette.primary());
        context.drawTextWithShadow(textRenderer, "drag \u00b7 right-click hide \u00b7 scroll scale \u00b7 shift-scroll fade",
                14, 22, palette.textDim());
        context.drawTextWithShadow(textRenderer, "corner grip resize \u00b7 [ ] layer \u00b7 R reset \u00b7 B panel \u00b7 N outline",
                14, 32, palette.textDim());
        context.drawTextWithShadow(textRenderer, "Ctrl+Z undo \u00b7 Ctrl+Y redo \u00b7 Shift+R reset all \u00b7 P preview \u00b7 L layouts",
                14, 42, palette.textDim());
        String hint = hud.layout().all().size() + " elements"
                + (history.canUndo() ? " \u00b7 undo " + history.undoDepth() : "");
        context.drawTextWithShadow(textRenderer, hint, 14, 52, palette.textDim());
    }

    /**
     * Draws the saved-layouts side panel: a list of stored layouts with Load/Delete actions, an
     * import/export row and a "save as" name field. Rows are hit-tested in {@link #mouseClicked}
     * using the rectangles rebuilt here, so the panel and its input handling stay in sync.
     */
    private void renderLayoutPanel(DrawContext context, Palette palette) {
        var library = library();
        if (library == null) {
            return;
        }
        int px = width - 236;
        int py = 8;
        int ph = 210;
        RenderUtil.roundedPanel(context, px, py, 228, ph, 6,
                ColorUtil.withAlpha(palette.panel(), 226), palette.border());
        context.drawTextWithShadow(textRenderer, "\u2661 HUD layouts", px + 8, py + 8, palette.primary());

        java.util.Map<String, int[]> rows = new java.util.LinkedHashMap<>();
        layoutNames = library.names();
        int y = py + 24;
        if (layoutNames.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "none saved yet", px + 10, y, palette.textDim());
            y += 14;
        }
        for (String name : layoutNames) {
            if (y > py + ph - 60) {
                context.drawTextWithShadow(textRenderer, "\u2026", px + 10, y, palette.textDim());
                break;
            }
            context.drawTextWithShadow(textRenderer, name, px + 10, y + 3, palette.text());
            drawChip(context, palette, "Load", px + 118, y, 46, "load:" + name, rows);
            drawChip(context, palette, "\u2715", px + 168, y, 20, "delete:" + name, rows);
            y += 16;
        }
        panelRows = rows;

        int footer = py + ph - 46;
        drawChip(context, palette, "Save as\u2026", px + 8, footer, 74, "save", rows);
        drawChip(context, palette, "Import", px + 86, footer, 52, "import", rows);
        drawChip(context, palette, "Export", px + 142, footer, 56, "export", rows);
        panelRows = rows;

        if (namingLayout) {
            String field = nameBuffer.isEmpty() ? "_" : nameBuffer + "_";
            context.drawTextWithShadow(textRenderer, "name: " + field, px + 10, footer + 18, palette.primary());
        } else if (!panelStatus.isEmpty()) {
            context.drawTextWithShadow(textRenderer, trim(panelStatus, 28), px + 10, footer + 18, palette.textDim());
        }
        context.drawTextWithShadow(textRenderer, "L toggle panel", px + 10, py + ph - 12, palette.textDim());
    }

    private void drawChip(DrawContext context, Palette palette, String label, int x, int y, int w,
                          String key, java.util.Map<String, int[]> rows) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 12;
        int bg = ColorUtil.withAlpha(hovered ? palette.primary() : palette.panelAlt(), hovered ? 200 : 160);
        RenderUtil.roundedPanel(context, x, y, w, 12, 3, bg, palette.border());
        context.drawTextWithShadow(textRenderer, label, x + 4, y + 2, palette.text());
        rows.put(key, new int[]{x, y, w, 12});
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "\u2026";
    }

    /** The layout library, or null when the client is not available (e.g. in a unit test). */
    private static com.logvex.crystal.config.HudLayoutLibrary library() {
        CrystalClient bootstrap = CrystalClient.get();
        return bootstrap == null ? null : bootstrap.hudLayouts();
    }

    /** Runs the panel action for a clicked row key, if it names one. */
    private boolean handlePanelClick(String key) {
        var library = library();
        if (library == null || key == null) {
            return false;
        }
        if (key.equals("save")) {
            namingLayout = true;
            nameBuffer = "";
            panelStatus = "";
            return true;
        }
        if (key.equals("import")) {
            var imported = library.importAll();
            panelStatus = imported.isEmpty() ? "nothing to import" : "imported " + imported.size();
            layoutNames = library.names();
            return true;
        }
        if (key.equals("export")) {
            if (layoutNames.isEmpty()) {
                panelStatus = "nothing to export";
                return true;
            }
            String name = layoutNames.get(0);
            panelStatus = library.export(name) != null ? "exported " + name : "export failed";
            return true;
        }
        if (key.startsWith("load:")) {
            String name = key.substring("load:".length());
            history.push(hud.layout());
            if (library.load(name, hud.layout())) {
                panelStatus = "loaded " + name;
                markDirty();
            } else {
                panelStatus = "load failed";
            }
            return true;
        }
        if (key.startsWith("delete:")) {
            String name = key.substring("delete:".length());
            panelStatus = library.delete(name) ? "deleted " + name : "delete failed";
            layoutNames = library.names();
            return true;
        }
        return false;
    }

    private void commitLayoutName() {
        namingLayout = false;
        var library = library();
        if (library == null) {
            return;
        }
        String name = nameBuffer.trim();
        if (name.isEmpty()) {
            panelStatus = "cancelled";
            return;
        }
        try {
            boolean written = library.save(name, hud.layout(), false);
            panelStatus = written ? "saved " + com.logvex.crystal.config.HudLayoutLibrary.sanitize(name)
                    : "name already used";
            layoutNames = library.names();
        } catch (java.io.IOException e) {
            panelStatus = "save failed";
        }
    }

    /** Draws a soft danger-coloured frame around elements that overlap another element. */
    private void renderOverlapWarnings(DrawContext context, Palette palette) {
        java.util.Map<String, HudSnap.Rect> rects = new java.util.LinkedHashMap<>();
        for (HudElement element : hud.elements()) {
            if (!element.draggable()) {
                continue;
            }
            int[] b = boundsOf(element);
            rects.put(element.id(), new HudSnap.Rect(b[0], b[1], b[2], b[3]));
        }
        java.util.Map<String, String> overlaps = HudSnap.findOverlaps(rects);
        for (String id : overlaps.keySet()) {
            int[] b = boundsOf(hud.element(id));
            RenderUtil.roundedPanel(context, b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, palette.radius(4), 0,
                    ColorUtil.withAlpha(palette.danger(), 200));
        }
    }

    /** Rectangles of every draggable element except {@code excludeId}. */
    private java.util.Map<String, HudSnap.Rect> otherRects(String excludeId) {
        java.util.Map<String, HudSnap.Rect> rects = new java.util.LinkedHashMap<>();
        for (HudElement element : hud.elements()) {
            if (!element.draggable() || element.id().equals(excludeId)) {
                continue;
            }
            int[] b = boundsOf(element);
            rects.put(element.id(), new HudSnap.Rect(b[0], b[1], b[2], b[3]));
        }
        return rects;
    }

    /** The id of the element under the cursor, or null. */
    private String hoveredElement(int mouseX, int mouseY) {
        // Iterate in reverse draw order so the topmost element wins the hit test.
        var order = hud.drawOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            HudElement element = order.get(i);
            if (!element.draggable()) {
                continue;
            }
            int[] bounds = boundsOf(element);
            if (mouseX >= bounds[0] - 2 && mouseX <= bounds[0] + bounds[2] + 2
                    && mouseY >= bounds[1] - 2 && mouseY <= bounds[1] + bounds[3] + 2) {
                return element.id();
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (layoutPanel && !preview) {
            for (var row : panelRows.entrySet()) {
                int[] r = row.getValue();
                if (click.x() >= r[0] && click.x() <= r[0] + r[2]
                        && click.y() >= r[1] && click.y() <= r[1] + r[3]) {
                    if (handlePanelClick(row.getKey())) {
                        return true;
                    }
                }
            }
        }
        var order = hud.drawOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            HudElement element = order.get(i);
            if (!element.draggable()) {
                continue;
            }
            int[] bounds = boundsOf(element);
            boolean onGrip = click.x() >= bounds[0] + bounds[2] - GRIP
                    && click.x() <= bounds[0] + bounds[2] + 2
                    && click.y() >= bounds[1] + bounds[3] - GRIP
                    && click.y() <= bounds[1] + bounds[3] + 2;
            if (click.x() >= bounds[0] - 2 && click.x() <= bounds[0] + bounds[2] + 2
                    && click.y() >= bounds[1] - 2 && click.y() <= bounds[1] + bounds[3] + 2) {
                HudLayout.Entry entry = hud.layout().entry(element.id());
                if (click.button() == 1) {
                    history.push(hud.layout());
                    // Toggle the owning module when there is one, so the element's
                    // visibility and the module's enabled state can never disagree;
                    // an unowned element (e.g. notifications) toggles directly.
                    var owner = hud.moduleForElement(element.id());
                    if (owner != null) {
                        owner.toggle(hud.modules());
                    } else {
                        entry.visible = !entry.visible;
                    }
                } else if (onGrip) {
                    history.push(hud.layout());
                    drag = Drag.RESIZE;
                    active = element.id();
                } else {
                    history.push(hud.layout());
                    drag = Drag.MOVE;
                    active = element.id();
                    dragOffsetX = (int) click.x() - bounds[0];
                    dragOffsetY = (int) click.y() - bounds[1];
                }
                markDirty();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (drag != Drag.NONE) {
            markDirty();
        }
        drag = Drag.NONE;
        active = null;
        guides = HudGuides.Result.EMPTY;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        String hovered = hoveredElement((int) mouseX, (int) mouseY);
        if (hovered != null) {
            HudLayout.Entry entry = hud.layout().entry(hovered);
            history.push(hud.layout());
            // Plain scroll scales the element; Shift+scroll changes its opacity.
            if (shiftDown()) {
                entry.opacity = clamp01(entry.opacity + (float) verticalAmount * 0.05f);
            } else {
                entry.scale = clamp(entry.scale + (float) verticalAmount * 0.05f, 0.5f, 2f);
            }
            markDirty();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean shiftDown() {
        var window = client == null ? null : client.getWindow();
        return window != null && net.minecraft.client.util.InputUtil.isKeyPressed(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    private boolean ctrlDown() {
        var window = client == null ? null : client.getWindow();
        return window != null && (net.minecraft.client.util.InputUtil.isKeyPressed(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                || net.minecraft.client.util.InputUtil.isKeyPressed(window,
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (namingLayout) {
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitLayoutName();
            } else if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                namingLayout = false;
                panelStatus = "cancelled";
            } else if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !nameBuffer.isEmpty()) {
                nameBuffer = nameBuffer.substring(0, nameBuffer.length() - 1);
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            CrystalClient bootstrap = CrystalClient.get();
            if (bootstrap != null) {
                bootstrap.saveNow();
            }
            close();
            return true;
        }
        if (ctrlDown()) {
            if (input.key() == GLFW.GLFW_KEY_Z && history.undo(hud.layout())) {
                markDirty();
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_Y && history.redo(hud.layout())) {
                markDirty();
                return true;
            }
        }
        if (input.key() == GLFW.GLFW_KEY_P) {
            preview = !preview;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_L) {
            layoutPanel = !layoutPanel;
            if (layoutPanel) {
                panelStatus = "";
            }
            return true;
        }
        String hovered = hoveredElement((int) mouseX, (int) mouseY);
        if (hovered != null) {
            HudLayout.Entry entry = hud.layout().entry(hovered);
            switch (input.key()) {
                case GLFW.GLFW_KEY_R -> {
                    history.push(hud.layout());
                    if (shiftDown()) {
                        // Shift+R resets every element's placement in one step.
                        for (HudLayout.Entry e : hud.layout().all().values()) {
                            HudSnap.reset(e);
                        }
                    } else {
                        HudSnap.reset(entry);
                    }
                    markDirty();
                    return true;
                }
                case GLFW.GLFW_KEY_B -> {
                    history.push(hud.layout());
                    entry.background = !entry.background;
                    markDirty();
                    return true;
                }
                case GLFW.GLFW_KEY_N -> {
                    history.push(hud.layout());
                    entry.border = !entry.border;
                    markDirty();
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT_BRACKET -> {
                    history.push(hud.layout());
                    entry.layer--;
                    markDirty();
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT_BRACKET -> {
                    history.push(hud.layout());
                    entry.layer++;
                    markDirty();
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(input);
    }

    /** Returns {x, y, width, height} for an element in its current placement. */
    private int[] boundsOf(HudElement element) {
        int[] anchor = anchors.getOrDefault(element.id(), new int[]{4, 4});
        HudLayout.Entry entry = hud.layout().entry(element.id());
        return new int[]{anchor[0], anchor[1],
                Math.max(40, hud.widthOf(element, entry, client)),
                Math.max(12, hud.heightOf(element, entry, client))};
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        if (namingLayout) {
            String typed = input.asString();
            if (!typed.isEmpty() && typed.charAt(0) >= ' ' && nameBuffer.length() < 40) {
                nameBuffer += typed;
            }
            return true;
        }
        return super.charTyped(input);
    }
}
