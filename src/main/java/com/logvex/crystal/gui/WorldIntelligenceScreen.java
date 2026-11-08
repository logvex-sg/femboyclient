package com.logvex.crystal.gui;

import com.logvex.crystal.gui.dashboard.IntelligenceDashboardState;
import com.logvex.crystal.module.world.WorldScannerModule;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.scan.world.DiscoveryDatabase;
import com.logvex.crystal.scan.world.Observation;
import com.logvex.crystal.scan.world.ScanSnapshot;
import com.logvex.crystal.scan.world.ScanState;
import com.logvex.crystal.scan.world.StructureCategory;
import com.logvex.crystal.scan.world.VerificationStatus;
import com.logvex.crystal.scan.world.WorldIntelligence;
import com.logvex.crystal.scan.world.WorldQuery;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The world-intelligence dashboard: a tabbed workspace over the {@link WorldIntelligence} layer.
 *
 * <p>Five workspaces, each wired to a real method and never to a placeholder:
 * <ul>
 *   <li><b>Overview</b> - live run state and the start/pause/resume/stop/reset controls, plus the
 *       most recent observations.</li>
 *   <li><b>Map</b> - a 2D chunk map of the recorded observations, coloured by category, with a
 *       legend and zoom.</li>
 *   <li><b>Discoveries</b> - a searchable, filterable, sortable list with confirm, dismiss,
 *       annotate and delete actions on the selected record.</li>
 *   <li><b>AI</b> - a natural-language query box with an answer pane; answers come from the
 *       deterministic engine and optionally from the local model.</li>
 *   <li><b>Data</b> - save, export, import, backup, prune and per-dimension delete.</li>
 * </ul>
 *
 * <p>All interaction state lives in {@link IntelligenceDashboardState}, which is Minecraft-free
 * and unit tested; this class only draws that state and maps clicks and keystrokes onto it. Every
 * hit box is captured during render into a field, so a click can never act on something that was
 * not drawn.
 */
public final class WorldIntelligenceScreen extends Screen {

    private static final int ROW_H = 12;
    private static final int BUTTON_H = 16;

    private final ThemeManager theme;
    private final WorldScannerModule module;
    private final IntelligenceDashboardState state = new IntelligenceDashboardState();

    private String status = "";
    private long statusAt;

    // Geometry captured during render so clicks stay in step with what was drawn.
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;

    private int tabY;
    private final int[] tabX = new int[IntelligenceDashboardState.Tab.values().length];
    private final int[] tabW = new int[IntelligenceDashboardState.Tab.values().length];

    private int controlY;
    private int controlX;

    private int searchX;
    private int searchY;
    private int searchW;

    private int filterX;
    private int sortX;

    private int listTop;
    private int listBottom;
    private int actionY;

    private int questionY;
    private int askX;
    private int askW;

    private int dataRowY;
    private int dataRow2Y;

    private int mapCentreChunkX;
    private int mapCentreChunkZ;
    private boolean mapCentred;

