package com.logvex.crystal.gui;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.world.BuildPlannerModule;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.world.build.BlueprintLibrary;
import com.logvex.crystal.world.build.BuildPlanner;
import com.logvex.crystal.world.build.Schematic;
import com.logvex.crystal.world.util.CoordinateMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The World Tools interface: a searchable, categorised catalogue of the building toolkit and the
 * world utilities, with a live preview panel on the right.
 *
 * <p>Every entry performs a real operation against the shared {@link BuildPlannerModule} library
 * or the client's own stores - there is no decorative row. The building entries edit the plan
 * (place, move, rotate, mirror, undo, redo, save) and the world entries toggle the corresponding
 * WorldInfo section or report the live reading. The preview panel shows the selected placement's
 * dimensions, block count and the missing-material checklist computed from the player's inventory.
 */
public final class WorldToolsScreen extends Screen {

    private static final int ROW_H = 13;
    private static final int BUTTON_H = 15;

    private enum Area {
        BUILD("Building"),
        WORLD("World"),
        STORAGE("Storage");

        private final String label;

        Area(String label) {
            this.label = label;
        }
    }

    /** One actionable tool entry. The action returns a status line to show the user. */
    private record Tool(String name, Area area, String description, java.util.function.Supplier<String> action) {
    }

    private final ThemeManager theme;
    private final CrystalClient client;

    private String search = "";
    private Area filter = Area.BUILD;
    private double scroll;
    private int maxScroll;
    private int selected = -1;
    private String status = "";
    private long statusAt;

    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int tabY;