    public WorldIntelligenceScreen(ThemeManager theme, WorldScannerModule module) {
        super(Text.literal("World Intelligence"));
        this.theme = theme;
        this.module = module;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private WorldIntelligence intelligence() {
        return module == null ? null : module.intelligence();
    }

    private void setStatus(String message) {
        this.status = message == null ? "" : message;
        this.statusAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null || module == null) {
            return;
        }
        Palette palette = theme.palette();
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, 170));

        panelLeft = 20;
        panelTop = 16;
        panelRight = width - 20;
        panelBottom = height - 16;
        RenderUtil.softShadow(context, panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop,
                palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, panelLeft, panelTop, panelRight - panelLeft,
                panelBottom - panelTop, palette.radius(10), palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2727 World Intelligence", panelLeft + 14, panelTop + 9,
                palette.primary());
        context.drawTextWithShadow(textRenderer, "local only \u00b7 reads loaded chunks \u00b7 Esc closes",
                panelLeft + 14, panelTop + 21, palette.textDim());

        renderTabs(context, palette, panelLeft + 14, panelTop + 34, mouseX, mouseY);

        contentLeft = panelLeft + 14;
        contentRight = panelRight - 14;
        contentTop = panelTop + 58;
        contentBottom = panelBottom - 26;

        switch (state.tab()) {
            case OVERVIEW -> renderOverview(context, palette, mouseX, mouseY);
            case MAP -> renderMap(context, palette);
            case DISCOVERIES -> renderDiscoveries(context, palette, mouseX, mouseY);
            case AI -> renderAi(context, palette, mouseX, mouseY);
            case DATA -> renderData(context, palette, mouseX, mouseY);
        }

        String footer = !status.isEmpty() && System.currentTimeMillis() - statusAt < 5000 ? status : footerText();
        context.drawTextWithShadow(textRenderer, footer, contentLeft, panelBottom - 16,
                !status.isEmpty() ? palette.primary() : palette.textDim());

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private String footerText() {
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            return "intelligence layer not started \u00b7 enable the WorldScanner module";
        }
        DiscoveryDatabase database = intelligence.database();
        return "recorded " + database.size() + " \u00b7 dims " + database.dimensions().size()
                + " \u00b7 ai " + intelligence.ai().backendDescription();
    }

    private void renderTabs(DrawContext context, Palette palette, int x, int y, int mouseX, int mouseY) {
        tabY = y;
        int cx = x;
        IntelligenceDashboardState.Tab[] tabs = IntelligenceDashboardState.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            String label = titleCase(tabs[i]);
            int w = textRenderer.getWidth(label) + 18;
            tabX[i] = cx;
            tabW[i] = w;
            boolean active = tabs[i] == state.tab();
            boolean hovered = mouseX >= cx && mouseX <= cx + w && mouseY >= y && mouseY <= y + 18;
            int fill = active ? palette.panelHover() : hovered ? palette.card() : palette.panel();
            RenderUtil.roundedPanel(context, cx, y, w, 18, palette.radius(4), fill,
                    active ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, label, cx + 9, y + 5,
                    active ? palette.text() : palette.textDim());
            cx += w + 4;
        }
    }

    private static String titleCase(IntelligenceDashboardState.Tab tab) {
        String name = tab.name();
        return name.charAt(0) + name.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ overview

    private void renderOverview(DrawContext context, Palette palette, int mouseX, int mouseY) {
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            context.drawTextWithShadow(textRenderer, "WorldScanner module is not enabled.", contentLeft, contentTop,
                    palette.textDim());
            return;
        }
        ScanSnapshot snapshot = intelligence.scanner().snapshot();
        int col2 = contentLeft + 240;
        int y = contentTop;
        if (snapshot == null) {
            context.drawTextWithShadow(textRenderer, "scanner not initialised", contentLeft, y, palette.textDim());
        } else {
            int ly = y;
            context.drawTextWithShadow(textRenderer, "state " + snapshot.state().label(), contentLeft, ly, palette.text());
            ly += ROW_H;
            context.drawTextWithShadow(textRenderer, "dimension " + WorldQuery.shortDimension(snapshot.dimension()),
                    contentLeft, ly, palette.text());
            ly += ROW_H;
            context.drawTextWithShadow(textRenderer, "explored " + snapshot.exploredChunks() + " chunks", contentLeft,
                    ly, palette.text());
            ly += ROW_H;
            context.drawTextWithShadow(textRenderer, "discoveries " + snapshot.discoveries(), contentLeft, ly,
                    palette.text());
            ly += ROW_H;
            context.drawTextWithShadow(textRenderer, "duration " + formatDuration(snapshot.durationMillis()),
                    contentLeft, ly, palette.text());
            ly += ROW_H;
            context.drawTextWithShadow(textRenderer, "agent " + intelligence.scanner().agent().visitedCount()
                    + " visited", contentLeft, ly, palette.text());

            int ry = y;
            context.drawTextWithShadow(textRenderer, "flight " + snapshot.flightStatus(), col2, ry, palette.text());
            ry += ROW_H;
            context.drawTextWithShadow(textRenderer, "movement " + snapshot.movementStatus(), col2, ry, palette.text());
            ry += ROW_H;
            context.drawTextWithShadow(textRenderer, "mode " + (intelligence.autonomous() ? "autonomous" : "in-place"),
                    col2, ry, palette.text());
            ry += ROW_H;
            context.drawTextWithShadow(textRenderer, "baritone " + intelligence.baritone().statusLine(), col2, ry,
                    palette.text());
            ry += ROW_H;
            context.drawTextWithShadow(textRenderer, "perf " + String.format("%.1f", snapshot.samplesPerSecond())
                    + " chunks/s \u00b7 " + (snapshot.averageTickNanos() / 1000) + " \u00b5s/tick", col2, ry,
                    palette.text());
            ry += ROW_H;
            context.drawTextWithShadow(textRenderer, "recorded " + intelligence.database().size() + " \u00b7 session "
                    + intelligence.observationsThisSession() + " (" + intelligence.newThisSession() + " new)", col2, ry,
                    palette.text());
        }

        controlY = contentBottom - 34;
        controlX = contentLeft;
        boolean running = module.isScanning();
        boolean paused = snapshot != null && snapshot.state() == ScanState.PAUSED;
        int bw = 62;
        int bx = controlX;
        drawButton(context, palette, bx, controlY, bw, "Start", !running, mouseX, mouseY);
        bx += bw + 6;
        drawButton(context, palette, bx, controlY, bw, "Pause", running && !paused, mouseX, mouseY);
        bx += bw + 6;
        drawButton(context, palette, bx, controlY, bw, "Resume", paused, mouseX, mouseY);
        bx += bw + 6;
        drawButton(context, palette, bx, controlY, bw, "Stop", running, mouseX, mouseY);
        bx += bw + 6;
        drawButton(context, palette, bx, controlY, 70, "Reset", true, mouseX, mouseY);

        renderRecent(context, palette, contentLeft, controlY - 118, contentRight, controlY - 12, intelligence);
    }

    private void renderRecent(DrawContext context, Palette palette, int left, int top, int right, int bottom,
                              WorldIntelligence intelligence) {
        context.drawTextWithShadow(textRenderer, "recent observations", left, top, palette.secondary());
        List<Observation> recent = intelligence.database().recent(8);
        if (recent.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "nothing recorded yet", left, top + 14, palette.textDim());
            return;
        }
        int y = top + 14;
        for (Observation observation : recent) {
            if (y + ROW_H > bottom) {
                break;
            }
            RenderUtil.roundedRect(context, left, y + 2, 8, 8, palette.radius(2),
                    categoryColor(palette, observation.category()));
            context.drawTextWithShadow(textRenderer, observation.summary() + "  " + observation.evidenceLine(),
                    left + 14, y, observation.discovered() ? palette.text() : palette.textDim());
            y += ROW_H;
        }
    }

    // ------------------------------------------------------------------ map

    private void renderMap(DrawContext context, Palette palette) {
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            context.drawTextWithShadow(textRenderer, "WorldScanner module is not enabled.", contentLeft, contentTop,
                    palette.textDim());
            return;
        }
        String dimension = intelligence.dimension();
        int panelW = contentRight - contentLeft;
        int panelH = contentBottom - contentTop;
        RenderUtil.roundedPanel(context, contentLeft, contentTop, panelW, panelH, palette.radius(6), palette.card(),
                palette.borderSoft());

        if (!mapCentred) {
            mapCentreChunkX = intelligence.scanner().agent().currentX() >> 4;
            mapCentreChunkZ = intelligence.scanner().agent().currentZ() >> 4;
            mapCentred = true;
        }
        int zoom = state.mapZoom();
        int centreX = contentLeft + panelW / 2;
        int centreY = contentTop + panelH / 2;
        int halfChunksX = panelW / (2 * zoom) + 1;
        int halfChunksZ = panelH / (2 * zoom) + 1;
        int drawn = 0;
        for (Observation observation : intelligence.database().forDimension(dimension)) {
            int dx = observation.chunkX() - mapCentreChunkX;
            int dz = observation.chunkZ() - mapCentreChunkZ;
            if (Math.abs(dx) > halfChunksX || Math.abs(dz) > halfChunksZ) {
                continue;
            }
            int px = centreX + dx * zoom - zoom / 2;
            int py = centreY + dz * zoom - zoom / 2;
            int color = categoryColor(palette, observation.category());
            if (!observation.discovered()) {
                color = ColorUtil.withAlpha(color, 90);
            }
            context.fill(px, py, px + Math.max(1, zoom - 1), py + Math.max(1, zoom - 1), color);
            drawn++;
        }
        RenderUtil.roundedRect(context, centreX - zoom / 2 - 1, centreY - zoom / 2 - 1, zoom + 2, zoom + 2,
                palette.radius(2), palette.primary());

        context.drawTextWithShadow(textRenderer, "map \u00b7 " + WorldQuery.shortDimension(dimension) + " \u00b7 "
                + drawn + " shown \u00b7 centre " + (mapCentreChunkX << 4) + "," + (mapCentreChunkZ << 4)
                + " \u00b7 zoom " + zoom + "px/chunk", contentLeft + 8, contentTop + 6, palette.text());

        int legendY = contentTop + 24;
        for (StructureCategory category : StructureCategory.values()) {
            if (legendY + ROW_H > contentBottom - 18) {
                break;
            }
            RenderUtil.roundedRect(context, contentLeft + 8, legendY + 2, 8, 8, palette.radius(2),
                    categoryColor(palette, category));
            context.drawTextWithShadow(textRenderer, category.label(), contentLeft + 20, legendY, palette.textDim());
            legendY += ROW_H;
        }
        context.drawTextWithShadow(textRenderer, "scroll to zoom", contentLeft + 8, contentBottom - 14,
                palette.textDim());
    }

    // ------------------------------------------------------------------ discoveries

    private void renderDiscoveries(DrawContext context, Palette palette, int mouseX, int mouseY) {
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            context.drawTextWithShadow(textRenderer, "WorldScanner module is not enabled.", contentLeft, contentTop,
                    palette.textDim());
            return;
        }
        DiscoveryDatabase database = intelligence.database();

        searchX = contentLeft;
        searchY = contentTop;
        searchW = 220;
        RenderUtil.roundedPanel(context, searchX, searchY, searchW, 16, palette.radius(4), palette.card(),
                palette.borderSoft());
        String searchText = state.search().isEmpty() ? "\u2315 search\u2026" : state.search();
        context.drawTextWithShadow(textRenderer, searchText, searchX + 6, searchY + 4,
                state.search().isEmpty() ? palette.textDim() : palette.text());

        filterX = searchX + searchW + 8;
        drawButton(context, palette, filterX, searchY, 96,
                "filter: " + (state.filter() == null ? "all" : state.filter().label()), true, mouseX, mouseY);
        sortX = filterX + 102;
        drawButton(context, palette, sortX, searchY, 96, "sort: " + state.sort().name().toLowerCase(), true, mouseX,
                mouseY);

        listTop = contentTop + 24;
        listBottom = contentBottom - 44;
        List<Observation> results = database.search(state.search(),
                state.filter() == null ? null : o -> o.category() == state.filter(), state.sort(),
                state.listScroll(), 9);
        int rowY = listTop;
        int index = 0;
        for (Observation observation : results) {
            if (rowY + ROW_H > listBottom) {
                break;
            }
            if (index == state.selectedIndex()) {
                RenderUtil.roundedRect(context, contentLeft - 2, rowY - 1, contentRight - contentLeft + 4, ROW_H,
                        palette.radius(2), ColorUtil.withAlpha(palette.primary(), 60));
            }
            RenderUtil.roundedRect(context, contentLeft, rowY + 2, 8, 8, palette.radius(2),
                    categoryColor(palette, observation.category()));
            String note = observation.note().isEmpty() ? "" : "  \u201c" + observation.note() + "\u201d";
            context.drawTextWithShadow(textRenderer,
                    observation.summary() + "  [" + observation.verification().label() + "]" + note, contentLeft + 14,
                    rowY, observation.discovered() ? palette.text() : palette.textDim());
            rowY += ROW_H;
            index++;
        }
        if (results.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "no matching observations", contentLeft, listTop, palette.textDim());
        }

        actionY = contentBottom - 40;
        Observation selected = state.selectedIndex() >= 0 && state.selectedIndex() < results.size()
                ? results.get(state.selectedIndex()) : null;
        if (state.annotateTargetIndex() >= 0) {
            context.drawTextWithShadow(textRenderer, "note: " + state.annotateBuffer() + "_", contentLeft,
                    actionY - 14, palette.primary());
        }
        drawButton(context, palette, contentLeft, actionY, 72, "Confirm", selected != null, mouseX, mouseY);
        drawButton(context, palette, contentLeft + 78, actionY, 72, "Dismiss", selected != null, mouseX, mouseY);
        drawButton(context, palette, contentLeft + 156, actionY, 72, "Annotate", selected != null, mouseX, mouseY);
        drawButton(context, palette, contentLeft + 234, actionY, 72, "Delete", selected != null, mouseX, mouseY);
        context.drawTextWithShadow(textRenderer, "shown " + results.size() + " \u00b7 scroll list with wheel",
                contentRight - 220, actionY + 4, palette.textDim());
    }

    // ------------------------------------------------------------------ AI

    private void renderAi(DrawContext context, Palette palette, int mouseX, int mouseY) {
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            context.drawTextWithShadow(textRenderer, "WorldScanner module is not enabled.", contentLeft, contentTop,
                    palette.textDim());
            return;
        }
        context.drawTextWithShadow(textRenderer, "backend " + intelligence.ai().backendDescription()
                + (intelligence.ai().modelAvailable() ? " \u00b7 model active" : " \u00b7 deterministic"),
                contentLeft, contentTop, palette.secondary());

        questionY = contentTop + 16;
        int boxW = contentRight - contentLeft - 92;
        RenderUtil.roundedPanel(context, contentLeft, questionY, boxW, 18, palette.radius(4), palette.card(),
                state.questionFocused() ? palette.primary() : palette.borderSoft());
        String shown = state.question().isEmpty() ? "\u2315 ask about your world\u2026" : state.question();
        context.drawTextWithShadow(textRenderer, shown, contentLeft + 6, questionY + 5,
                state.question().isEmpty() ? palette.textDim() : palette.text());
        askX = contentLeft + boxW + 6;
        askW = 86;
        drawButton(context, palette, askX, questionY, askW, "Ask", true, mouseX, mouseY);

        int hy = questionY + 26;
        List<WorldQuery.Answer> answers = state.answers();
        if (answers.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "try:", contentLeft, hy, palette.textDim());
            hy += ROW_H;
            for (String suggestion : WorldQuery.supportedQuestions()) {
                if (hy > contentBottom - 40) {
                    break;
                }
                context.drawTextWithShadow(textRenderer, "  \u2022 " + suggestion, contentLeft, hy, palette.textDim());
                hy += ROW_H;
            }
        } else {
            for (int i = answers.size() - 1; i >= 0 && hy < contentBottom - 40; i--) {
                WorldQuery.Answer answer = answers.get(i);
                context.drawTextWithShadow(textRenderer, "\u203a " + answer.question(), contentLeft, hy,
                        palette.secondary());
                hy += ROW_H;
                for (String line : wrap(answer.text(), contentRight - contentLeft - 8)) {
                    if (hy > contentBottom - 40) {
                        break;
                    }
                    context.drawTextWithShadow(textRenderer, line, contentLeft + 8, hy, palette.text());
                    hy += ROW_H;
                }
                hy += 2;
            }
        }
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textRenderer.getWidth(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    // ------------------------------------------------------------------ data

    private void renderData(DrawContext context, Palette palette, int mouseX, int mouseY) {
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            context.drawTextWithShadow(textRenderer, "WorldScanner module is not enabled.", contentLeft, contentTop,
                    palette.textDim());
            return;
        }
        DiscoveryDatabase database = intelligence.database();
        int y = contentTop;
        context.drawTextWithShadow(textRenderer, "file " + database.file(), contentLeft, y, palette.text());
        y += ROW_H;
        context.drawTextWithShadow(textRenderer, "observations " + database.size() + " \u00b7 route entries "
                + database.routeHistory().size() + " \u00b7 summaries " + database.summaries().size(), contentLeft, y,
                palette.text());
        y += ROW_H;
        context.drawTextWithShadow(textRenderer, "skipped on last load " + database.skipped() + " \u00b7 last save "
                + (intelligence.lastSaveAt() == 0 ? "never" : formatAgo(intelligence.lastSaveAt())), contentLeft, y,
                palette.text());
        y += ROW_H * 2;

        dataRowY = y;
        int bw = 92;
        int bx = contentLeft;
        drawButton(context, palette, bx, y, bw, "Save", true, mouseX, mouseY);
        bx += bw + 6;
        drawButton(context, palette, bx, y, bw, "Export", true, mouseX, mouseY);
        bx += bw + 6;
        drawButton(context, palette, bx, y, bw, "Import", true, mouseX, mouseY);
        bx += bw + 6;
        drawButton(context, palette, bx, y, bw, "Backup", true, mouseX, mouseY);

        dataRow2Y = y + BUTTON_H + 12;
        drawButton(context, palette, contentLeft, dataRow2Y, 132, "Delete dimension", true, mouseX, mouseY);
        drawButton(context, palette, contentLeft + 138, dataRow2Y, 132, "Delete older 24h", true, mouseX, mouseY);
        drawButton(context, palette, contentLeft + 276, dataRow2Y, 132, "Clear all", true, mouseX, mouseY);

        context.drawTextWithShadow(textRenderer, "export writes the JSON file above; import reloads it; backup writes "
                + "a timestamped copy beside it.", contentLeft, dataRow2Y + BUTTON_H + 8, palette.textDim());
    }

    private static String formatAgo(long at) {
        long seconds = Math.max(0, (System.currentTimeMillis() - at) / 1000);
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        return minutes < 60 ? minutes + "m ago" : (minutes / 60) + "h ago";
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int x = (int) click.x();
        int y = (int) click.y();
        if (y >= tabY && y <= tabY + 18) {
            IntelligenceDashboardState.Tab[] tabs = IntelligenceDashboardState.Tab.values();
            for (int i = 0; i < tabs.length; i++) {
                if (x >= tabX[i] && x <= tabX[i] + tabW[i]) {
                    state.selectTab(tabs[i]);
                    return true;
                }
            }
        }
        WorldIntelligence intelligence = intelligence();
        if (intelligence == null) {
            return super.mouseClicked(click, doubled);
        }
        boolean handled = switch (state.tab()) {
            case OVERVIEW -> clickOverview(x, y);
            case MAP -> false;
            case DISCOVERIES -> clickDiscoveries(intelligence, x, y);
            case AI -> clickAi(intelligence, x, y);
            case DATA -> clickData(intelligence, x, y);
        };
        return handled || super.mouseClicked(click, doubled);
    }

    private boolean clickOverview(int x, int y) {
        if (y < controlY || y > controlY + BUTTON_H || x < controlX) {
            return false;
        }
        int index = (x - controlX) / 68;
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
                return false;
            }
        }
        return true;
    }

    private boolean clickDiscoveries(WorldIntelligence intelligence, int x, int y) {
        if (y >= searchY && y <= searchY + 16) {
            if (x >= searchX && x <= searchX + searchW) {
                state.clearSearch();
                return true;
            }
            if (x >= filterX && x <= filterX + 96) {
                state.cycleFilter();
                return true;
            }
            if (x >= sortX && x <= sortX + 96) {
                state.cycleSort();
                return true;
            }
        }
        if (y >= listTop && y <= listBottom) {
            int index = (y - listTop) / ROW_H;
            if (index >= 0) {
                state.selectRow(index);
                return true;
            }
        }
        if (y >= actionY && y <= actionY + BUTTON_H) {
            List<Observation> results = intelligence.database().search(state.search(),
                    state.filter() == null ? null : o -> o.category() == state.filter(), state.sort(),
                    state.listScroll(), 9);
            if (state.selectedIndex() < 0 || state.selectedIndex() >= results.size()) {
                setStatus("select an observation first");
                return true;
            }
            Observation selected = results.get(state.selectedIndex());
            if (x >= contentLeft && x <= contentLeft + 72) {
                intelligence.database().setVerification(selected.id(), VerificationStatus.CONFIRMED);
                setStatus("confirmed " + selected.id());
            } else if (x >= contentLeft + 78 && x <= contentLeft + 150) {
                intelligence.database().setVerification(selected.id(), VerificationStatus.REJECTED);
                setStatus("dismissed " + selected.id());
            } else if (x >= contentLeft + 156 && x <= contentLeft + 228) {
                state.beginAnnotate(state.selectedIndex(), selected.note());
                setStatus("type a note, then press Enter");
                return true;
            } else if (x >= contentLeft + 234 && x <= contentLeft + 306) {
                intelligence.database().delete(selected.id());
                state.clearSelection();
                setStatus("deleted " + selected.id());
            } else {
                return false;
            }
            intelligence.save();
            return true;
        }
        return false;
    }

    private boolean clickAi(WorldIntelligence intelligence, int x, int y) {
        if (y >= questionY && y <= questionY + 18) {
            if (x >= askX && x <= askX + askW) {
                askQuestion(intelligence);
                return true;
            }
            if (x >= contentLeft && x < askX) {
                state.focusQuestion();
                return true;
            }
        }
        return false;
    }

    private void askQuestion(WorldIntelligence intelligence) {
        String asked = state.takeQuestion();
        if (asked.isBlank()) {
            setStatus("type a question first");
            return;
        }
        intelligence.ask(asked, state::addAnswer);
        setStatus("asked: " + asked);
    }

    private boolean clickData(WorldIntelligence intelligence, int x, int y) {
        if (y >= dataRowY && y <= dataRowY + BUTTON_H) {
            int index = (x - contentLeft) / 98;
            switch (index) {
                case 0 -> {
                    setStatus(intelligence.save() == null ? "saved" : "save failed");
                    return true;
                }
                case 1 -> {
                    setStatus(exportMap(intelligence));
                    return true;
                }
                case 2 -> {
                    setStatus(importMap(intelligence));
                    return true;
                }
                case 3 -> {
                    setStatus(backup(intelligence));
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        if (y >= dataRow2Y && y <= dataRow2Y + BUTTON_H) {
            if (x >= contentLeft && x <= contentLeft + 132) {
                int removed = intelligence.database().deleteDimension(intelligence.dimension());
                intelligence.save();
                setStatus("deleted " + removed + " observations in this dimension");
            } else if (x >= contentLeft + 138 && x <= contentLeft + 270) {
                int removed = intelligence.database().deleteOlderThan(System.currentTimeMillis() - 24L * 3600_000L);
                intelligence.save();
                setStatus("pruned " + removed + " old observations");
            } else if (x >= contentLeft + 276 && x <= contentLeft + 408) {
                intelligence.database().clear();
                intelligence.save();
                setStatus("cleared all observations");
            } else {
                return false;
            }
            return true;
        }
        return false;
    }

    private String exportMap(WorldIntelligence intelligence) {
        try {
            intelligence.database().save();
            return "exported to " + intelligence.database().file();
        } catch (Exception e) {
            return "export failed: " + e.getMessage();
        }
    }

    private String importMap(WorldIntelligence intelligence) {
        intelligence.database().load();
        return "imported from " + intelligence.database().file() + " (" + intelligence.database().size() + " records)";
    }

    private String backup(WorldIntelligence intelligence) {
        try {
            Path file = intelligence.database().file();
            intelligence.database().save();
            Path backup = file.resolveSibling(file.getFileName() + "." + System.currentTimeMillis() + ".bak");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return "backup written to " + backup.getFileName();
        } catch (Exception e) {
            return "backup failed: " + e.getMessage();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (state.tab() == IntelligenceDashboardState.Tab.MAP) {
            state.zoom(verticalAmount > 0 ? 1 : -1);
            return true;
        }
        if (state.tab() == IntelligenceDashboardState.Tab.DISCOVERIES) {
            state.scrollList(verticalAmount > 0 ? -2 : 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (state.blur()) {
                return true;
            }
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (state.questionFocused()) {
                WorldIntelligence intelligence = intelligence();
                if (intelligence != null) {
                    askQuestion(intelligence);
                }
                return true;
            }
            if (state.annotateTargetIndex() >= 0) {
                commitAnnotation();
                return true;
            }
        }
        if (input.key() == GLFW.GLFW_KEY_TAB) {
            state.nextTab();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (state.questionFocused()) {
                state.backspaceQuestion();
                return true;
            }
            if (state.annotateTargetIndex() >= 0) {
                state.backspaceAnnotation();
                return true;
            }
            if (state.tab() == IntelligenceDashboardState.Tab.DISCOVERIES) {
                state.backspaceSearch();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private void commitAnnotation() {
        WorldIntelligence intelligence = intelligence();
        String note = state.takeAnnotation();
        if (intelligence == null || note == null) {
            return;
        }
        int index = state.selectedIndex();
        List<Observation> results = intelligence.database().search(state.search(),
                state.filter() == null ? null : o -> o.category() == state.filter(), state.sort(),
                state.listScroll(), 9);
        if (index >= 0 && index < results.size()) {
            Observation target = results.get(index);
            intelligence.database().annotate(target.id(), note);
            intelligence.save();
            setStatus("annotated " + target.id());
        }
    }

    @Override
    public boolean charTyped(CharInput input) {
        String typed = input.asString();
        if (typed == null || typed.isEmpty()) {
            return super.charTyped(input);
        }
        if (state.questionFocused()) {
            state.appendQuestion(typed);
            return true;
        }
        if (state.annotateTargetIndex() >= 0) {
            state.appendAnnotation(typed);
            return true;
        }
        if (state.tab() == IntelligenceDashboardState.Tab.DISCOVERIES) {
            state.appendSearch(typed);
            return true;
        }
        return super.charTyped(input);
    }

    // ------------------------------------------------------------------ helpers

    private int categoryColor(Palette palette, StructureCategory category) {
        return switch (category) {
            case BUILDING, STORAGE, PLAYER_BUILD -> palette.primary();
            case FARM, VILLAGE -> palette.enabled();
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
}