    public WorldToolsScreen(ThemeManager theme, CrystalClient client) {
        super(Text.literal("World Tools"));
        this.theme = theme;
        this.client = client;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private BuildPlannerModule planner() {
        return client == null || client.modules() == null ? null
                : client.modules().byClass(BuildPlannerModule.class);
    }

    private void setStatus(String message) {
        this.status = message == null ? "" : message;
        this.statusAt = System.currentTimeMillis();
    }

    // ---------------------------------------------------------------- tools

    private List<Tool> tools() {
        List<Tool> tools = new ArrayList<>();
        tools.add(new Tool("Place demo", Area.BUILD,
                "Place the built-in 3x3 floor schematic at your feet.",
                () -> {
                    var module = planner();
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (module == null || mc.player == null) {
                        return "not in a world";
                    }
                    var placement = module.library().place("demo-3x3-floor",
                            mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
                    return "placed " + placement.describe();
                }));
        tools.add(new Tool("Rotate selection", Area.BUILD,
                "Rotate the first placement 90 degrees clockwise.",
                () -> {
                    var module = planner();
                    if (module == null || module.library().placements().isEmpty()) {
                        return "no placements";
                    }
                    String id = module.library().placements().get(0).id();
                    module.library().rotate(id);
                    return "rotated " + id;
                }));
        tools.add(new Tool("Mirror selection", Area.BUILD,
                "Flip the first placement across the X axis.",
                () -> {
                    var module = planner();
                    if (module == null || module.library().placements().isEmpty()) {
                        return "no placements";
                    }
                    String id = module.library().placements().get(0).id();
                    module.library().mirror(id);
                    return "mirrored " + id;
                }));
        tools.add(new Tool("Undo", Area.BUILD, "Undo the last local planning edit.",
                () -> {
                    var module = planner();
                    if (module == null) {
                        return "planner unavailable";
                    }
                    String label = module.library().undo();
                    return label == null ? "nothing to undo" : "undid " + label;
                }));
        tools.add(new Tool("Redo", Area.BUILD, "Redo the last undone planning edit.",
                () -> {
                    var module = planner();
                    if (module == null) {
                        return "planner unavailable";
                    }
                    String label = module.library().redo();
                    return label == null ? "nothing to redo" : "redid " + label;
                }));
        tools.add(new Tool("Save plan", Area.BUILD, "Persist schematics and placements to disk.",
                () -> {
                    var module = planner();
                    if (module == null) {
                        return "planner unavailable";
                    }
                    return module.save() ? "plan saved" : "save failed";
                }));
        tools.add(new Tool("Clear plan", Area.BUILD, "Remove every placement (schematics stay).",
                () -> {
                    var module = planner();
                    if (module == null) {
                        return "planner unavailable";
                    }
                    int removed = module.library().placements().size();
                    for (var placement : new ArrayList<>(module.library().placements())) {
                        module.library().remove(placement.id());
                    }
                    return "cleared " + removed + " placement(s)";
                }));
        tools.add(new Tool("Missing materials", Area.BUILD,
                "Report the materials still needed for the plan.",
                () -> {
                    var module = planner();
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (module == null || mc.player == null) {
                        return "not in a world";
                    }
                    Schematic combined = combinedSchematic(module.library());
                    var missing = BuildPlanner.missing(combined, item -> countItem(mc, item));
                    if (missing.isEmpty()) {
                        return "all materials present";
                    }
                    StringBuilder builder = new StringBuilder("missing:");
                    missing.forEach((item, count) -> builder.append(' ').append(item).append(" x").append(count));
                    return builder.toString();
                }));

        tools.add(new Tool("Coordinates", Area.WORLD, "Print your exact position and chunk.",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null) {
                        return "not in a world";
                    }
                    var pos = mc.player.getEntityPos();
                    return CoordinateMath.format((int) pos.x, (int) pos.y, (int) pos.z);
                }));
        tools.add(new Tool("Distance to waypoint", Area.WORLD,
                "Report the distance to the nearest waypoint in this dimension.",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (client == null || mc.player == null || mc.world == null) {
                        return "not in a world";
                    }
                    var pos = mc.player.getEntityPos();
                    String dimension = mc.world.getRegistryKey().getValue().toString();
                    var list = client.waypoints().nearest(dimension, pos.x, pos.y, pos.z);
                    if (list.isEmpty()) {
                        return "no waypoints here";
                    }
                    var waypoint = list.get(0);
                    double distance = CoordinateMath.distance(pos.x, pos.y, pos.z,
                            waypoint.x(), waypoint.y(), waypoint.z());
                    return String.format(Locale.ROOT, "%s is %.0f blocks away", waypoint.name(), distance);
                }));
        tools.add(new Tool("Toggle chunk borders", Area.WORLD,
                "Show or hide the chunk border overlay.",
                () -> toggleWorldInfoSection("Chunks")));
        tools.add(new Tool("Toggle light level", Area.WORLD,
                "Show or hide the light-level readout.",
                () -> toggleWorldInfoSection("Light")));
        tools.add(new Tool("Toggle biome", Area.WORLD,
                "Show or hide the biome readout.",
                () -> toggleWorldInfoSection("Biome")));
        tools.add(new Tool("Toggle time/weather", Area.WORLD,
                "Show or hide the world time and weather readout.",
                () -> toggleWorldInfoSection("Time")));
        tools.add(new Tool("Toggle entity counts", Area.WORLD,
                "Show or hide nearby entity counts.",
                () -> toggleWorldInfoSection("Counts")));

        tools.add(new Tool("Storage summary", Area.STORAGE,
                "Scan your inventory (and any open container) and report item totals.",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    var module = client == null ? null
                            : client.modules().byClass(com.logvex.crystal.module.automation.AutoTrashAutomation.class);
                    var snapshot = com.logvex.crystal.storage.StorageScanner.scan(mc,
                            module == null ? java.util.Set.of() : module.protectedItems());
                    if (snapshot.isEmpty()) {
                        return "nothing to scan";
                    }
                    var totals = snapshot.totals();
                    StringBuilder sb = new StringBuilder();
                    int shown = 0;
                    for (var entry : totals.entrySet()) {
                        if (shown++ >= 8) {
                            sb.append(", +").append(totals.size() - 8).append(" more");
                            break;
                        }
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(entry.getKey()).append('x').append(entry.getValue());
                    }
                    return snapshot.stackCount() + " stack(s), " + snapshot.itemCount() + " item(s): " + sb;
                }));
        tools.add(new Tool("Duplicate stacks", Area.STORAGE,
                "Find item ids that appear in more than one stack across accessible inventories.",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    var module = client == null ? null
                            : client.modules().byClass(com.logvex.crystal.module.automation.AutoTrashAutomation.class);
                    var snapshot = com.logvex.crystal.storage.StorageScanner.scan(mc,
                            module == null ? java.util.Set.of() : module.protectedItems());
                    var duplicates = snapshot.duplicates();
                    if (duplicates.isEmpty()) {
                        return "no duplicate stacks";
                    }
                    return duplicates.size() + " item id(s) duplicated: "
                            + String.join(", ", duplicates.keySet().stream().limit(8).toList());
                }));
        tools.add(new Tool("Container contents", Area.STORAGE,
                "Report the stacks in the currently open container.",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    var handler = com.logvex.crystal.automation.inventory.InventoryActions.handler(mc);
                    if (handler == null || !com.logvex.crystal.automation.inventory.InventoryActions.containerOpen(mc)) {
                        return "no container open";
                    }
                    int occupied = com.logvex.crystal.automation.inventory.InventoryActions.containerOccupied(handler);
                    return occupied + " occupied slot(s)";
                }));
        tools.add(new Tool("Protected items", Area.STORAGE,
                "List the item ids protected from sorting and trash.",
                () -> {
                    var module = client == null ? null
                            : client.modules().byClass(com.logvex.crystal.module.automation.AutoTrashAutomation.class);
                    return module == null ? "trash module unavailable" : module.protectedSummary();
                }));
        tools.add(new Tool("Item database", Area.STORAGE,
                "Search the offline item catalogue by id or name (uses the search box).",
                () -> {
                    var db = com.logvex.crystal.storage.ItemDatabase.get();
                    var matches = db.search(search);
                    if (matches.isEmpty()) {
                        return "no catalogue match for '" + search + "'";
                    }
                    var first = matches.get(0);
                    return matches.size() + " match(es); e.g. " + first.id() + " = " + first.name()
                            + " (" + first.category() + ", stack " + first.maxStack() + ")";
                }));
        tools.add(new Tool("Container label", Area.STORAGE,
                "Label the currently open container with the search box text.",
                () -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    var handler = com.logvex.crystal.automation.inventory.InventoryActions.handler(mc);
                    if (handler == null
                            || !com.logvex.crystal.automation.inventory.InventoryActions.containerOpen(mc)) {
                        return "no container open";
                    }
                    if (search.isBlank()) {
                        return "type a label in the search box first";
                    }
                    if (mc == null || mc.player == null || mc.world == null) {
                        return "not in a world";
                    }
                    var pos = mc.player.getBlockPos();
                    String key = com.logvex.crystal.storage.ContainerLabels.key(
                            mc.world.getRegistryKey().getValue().toString(),
                            pos.getX(), pos.getY(), pos.getZ());
                    var labels = com.logvex.crystal.storage.ContainerLabels.get();
                    labels.set(key, search);
                    labels.save();
                    return "labelled " + key + " as '" + search + "'";
                }));
        return tools;
    }

    private String toggleWorldInfoSection(String label) {
        var module = client == null ? null
                : client.modules().byClass(com.logvex.crystal.module.world.WorldInfoModule.class);
        if (module == null) {
            return "WorldInfo unavailable";
        }
        BooleanSettingRef setting = findWorldInfoToggle(module, label);
        if (setting == null) {
            return "no '" + label + "' toggle";
        }
        setting.set(!setting.get());
        return label + (setting.get() ? " on" : " off");
    }

    /** A tiny adapter so the screen can flip a WorldInfo BooleanSetting without importing it here. */
    private record BooleanSettingRef(com.logvex.crystal.setting.BooleanSetting setting) {
        boolean get() {
            return setting.value();
        }

        void set(boolean value) {
            setting.set(value);
        }
    }

    private BooleanSettingRef findWorldInfoToggle(Module module, String label) {
        for (SettingGroup group : module.groups()) {
            for (Setting<?> setting : group.settings()) {
                if (setting.name().toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT))
                        && setting instanceof com.logvex.crystal.setting.BooleanSetting bool) {
                    return new BooleanSettingRef(bool);
                }
            }
        }
        return null;
    }

    private Schematic combinedSchematic(BlueprintLibrary library) {
        List<Schematic.Block> blocks = new ArrayList<>();
        int offset = 0;
        for (var placement : library.placements()) {
            Schematic schematic = library.effectiveSchematic(placement);
            if (schematic == null) {
                continue;
            }
            for (Schematic.Block block : schematic.blocks()) {
                blocks.add(new Schematic.Block(block.x() + offset, block.y(), block.z(), block.itemId()));
            }
            offset += 8;
        }
        return Schematic.of("plan", blocks);
    }

    private static int countItem(MinecraftClient mc, String itemId) {
        int count = 0;
        for (int slot = 0; slot < com.logvex.crystal.player.InventoryUtil.MAIN_SIZE; slot++) {
            var stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()
                    && net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString().equals(itemId)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private List<Tool> filtered() {
        List<Tool> result = new ArrayList<>();
        String needle = search.toLowerCase(Locale.ROOT).trim();
        for (Tool tool : tools()) {
            if (tool.area() != filter) {
                continue;
            }
            if (needle.isEmpty() || tool.name().toLowerCase(Locale.ROOT).contains(needle)
                    || tool.description().toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(tool);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        Palette palette = theme.palette();
        context.fill(0, 0, width, height, com.logvex.crystal.util.ColorUtil.withAlpha(0xFF000000, 170));

        int left = 20;
        int top = 18;
        int right = width - 20;
        int bottom = height - 18;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u26CF World Tools", left + 14, top + 8, palette.primary());
        context.drawTextWithShadow(textRenderer, "search \u00b7 click to run \u00b7 Esc closes",
                left + 14, top + 20, palette.textDim());

        // Search box.
        int searchY = top + 34;
        int searchWidth = 190;
        RenderUtil.roundedPanel(context, left + 14, searchY, searchWidth, 16, palette.radius(4),
                palette.card(), palette.borderSoft());
        context.drawTextWithShadow(textRenderer,
                search.isEmpty() ? "\u2315 search\u2026" : "\u2315 " + search,
                left + 20, searchY + 4, search.isEmpty() ? palette.textDim() : palette.text());

        // Area tabs.
        tabY = searchY + 22;
        int tx = left + 14;
        for (Area area : Area.values()) {
            int w = textRenderer.getWidth(area.label) + 16;
            boolean active = area == filter;
            RenderUtil.roundedPanel(context, tx, tabY, w, 16, palette.radius(4),
                    active ? palette.primary() : palette.card(),
                    active ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, area.label, tx + 8, tabY + 4,
                    active ? palette.textOnAccent() : palette.textDim());
            tx += w + 6;
        }

        // List.
        int panelWidth = (right - left) / 2;
        listLeft = left + 14;
        listRight = left + 14 + panelWidth - 20;
        listTop = tabY + 22;
        listBottom = bottom - 34;
        renderList(context, palette, mouseX, mouseY);

        // Preview panel.
        renderPreview(context, palette, listRight + 12, listTop, right - 14, listBottom);

        String footer = !status.isEmpty() && System.currentTimeMillis() - statusAt < 5000
                ? status : "select a tool to run it";
        context.drawTextWithShadow(textRenderer, footer, left + 14, bottom - 14,
                !status.isEmpty() ? palette.primary() : palette.textDim());

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderList(DrawContext context, Palette palette, int mouseX, int mouseY) {
        List<Tool> rows = filtered();
        context.enableScissor(listLeft, listTop, listRight, listBottom);
        int y = listTop - (int) scroll;
        for (int i = 0; i < rows.size(); i++) {
            Tool tool = rows.get(i);
            if (y + ROW_H > listTop - ROW_H && y < listBottom) {
                boolean hovered = mouseX >= listLeft && mouseX <= listRight
                        && mouseY >= y && mouseY <= y + ROW_H - 1;
                boolean chosen = i == selected;
                RenderUtil.roundedPanel(context, listLeft, y, listRight - listLeft, ROW_H - 1,
                        palette.radius(3),
                        chosen ? palette.panelHover() : hovered ? palette.panel() : palette.card(),
                        chosen ? palette.primary() : palette.borderSoft());
                context.drawTextWithShadow(textRenderer, tool.name(), listLeft + 6, y + 3, palette.text());
            }
            y += ROW_H;
        }
        int contentHeight = y - (listTop - (int) scroll);
        maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        context.disableScissor();
    }

    private void renderPreview(DrawContext context, Palette palette, int x, int top, int right, int bottom) {
        int width = right - x;
        RenderUtil.roundedPanel(context, x, top, width, bottom - top, palette.radius(6),
                palette.card(), palette.borderSoft());
        int y = top + 8;
        context.drawTextWithShadow(textRenderer, "Preview", x + 8, y, palette.secondary());
        y += 16;

        var module = planner();
        if (module == null) {
            context.drawTextWithShadow(textRenderer, "build planner unavailable", x + 8, y, palette.textDim());
            return;
        }
        BlueprintLibrary library = module.library();
        context.drawTextWithShadow(textRenderer, library.schematics().size() + " schematic(s)", x + 8, y,
                palette.text());
        y += 12;
        context.drawTextWithShadow(textRenderer, library.placements().size() + " placement(s)", x + 8, y,
                palette.text());
        y += 12;
        context.drawTextWithShadow(textRenderer, library.plannedBlockCount() + " planned block(s)", x + 8, y,
                palette.text());
        y += 16;

        context.drawTextWithShadow(textRenderer, "Placements", x + 8, y, palette.secondary());
        y += 12;
        MinecraftClient mc = MinecraftClient.getInstance();
        int centerX = mc.player == null ? 0 : mc.player.getBlockX();
        int centerZ = mc.player == null ? 0 : mc.player.getBlockZ();
        for (var placement : library.placements()) {
            if (y > bottom - 24) {
                break;
            }
            Schematic schematic = library.effectiveSchematic(placement);
            int previewCount = library.previewBlocks(placement, centerX, centerZ, 48, 2048).size();
            String line = placement.schematicName() + " @ " + placement.x() + "," + placement.z()
                    + (schematic == null ? "" : " " + schematic.sizeX() + "x" + schematic.sizeZ())
                    + " \u00b7 " + previewCount + " shown";
            context.drawTextWithShadow(textRenderer, line, x + 8, y, palette.textDim());
            y += 11;
        }

        y = bottom - 18;
        var history = library.history();
        String historyLine = "undo " + history.undoDepth() + " \u00b7 redo " + history.redoDepth()
                + (history.canUndo() ? " \u00b7 next: " + history.nextUndoLabel() : "");
        context.drawTextWithShadow(textRenderer, historyLine, x + 8, y, palette.textDim());
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.y() >= tabY && click.y() <= tabY + 16) {
            int tx = 20 + 14;
            for (Area area : Area.values()) {
                int w = textRenderer.getWidth(area.label) + 16;
                if (click.x() >= tx && click.x() <= tx + w) {
                    filter = area;
                    selected = -1;
                    scroll = 0;
                    return true;
                }
                tx += w + 6;
            }
        }
        if (click.y() >= listTop && click.y() <= listBottom
                && click.x() >= listLeft && click.x() <= listRight) {
            int index = (int) ((click.y() - listTop + scroll) / ROW_H);
            List<Tool> rows = filtered();
            if (index >= 0 && index < rows.size()) {
                selected = index;
                Tool tool = rows.get(index);
                setStatus(runTool(tool));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private String runTool(Tool tool) {
        try {
            return tool.name() + ": " + tool.action().get();
        } catch (RuntimeException e) {
            return tool.name() + " failed: " + e.getMessage();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 16));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!search.isEmpty()) {
                search = "";
                return true;
            }
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                scroll = 0;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        String typed = input.asString();
        if (!typed.isEmpty() && typed.charAt(0) >= ' ') {
            search = search + typed;
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }
}
