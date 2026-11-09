package com.logvex.crystal.gui;

import com.logvex.crystal.compat.Compat;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.CommandCenterService;
import com.logvex.crystal.commandcenter.CompatibilityVerdict;
import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceFinding;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.PerformanceSample;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.commandcenter.assistant.AssistantService;
import com.logvex.crystal.commandcenter.assistant.DeterministicDiagnostics;
import com.logvex.crystal.commandcenter.quick.QuickAction;
import com.logvex.crystal.commandcenter.quick.QuickActionEngine;
import com.logvex.crystal.commandcenter.quick.QuickActionType;
import com.logvex.crystal.commandcenter.quick.QuickTrigger;
import com.logvex.crystal.commandcenter.ui.CommandCenterDraw;
import com.logvex.crystal.commandcenter.ui.CommandCenterPreferences;
import com.logvex.crystal.commandcenter.ui.DashboardLayout;
import com.logvex.crystal.commandcenter.ui.DashboardWidget;
import com.logvex.crystal.commandcenter.ui.MetricSeries;
import com.logvex.crystal.commandcenter.ui.PerformanceHistory;
import com.logvex.crystal.commandcenter.ui.SearchEntry;
import com.logvex.crystal.commandcenter.ui.UnifiedSearchIndex;
import com.logvex.crystal.cosmetic.GuiSounds;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.Animator;
import com.logvex.crystal.util.BestEffort;
import com.logvex.crystal.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The Crystal Command Center: the single user-facing control surface over the Phase 8A
 * backend.
 *
 * <p>It is one themed {@link Screen} with a persistent sidebar of workspaces and a header
 * search box. The dashboard shows the rolled-up status as user-arrangeable cards; the other
 * workspaces expose the modules, the performance history, the Baritone integration, the
 * configuration, the crash intelligence, the notifications and the quick-action engine. The
 * AI assistant workspace works with or without a local model.
 *
 * <p>It is a pure view: every action delegates to a service that already exists
 * ({@link CommandCenterService}, the module manager, the profile service, the assistant, the
 * quick-action engine). Nothing here reaches into the world, and the only state it owns is
 * transient UI state (the open workspace, the search text, scroll offsets). Theme changes take
 * effect immediately because the palette is re-read every frame.
 */
public final class CommandCenterScreen extends Screen {

    private static final int SIDEBAR_WIDTH = 116;
    private static final int HEADER_HEIGHT = 46;
    private static final int FOOTER_HEIGHT = 18;
    private static final int MARGIN = 16;

    private final ThemeManager theme;
    private final CrystalClient client;

    private CommandCenterPreferences.Workspace workspace;
    private final Animator openAnim = new Animator(0f, 3.4f);

    private String searchText = "";
    private boolean searchFocused;
    private String status = "";
    private long statusAt;

    // Scroll offsets, one per scrollable list.
    private int dashboardScroll;
    private int moduleScroll;
    private int searchScroll;
    private int crashScroll;
    private int notificationScroll;
    private int quickScroll;
    private int configScroll;

    // Selection state.
    private Module selectedModule;
    private int selectedCrash = -1;
    private QuickAction selectedQuickAction;
    private boolean quickEditorOpen;
    private QuickAction quickDraft;
    private boolean dashboardEditorOpen;

    // Hit boxes captured during render.
    private final int[] navX = new int[CommandCenterPreferences.Workspace.values().length];
    private final int[] navY = new int[CommandCenterPreferences.Workspace.values().length];
    private final int[] navW = new int[CommandCenterPreferences.Workspace.values().length];
    private int searchBoxX;
    private int searchBoxY;
    private int searchBoxW;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;

    public CommandCenterScreen(CrystalClient client, CommandCenterPreferences.Workspace workspace) {
        super(Text.literal("Crystal Command Center"));
        this.client = client;
        this.theme = client.theme();
        this.workspace = workspace == null ? client.commandCenterPreferences().defaultWorkspace : workspace;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String message) {
        this.status = message == null ? "" : message;
        this.statusAt = System.currentTimeMillis();
    }

    private CommandCenterService service() {
        return client.commandCenter();
    }

    private CommandCenterPreferences preferences() {
        return client.commandCenterPreferences();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        Palette palette = theme.palette();
        float speed = theme.settings().reducedMotion ? 0f : theme.settings().animationSpeed;
        openAnim.setTarget(1f);
        float open = openAnim.update(com.logvex.crystal.util.FrameClock.deltaMillis(), speed);

        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, (int) (150 * open)));
        if (open > 0.01f && theme.settings().gradientIntensity > 0f) {
            RenderUtil.verticalGradient(context, 0, 0, width, height,
                    ColorUtil.scaleAlpha(palette.accentGradientStart(), 0.10f * open),
                    ColorUtil.scaleAlpha(palette.accentGradientEnd(), 0.14f * open));
        }
        if (theme.settings().blur && !theme.settings().reducedMotion) {
            try {
                context.applyBlur();
            } catch (Throwable t) {
                BestEffort.report("command-center-blur", "Command Center blur", t);
            }
        }

        float scale = uiScale();
        var matrices = context.getMatrices();
        Compat.matrixPush(matrices);
        Compat.matrixScale(matrices, scale);
        int vw = vw();
        int vh = vh();
        int vmx = (int) Math.round(mouseX / scale);
        int vmy = (int) Math.round(mouseY / scale);

        int animOffset = (int) ((1f - open) * 12);
        int left = MARGIN;
        int top = MARGIN + animOffset;
        int right = vw - MARGIN;
        int bottom = vh - MARGIN - animOffset;
        if (bottom <= top) {
            Compat.matrixPop(matrices);
            return;
        }

        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 120);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        drawHeader(context, palette, left, top, right, vmx, vmy);
        drawSidebar(context, palette, left, top + HEADER_HEIGHT, bottom, vmx, vmy);

        contentLeft = left + SIDEBAR_WIDTH + 8;
        contentRight = right - 8;
        contentTop = top + HEADER_HEIGHT + 6;
        contentBottom = bottom - FOOTER_HEIGHT - 4;

        if (searchFocused || !searchText.isBlank()) {
            renderSearchResults(context, palette, vmx, vmy);
        } else {
            switch (workspace) {
                case DASHBOARD -> renderDashboard(context, palette, vmx, vmy);
                case AI -> renderAi(context, palette, vmx, vmy);
                case MODULES -> renderModules(context, palette, vmx, vmy);
                case PERFORMANCE -> renderPerformance(context, palette, vmx, vmy);
                case BARITONE -> renderBaritone(context, palette, vmx, vmy);
                case CONFIG -> renderConfig(context, palette, vmx, vmy);
                case CRASHES -> renderCrashes(context, palette, vmx, vmy);
                case NOTIFICATIONS -> renderNotifications(context, palette, vmx, vmy);
                case QUICK_ACTIONS -> renderQuickActions(context, palette, vmx, vmy);
            }
        }
        drawFooter(context, palette, left, bottom);

        Compat.matrixPop(matrices);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void drawHeader(DrawContext context, Palette palette, int left, int top, int right,
                            int mouseX, int mouseY) {
        RenderUtil.horizontalGradient(context, left + 2, top + 2, right - left - 4, 2,
                palette.accentGradientStart(), palette.accentGradientEnd());
        context.drawTextWithShadow(textRenderer, "\u2661 Crystal Command Center", left + 12, top + 10,
                palette.primary());
        context.drawTextWithShadow(textRenderer, "status \u00B7 modules \u00B7 performance \u00B7 diagnostics",
                left + 12, top + 24, palette.textDim());

        int boxW = Math.min(240, (right - left) / 3);
        searchBoxX = right - 12 - boxW;
        searchBoxY = top + 12;
        searchBoxW = boxW;
        boolean hovered = inBox(mouseX, mouseY, searchBoxX, searchBoxY, searchBoxW, CommandCenterDraw.BUTTON_H);
        int border = searchFocused ? palette.primary() : hovered ? palette.border() : palette.borderSoft();
        RenderUtil.roundedPanel(context, searchBoxX, searchBoxY, searchBoxW, CommandCenterDraw.BUTTON_H,
                palette.radius(5), palette.card(), border);
        String text = searchText.isEmpty() && !searchFocused ? "\u2315 search everything\u2026" : "\u2315 " + searchText;
        int textColor = searchText.isEmpty() && !searchFocused ? palette.textDim() : palette.text();
        context.drawTextWithShadow(textRenderer, text, searchBoxX + 6, searchBoxY + 5, textColor);
        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = searchBoxX + 6 + textRenderer.getWidth("\u2315 " + searchText);
            context.fill(caretX, searchBoxY + 4, caretX + 1, searchBoxY + 14, palette.primary());
        }
    }

    private void drawSidebar(DrawContext context, Palette palette, int left, int top, int bottom,
                             int mouseX, int mouseY) {
        CommandCenterPreferences.Workspace[] spaces = CommandCenterPreferences.Workspace.values();
        int y = top + 8;
        for (int i = 0; i < spaces.length; i++) {
            String label = glyphFor(spaces[i]) + " " + spaces[i].label();
            int w = SIDEBAR_WIDTH - 12;
            navX[i] = left + 6;
            navY[i] = y;
            navW[i] = w;
            boolean active = workspace == spaces[i] && searchText.isBlank();
            boolean hovered = inBox(mouseX, mouseY, navX[i], navY[i], w, 18);
            int fill = active ? palette.panelHover() : hovered ? palette.card() : ColorUtil.withAlpha(0, 0);
            if (fill != 0) {
                RenderUtil.roundedPanel(context, navX[i], navY[i], w, 18, palette.radius(4), fill,
                        active ? palette.primary() : palette.borderSoft());
            }
            context.drawTextWithShadow(textRenderer, label, navX[i] + 6, navY[i] + 5,
                    active ? palette.primary() : palette.text());
            y += 20;
        }
    }

    private void drawFooter(DrawContext context, Palette palette, int left, int bottom) {
        String hint = "Esc closes \u00B7 click a card to act";
        context.drawTextWithShadow(textRenderer, hint, left + 12, bottom - FOOTER_HEIGHT + 2, palette.textDim());
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 5000) {
            context.drawTextWithShadow(textRenderer, status,
                    contentRight - textRenderer.getWidth(status), bottom - FOOTER_HEIGHT + 2, palette.primary());
        } else {
            String engine = service() == null ? "backend unavailable"
                    : service().actions().size() + " actions \u00B7 " + service().providers().size() + " providers";
            context.drawTextWithShadow(textRenderer, engine,
                    contentRight - textRenderer.getWidth(engine), bottom - FOOTER_HEIGHT + 2, palette.textDim());
        }
    }

    private static String glyphFor(CommandCenterPreferences.Workspace workspace) {
        return switch (workspace) {
            case DASHBOARD -> "\u25A6";
            case AI -> "\u2728";
            case MODULES -> "\u2726";
            case PERFORMANCE -> "\u26A1";
            case BARITONE -> "\u2748";
            case CONFIG -> "\u2699";
            case CRASHES -> "\u2620";
            case NOTIFICATIONS -> "\u2709";
            case QUICK_ACTIONS -> "\u2692";
        };
    }

    // ------------------------------------------------------------------ dashboard

    private void renderDashboard(DrawContext context, Palette palette, int mouseX, int mouseY) {
        CommandCenterService service = service();
        if (service == null) {
            context.drawTextWithShadow(textRenderer, "The Command Center backend is unavailable.",
                    contentLeft, contentTop, palette.danger());
            return;
        }
        ClientStatusModel model = service.statusModel();
        DashboardLayout layout = client.dashboardLayout();

        // Health banner, always drawn so the roll-up is visible even with every card hidden.
        int bannerH = 34;
        RenderUtil.roundedGradientPanel(context, contentLeft, contentTop, contentRight - contentLeft, bannerH,
                palette.radius(6), palette.card(), palette.panel(), palette.borderSoft());
        HealthStatus overall = model.overall();
        RenderUtil.dot(context, contentLeft + 14, contentTop + bannerH / 2, 4, overall.tint());
        context.drawTextWithShadow(textRenderer, model.summary(), contentLeft + 26, contentTop + 7,
                palette.text());
        context.drawTextWithShadow(textRenderer, "worst subsystem: " + worstSectionTitle(model),
                contentLeft + 26, contentTop + 19, palette.textDim());
        CommandCenterDraw.badge(context, textRenderer, palette,
                contentRight - 8 - textRenderer.getWidth(overall.label()) - 8, contentTop + 11,
                overall.label(), overall.tint());

        int y = contentTop + bannerH + 6;
        int gap = 6;
        int halfWidth = (contentRight - contentLeft - gap) / 2;
        int column = 0;
        int rowStartY = y;
        for (DashboardWidget widget : layout.visible()) {
            if (widget == DashboardWidget.HEALTH) {
                continue; // the banner is the health card
            }
            int cardWidth = widget.size() >= 2 ? (contentRight - contentLeft) : halfWidth;
            if (column == 1 && cardWidth >= (contentRight - contentLeft)) {
                // A full-width card cannot share a row; finish the current row first.
                y = rowStartY;
                column = 0;
            }
            int cardX = contentLeft + (column == 0 ? 0 : halfWidth + gap);
            int cardHeight = widgetCardHeight(widget, model, service);
            if (y + cardHeight > contentBottom + dashboardScroll) {
                // Below the fold; still draw when scrolled into view.
            }
            int drawY = y - dashboardScroll;
            if (drawY + cardHeight > contentTop + bannerH + 4 && drawY < contentBottom) {
                renderWidget(context, palette, widget, cardX, drawY, cardWidth, cardHeight, model, service);
            }
            if (cardWidth >= (contentRight - contentLeft)) {
                y += cardHeight + gap;
                column = 0;
            } else if (column == 0) {
                column = 1;
                rowStartY = y;
            } else {
                y += cardHeight + gap;
                column = 0;
            }
        }
        // Dashboard customisation buttons in the footer row.
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 96;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, bw,
                dashboardEditorOpen ? "Close layout" : "Arrange cards", true,
                inBox(mouseX, mouseY, contentLeft, by, bw, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + bw + 6, by, 80, "Refresh", true,
                inBox(mouseX, mouseY, contentLeft + bw + 6, by, 80, CommandCenterDraw.BUTTON_H), false);
        if (dashboardEditorOpen) {
            renderDashboardEditor(context, palette, contentLeft + 2 * (bw + 6), by);
        }
    }

    private int widgetCardHeight(DashboardWidget widget, ClientStatusModel model, CommandCenterService service) {
        StatusSection section = model.section(widget.sectionId());
        int lines = section == null ? 2 : Math.min(6, section.entries().size());
        return 26 + lines * 11;
    }

    private void renderWidget(DrawContext context, Palette palette, DashboardWidget widget, int x, int y,
                              int width, int height, ClientStatusModel model, CommandCenterService service) {
        if (widget == DashboardWidget.PERFORMANCE) {
            renderPerformanceWidget(context, palette, x, y, width, height, service);
            return;
        }
        StatusSection section = model.section(widget.sectionId());
        String title = widget.title();
        String subtitle = section == null ? "unavailable" : section.summary();
        RenderUtil.softShadow(context, x, y, width, height, palette.radius(6), 50);
        RenderUtil.roundedPanel(context, x, y, width, height, palette.radius(6), palette.card(),
                palette.borderSoft());
        context.drawTextWithShadow(textRenderer, title, x + 8, y + 6, palette.primary());
        if (section != null) {
            RenderUtil.dot(context, x + width - 12, y + 10, 3, section.worst().tint());
        }
        context.drawTextWithShadow(textRenderer, subtitle, x + 8, y + 17, palette.textDim());
        int lineY = y + 29;
        if (section != null) {
            for (StatusEntry entry : section.entries()) {
                if (lineY + 10 > y + height) {
                    break;
                }
                int color = entry.status().needsAttention() ? entry.status().tint() : palette.text();
                context.drawTextWithShadow(textRenderer, entry.label() + ": " + entry.value(), x + 8, lineY, color);
                lineY += 11;
            }
        }
    }

    private void renderPerformanceWidget(DrawContext context, Palette palette, int x, int y, int width,
                                         int height, CommandCenterService service) {
        RenderUtil.softShadow(context, x, y, width, height, palette.radius(6), 50);
        RenderUtil.roundedPanel(context, x, y, width, height, palette.radius(6), palette.card(),
                palette.borderSoft());
        context.drawTextWithShadow(textRenderer, "Performance", x + 8, y + 6, palette.primary());
        PerformanceReport report = service.performance();
        PerformanceSample sample = report.sample();
        int sparkW = Math.min(120, width / 3);
        PerformanceHistory history = client.performanceHistory();
        if (history != null) {
            CommandCenterDraw.sparkline(context, textRenderer, palette, x + width - sparkW - 8, y + 6,
                    sparkW, Math.max(24, height - 12), history.series(PerformanceHistory.FPS).snapshot(),
                    palette.primary());
        }
        int lineY = y + 20;
        if (sample.hasFrameData()) {
            context.drawTextWithShadow(textRenderer,
                    sample.fps() + " fps \u00B7 " + String.format("%.1f", sample.avgFrameMillis()) + "ms \u00B7 "
                            + sample.activeModules() + " modules", x + 8, lineY, palette.text());
            lineY += 11;
        } else {
            context.drawTextWithShadow(textRenderer, "no frame data yet", x + 8, lineY, palette.textDim());
            lineY += 11;
        }
        for (PerformanceFinding finding : report.findings()) {
            if (lineY + 10 > y + height) {
                break;
            }
            context.drawTextWithShadow(textRenderer, "\u2022 " + finding.observation(), x + 8, lineY,
                    finding.severity().tint());
            lineY += 11;
        }
    }

    private void renderDashboardEditor(DrawContext context, Palette palette, int x, int y) {
        DashboardLayout layout = client.dashboardLayout();
        int w = 150;
        int rowH = 13;
        int rows = DashboardWidget.values().length;
        int h = rows * rowH + 8;
        int panelY = y - h - 4;
        RenderUtil.roundedPanel(context, x, panelY, w, h, palette.radius(5), palette.panel(), palette.border());
        int ry = panelY + 4;
        for (DashboardWidget widget : DashboardWidget.values()) {
            boolean visible = layout.isVisible(widget);
            context.drawTextWithShadow(textRenderer, (visible ? "\u2661 " : "\u25CB ") + widget.title(),
                    x + 6, ry, visible ? palette.primary() : palette.textDim());
            ry += rowH;
        }
    }

    private static String worstSectionTitle(ClientStatusModel model) {
        StatusSection worst = null;
        for (StatusSection section : model.sections()) {
            if (worst == null || section.worst().ordinal() > worst.worst().ordinal()) {
                worst = section;
            }
        }
        return worst == null ? "none" : worst.title() + " (" + worst.worst().label() + ")";
    }

    // ------------------------------------------------------------------ AI assistant

    private void renderAi(DrawContext context, Palette palette, int mouseX, int mouseY) {
        AssistantService assistant = client.assistant();
        if (assistant == null) {
            context.drawTextWithShadow(textRenderer, "The assistant is unavailable.", contentLeft, contentTop,
                    palette.danger());
            return;
        }
        int y = contentTop;
        y = CommandCenterDraw.wrapped(context, textRenderer, contentLeft, y, contentRight - contentLeft,
                assistant.summary(), palette.text()) + 4;

        boolean modelReady = client.ai() != null && client.ai().isLoaded();
        CommandCenterDraw.badge(context, textRenderer, palette, contentLeft, y,
                modelReady ? "local model ready" : "deterministic mode", modelReady ? palette.enabled()
                        : palette.secondary());
        y += 16;

        context.drawTextWithShadow(textRenderer, "Ranked findings", contentLeft, y, palette.secondary());
        y += 12;
        List<DeterministicDiagnostics.Finding> findings = assistant.findings();
        if (findings.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "\u2661 nothing needs attention", contentLeft + 6, y,
                    palette.enabled());
            y += 12;
        }
        for (DeterministicDiagnostics.Finding finding : findings) {
            if (y + 22 > contentBottom - 40) {
                break;
            }
            RenderUtil.roundedPanel(context, contentLeft, y, contentRight - contentLeft, 20, palette.radius(3),
                    palette.card(), palette.borderSoft());
            context.drawTextWithShadow(textRenderer, "[" + finding.severity().label() + "] " + finding.section(),
                    contentLeft + 6, y + 5, finding.severity().tint());
            CommandCenterDraw.wrapped(context, textRenderer, contentLeft + 6, y + 5,
                    contentRight - contentLeft - 90, finding.problem(), palette.text());
            y += 22;
        }

        y += 4;
        context.drawTextWithShadow(textRenderer, "Assistant", contentLeft, y, palette.secondary());
        y += 12;
        String answer = assistant.pending() ? "thinking\u2026" : assistant.lastAnswer();
        if (!assistant.lastError().isBlank()) {
            answer = "error: " + assistant.lastError();
        }
        if (answer.isBlank()) {
            context.drawTextWithShadow(textRenderer, "ask a question, or read the findings above",
                    contentLeft, y, palette.textDim());
        } else {
            CommandCenterDraw.wrapped(context, textRenderer, contentLeft, y, contentRight - contentLeft,
                    answer, palette.text());
        }

        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 108;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, bw, "Diagnose", true,
                inBox(mouseX, mouseY, contentLeft, by, bw, CommandCenterDraw.BUTTON_H), true);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + bw + 6, by, bw,
                "Explain performance", true,
                inBox(mouseX, mouseY, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6), by, 80, "Clear", true,
                inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6), by, 80, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6) + 86, by, 96,
                "Open AI screen", true,
                inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6) + 86, by, 96, CommandCenterDraw.BUTTON_H), false);
    }

    // ------------------------------------------------------------------ modules

    private void renderModules(DrawContext context, Palette palette, int mouseX, int mouseY) {
        ModuleManager modules = client.modules();
        CommandCenterService service = service();
        int listWidth = Math.min(210, (contentRight - contentLeft) / 2);
        context.drawTextWithShadow(textRenderer, "Modules (" + modules.enabledCount() + "/" + modules.size()
                + " enabled)", contentLeft, contentTop, palette.secondary());
        int listTop = contentTop + 14;
        int listBottom = contentBottom - CommandCenterDraw.BUTTON_H - 6;
        List<Module> all = modules.all();
        int rowH = 14;
        int visibleRows = Math.max(1, (listBottom - listTop) / rowH);
        moduleScroll = Math.max(0, Math.min(moduleScroll, Math.max(0, all.size() - visibleRows)));
        int drawn = 0;
        for (int i = moduleScroll; i < all.size(); i++) {
            int y = listTop + drawn * rowH;
            if (y + rowH > listBottom) {
                break;
            }
            Module module = all.get(i);
            boolean selected = module == selectedModule;
            boolean hovered = inBox(mouseX, mouseY, contentLeft, y, listWidth, rowH);
            RenderUtil.roundedPanel(context, contentLeft, y, listWidth, rowH - 1, palette.radius(3),
                    selected ? palette.panelHover() : hovered ? palette.card() : palette.panel(),
                    selected ? palette.primary() : palette.borderSoft());
            RenderUtil.dot(context, contentLeft + 7, y + 6, 3,
                    module.isEnabled() ? palette.enabled() : palette.disabled());
            context.drawTextWithShadow(textRenderer, module.name(), contentLeft + 14, y + 3,
                    module.isEnabled() ? palette.text() : palette.textDim());
            if (service != null) {
                CompatibilityVerdict verdict = service.verdictFor(module.name());
                if (verdict != null && !verdict.operable()) {
                    context.drawTextWithShadow(textRenderer, "\u26A0",
                            contentLeft + listWidth - 12, y + 3, palette.danger());
                }
            }
            drawn++;
        }
        CommandCenterDraw.scrollbar(context, palette, contentLeft + listWidth + 1, listTop,
                listBottom - listTop, moduleScroll, all.size(), visibleRows);

        int detailX = contentLeft + listWidth + 12;
        if (selectedModule == null) {
            context.drawTextWithShadow(textRenderer, "select a module to see its details",
                    detailX, listTop, palette.textDim());
        } else {
            renderModuleDetail(context, palette, detailX, listTop, selectedModule);
        }

        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 84;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, bw, "Toggle", selectedModule != null,
                inBox(mouseX, mouseY, contentLeft, by, bw, CommandCenterDraw.BUTTON_H), true);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + bw + 6, by, bw, "Ask AI",
                selectedModule != null, inBox(mouseX, mouseY, contentLeft + bw + 6, by, bw,
                        CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6), by, 118,
                "Add quick action", selectedModule != null,
                inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6), by, 118, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6) + 124, by, 84,
                "Conflicts", service != null,
                inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6) + 124, by, 84, CommandCenterDraw.BUTTON_H), false);
    }

    private void renderModuleDetail(DrawContext context, Palette palette, int x, int top, Module module) {
        int y = top;
        context.drawTextWithShadow(textRenderer, module.name(), x, y, palette.primary());
        y += 12;
        context.drawTextWithShadow(textRenderer, module.category().icon() + " " + module.category().displayName(),
                x, y, palette.secondary());
        y += 12;
        y = CommandCenterDraw.wrapped(context, textRenderer, x, y, contentRight - x, module.description(),
                palette.textDim()) + 2;
        CommandCenterService service = service();
        if (service != null) {
            CompatibilityVerdict verdict = service.verdictFor(module.name());
            if (verdict != null) {
                y = CommandCenterDraw.wrapped(context, textRenderer, x, y, contentRight - x,
                        verdict.describe(), verdict.operable() ? palette.enabled() : palette.warning()) + 2;
                if (!verdict.requirements().isEmpty()) {
                    y = CommandCenterDraw.wrapped(context, textRenderer, x, y, contentRight - x,
                            "requires: " + String.join(", ", verdict.requirements()), palette.textDim()) + 2;
                }
            }
        }
        if (module.errorCount() > 0) {
            context.drawTextWithShadow(textRenderer, "errors: " + module.errorCount() + " \u2014 "
                    + module.lastError(), x, y, palette.danger());
            y += 12;
        }
        context.drawTextWithShadow(textRenderer, "settings", x, y, palette.secondary());
        y += 12;
        for (SettingGroup group : module.groups()) {
            if (y + 12 > contentBottom - 26) {
                break;
            }
            context.drawTextWithShadow(textRenderer, group.name(), x, y, palette.textDim());
            y += 11;
            for (Setting<?> setting : group.settings()) {
                if (y + 11 > contentBottom - 26) {
                    break;
                }
                context.drawTextWithShadow(textRenderer, "\u2022 " + setting.name(), x + 6, y, palette.text());
                String value = setting.displayValue();
                context.drawTextWithShadow(textRenderer, value,
                        contentRight - textRenderer.getWidth(value), y, palette.textDim());
                y += 11;
            }
        }
    }

    // ------------------------------------------------------------------ performance

    private void renderPerformance(DrawContext context, Palette palette, int mouseX, int mouseY) {
        CommandCenterService service = service();
        PerformanceHistory history = client.performanceHistory();
        int y = contentTop;
        if (service != null) {
            PerformanceReport report = service.performance();
            y = CommandCenterDraw.wrapped(context, textRenderer, contentLeft, y, contentRight - contentLeft,
                    report.summary(), report.clean() ? palette.enabled() : palette.warning()) + 4;
            for (PerformanceFinding finding : report.findings()) {
                y = CommandCenterDraw.wrapped(context, textRenderer, contentLeft, y, contentRight - contentLeft,
                        "[" + finding.severity().label() + "] " + finding.describe(), finding.severity().tint()) + 2;
            }
        }
        y += 4;
        context.drawTextWithShadow(textRenderer, "History", contentLeft, y, palette.secondary());
        y += 12;
        if (history == null) {
            context.drawTextWithShadow(textRenderer, "no history service", contentLeft, y, palette.textDim());
            return;
        }
        int chartWidth = (contentRight - contentLeft - 8) / 2;
        int chartHeight = 52;
        int cx = contentLeft;
        int cy = y;
        for (MetricSeries series : history.all()) {
            MetricSeries.Snapshot snapshot = series.snapshot();
            int tint = chartTint(series.id(), palette);
            CommandCenterDraw.sparkline(context, textRenderer, palette, cx, cy, chartWidth, chartHeight,
                    snapshot, tint);
            String label = series.label() + (snapshot.isEmpty() ? "" :
                    " \u00B7 " + String.format("%.1f", snapshot.latest()) + series.unit());
            context.drawTextWithShadow(textRenderer, label, cx + 4, cy - 10, palette.textDim());
            if (cx == contentLeft) {
                cx = contentLeft + chartWidth + 8;
            } else {
                cx = contentLeft;
                cy += chartHeight + 20;
            }
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, 96, "Clear history", true,
                inBox(mouseX, mouseY, contentLeft, by, 96, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 102, by, 96, "Sample now", true,
                inBox(mouseX, mouseY, contentLeft + 102, by, 96, CommandCenterDraw.BUTTON_H), false);
    }

    private int chartTint(String id, Palette palette) {
        return switch (id) {
            case PerformanceHistory.FPS -> palette.primary();
            case PerformanceHistory.FRAME_MS -> palette.secondary();
            case PerformanceHistory.MEMORY_MB -> palette.tertiary();
            case PerformanceHistory.TICK_MS -> palette.warning();
            case PerformanceHistory.AI_QUEUE -> palette.crystalDamage();
            default -> palette.enabled();
        };
    }

    // ------------------------------------------------------------------ baritone

    private void renderBaritone(DrawContext context, Palette palette, int mouseX, int mouseY) {
        var baritone = client.baritone();
        int y = contentTop;
        if (baritone == null) {
            context.drawTextWithShadow(textRenderer, "Baritone service unavailable", contentLeft, y, palette.danger());
            return;
        }
        var status = baritone.status();
        if (!status.available()) {
            y = CommandCenterDraw.wrapped(context, textRenderer, contentLeft, y, contentRight - contentLeft,
                    "Baritone is not detected. This integration is optional \u2014 install Baritone to enable it.",
                    palette.textDim()) + 4;
        } else {
            context.drawTextWithShadow(textRenderer, "version: " + status.version(), contentLeft, y, palette.text());
            y += 12;
            context.drawTextWithShadow(textRenderer, "state: " + status.statusText(), contentLeft, y, palette.text());
            y += 12;
            if (status.hasGoal()) {
                context.drawTextWithShadow(textRenderer, "goal: " + status.goalType() + " " + status.goalDetail(),
                        contentLeft, y, palette.text());
                y += 12;
            }
        }
        y += 4;
        context.drawTextWithShadow(textRenderer, "History", contentLeft, y, palette.secondary());
        y += 12;
        var entries = baritone.history().recent(10);
        if (entries.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "no transitions recorded", contentLeft, y, palette.textDim());
        }
        for (var entry : entries) {
            if (y + 12 > contentBottom - 26) {
                break;
            }
            context.drawTextWithShadow(textRenderer, entry.detail(), contentLeft, y, palette.text());
            String kind = entry.kind();
            context.drawTextWithShadow(textRenderer, kind, contentRight - textRenderer.getWidth(kind), y,
                    "error".equals(kind) ? palette.danger() : palette.textDim());
            y += 12;
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 88;
        boolean enabled = status.available();
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, bw, "Go to here", enabled,
                inBox(mouseX, mouseY, contentLeft, by, bw, CommandCenterDraw.BUTTON_H), true);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + bw + 6, by, bw, "Cancel", enabled,
                inBox(mouseX, mouseY, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6), by, 96,
                "Open screen", true,
                inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6), by, 96, CommandCenterDraw.BUTTON_H), false);
    }

    // ------------------------------------------------------------------ config

    private void renderConfig(DrawContext context, Palette palette, int mouseX, int mouseY) {
        int y = contentTop;
        context.drawTextWithShadow(textRenderer, "Profiles & workspaces", contentLeft, y, palette.secondary());
        y += 12;
        List<String> names = client.profiles().names();
        if (names.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "no saved profiles yet", contentLeft, y, palette.textDim());
            y += 12;
        }
        int rowH = 14;
        for (int i = 0; i < names.size(); i++) {
            int ry = y + i * rowH;
            if (ry + rowH > contentBottom - 60) {
                break;
            }
            String name = names.get(i);
            boolean hovered = inBox(mouseX, mouseY, contentLeft, ry, contentRight - contentLeft, rowH);
            RenderUtil.roundedPanel(context, contentLeft, ry, contentRight - contentLeft, rowH - 1,
                    palette.radius(3), hovered ? palette.panelHover() : palette.card(), palette.borderSoft());
            context.drawTextWithShadow(textRenderer, "\u2661 " + name, contentLeft + 6, ry + 3, palette.text());
            String diff = client.workspaces().diff(name).describe();
            context.drawTextWithShadow(textRenderer, diff, contentRight - 6 - textRenderer.getWidth(diff), ry + 3,
                    palette.textDim());
        }
        y += Math.min(names.size(), 6) * rowH + 8;
        context.drawTextWithShadow(textRenderer, "Appearance quick links", contentLeft, y, palette.secondary());
        y += 12;
        context.drawTextWithShadow(textRenderer, "theme: " + theme.settings().preset.label()
                + " \u00B7 " + theme.settings().variant.label(), contentLeft, y, palette.text());
        y += 12;
        context.drawTextWithShadow(textRenderer, "the Appearance tab has every slider and toggle",
                contentLeft, y, palette.textDim());

        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 92;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, bw, "Save config", true,
                inBox(mouseX, mouseY, contentLeft, by, bw, CommandCenterDraw.BUTTON_H), true);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + bw + 6, by, bw, "Reload config", true,
                inBox(mouseX, mouseY, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6), by, 108,
                "Open Appearance", true,
                inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6), by, 108, CommandCenterDraw.BUTTON_H), false);
    }

    // ------------------------------------------------------------------ crashes

    private void renderCrashes(DrawContext context, Palette palette, int mouseX, int mouseY) {
        List<com.logvex.crystal.ai.crash.CrashRecord> history = client.crashMonitor().history();
        int listWidth = Math.min(190, (contentRight - contentLeft) / 2);
        context.drawTextWithShadow(textRenderer, "Crashes (" + history.size() + ")", contentLeft, contentTop,
                palette.secondary());
        int listTop = contentTop + 14;
        int listBottom = contentBottom - CommandCenterDraw.BUTTON_H - 6;
        int rowH = 14;
        int visibleRows = Math.max(1, (listBottom - listTop) / rowH);
        crashScroll = Math.max(0, Math.min(crashScroll, Math.max(0, history.size() - visibleRows)));
        int drawn = 0;
        for (int i = history.size() - 1 - crashScroll; i >= 0; i--) {
            int ry = listTop + drawn * rowH;
            if (ry + rowH > listBottom) {
                break;
            }
            var record = history.get(i);
            boolean selected = i == selectedCrash;
            RenderUtil.roundedPanel(context, contentLeft, ry, listWidth, rowH - 1, palette.radius(3),
                    selected ? palette.panelHover() : palette.card(),
                    selected ? palette.primary() : palette.borderSoft());
            context.drawTextWithShadow(textRenderer, record.category().label(), contentLeft + 6, ry + 3,
                    selected ? palette.primary() : palette.text());
            String sev = record.severity().label();
            context.drawTextWithShadow(textRenderer, sev, contentLeft + listWidth - 6 - textRenderer.getWidth(sev),
                    ry + 3, record.severity().tint());
            drawn++;
        }
        int detailX = contentLeft + listWidth + 12;
        if (selectedCrash < 0 || selectedCrash >= history.size()) {
            context.drawTextWithShadow(textRenderer, "select a crash to see its analysis", detailX, listTop,
                    palette.textDim());
        } else {
            var record = history.get(selectedCrash);
            var diagnosis = client.crashAnalyzer().diagnose(record);
            int y = listTop;
            context.drawTextWithShadow(textRenderer, "cause (" + diagnosis.confidence().label() + ")",
                    detailX, y, palette.secondary());
            y += 12;
            y = CommandCenterDraw.wrapped(context, textRenderer, detailX, y, contentRight - detailX,
                    diagnosis.likelyCause(), palette.text()) + 2;
            context.drawTextWithShadow(textRenderer, "fixes", detailX, y, palette.secondary());
            y += 12;
            for (String fix : diagnosis.suggestedFixes()) {
                y = CommandCenterDraw.wrapped(context, textRenderer, detailX, y, contentRight - detailX,
                        "\u2022 " + fix, palette.text());
            }
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 96;
        boolean has = selectedCrash >= 0 && selectedCrash < history.size();
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, bw, "Analyse with AI",
                has && client.aiSettings().localAnalysis,
                inBox(mouseX, mouseY, contentLeft, by, bw, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + bw + 6, by, bw, "Queue report", has,
                inBox(mouseX, mouseY, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6), by, 84, "Delete", has,
                inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6), by, 84, CommandCenterDraw.BUTTON_H), false);
    }

    // ------------------------------------------------------------------ notifications

    private void renderNotifications(DrawContext context, Palette palette, int mouseX, int mouseY) {
        var notifications = client.notifications();
        int y = contentTop;
        var settings = notifications.settings();
        y = toggleRow(context, palette, contentLeft, y, "Sounds", settings.sound);
        y = toggleRow(context, palette, contentLeft, y, "Suppress duplicates", settings.suppressDuplicates);
        y = toggleRow(context, palette, contentLeft, y, "Cat cosmetics",
                theme.settings().catCosmetics);
        y = toggleRow(context, palette, contentLeft, y, "Command Center notices",
                preferences().notificationsEnabled);
        y += 4;
        context.drawTextWithShadow(textRenderer, "Recent history", contentLeft, y, palette.secondary());
        y += 12;
        List<com.logvex.crystal.notification.NotificationManager.Notification> history =
                notifications.history();
        int rowH = 12;
        int shown = 0;
        for (int i = history.size() - 1; i >= 0 && shown < 10; i--) {
            if (y + rowH > contentBottom - 26) {
                break;
            }
            var notification = history.get(i);
            context.drawTextWithShadow(textRenderer,
                    notification.level().glyph() + " " + notification.text(), contentLeft, y,
                    notification.level().fallbackColor());
            y += rowH;
            shown++;
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, 96, "Clear history", true,
                inBox(mouseX, mouseY, contentLeft, by, 96, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 102, by, 108, "Test notice", true,
                inBox(mouseX, mouseY, contentLeft + 102, by, 108, CommandCenterDraw.BUTTON_H), false);
    }

    private int toggleRow(DrawContext context, Palette palette, int x, int y, String label, boolean on) {
        boolean hovered = false; // hover feedback is drawn by the caller's click path; keep rows calm
        context.drawTextWithShadow(textRenderer, label, x, y, palette.text());
        int toggleX = x + 160;
        RenderUtil.roundedPanel(context, toggleX, y - 2, 26, 12, palette.radius(3),
                on ? palette.primary() : palette.card(), on ? palette.primary() : palette.borderSoft());
        RenderUtil.dot(context, toggleX + (on ? 19 : 7), y + 4, 4, on ? palette.onPrimary() : palette.disabled());
        return y + 14;
    }

    // ------------------------------------------------------------------ quick actions

    private void renderQuickActions(DrawContext context, Palette palette, int mouseX, int mouseY) {
        QuickActionEngine engine = client.quickActions();
        int y = contentTop;
        if (engine == null) {
            context.drawTextWithShadow(textRenderer, "The quick-action engine is unavailable.", contentLeft, y,
                    palette.danger());
            return;
        }
        context.drawTextWithShadow(textRenderer, engine.summary(), contentLeft, y, palette.secondary());
        y += 12;
        context.drawTextWithShadow(textRenderer,
                "Quick actions are opt-in and rate limited. Mutating actions confirm first.",
                contentLeft, y, palette.textDim());
        y += 14;

        int listBottom = contentBottom - CommandCenterDraw.BUTTON_H - 6;
        int rowH = 16;
        int visibleRows = Math.max(1, (listBottom - y) / rowH);
        List<QuickAction> actions = engine.actions();
        quickScroll = Math.max(0, Math.min(quickScroll, Math.max(0, actions.size() - visibleRows)));
        int drawn = 0;
        for (int i = quickScroll; i < actions.size(); i++) {
            int ry = y + drawn * rowH;
            if (ry + rowH > listBottom) {
                break;
            }
            QuickAction action = actions.get(i);
            boolean selected = action == selectedQuickAction;
            boolean hovered = inBox(mouseX, mouseY, contentLeft, ry, contentRight - contentLeft, rowH);
            RenderUtil.roundedPanel(context, contentLeft, ry, contentRight - contentLeft, rowH - 1,
                    palette.radius(3), selected ? palette.panelHover() : hovered ? palette.card() : palette.panel(),
                    selected ? palette.primary() : palette.borderSoft());
            RenderUtil.dot(context, contentLeft + 7, ry + 7, 3,
                    action.enabled() ? palette.enabled() : palette.disabled());
            context.drawTextWithShadow(textRenderer, action.describe(), contentLeft + 14, ry + 4,
                    action.enabled() ? palette.text() : palette.textDim());
            if (engine.awaitingConfirmation(action.id())) {
                String wait = "awaiting confirm";
                context.drawTextWithShadow(textRenderer, wait,
                        contentRight - 6 - textRenderer.getWidth(wait), ry + 4, palette.warning());
            }
            drawn++;
        }
        if (actions.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "no quick actions yet \u2014 create one below",
                    contentLeft, y, palette.textDim());
        }

        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 96;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft, by, bw, "New action", true,
                inBox(mouseX, mouseY, contentLeft, by, bw, CommandCenterDraw.BUTTON_H), true);
        boolean selected = selectedQuickAction != null;
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + bw + 6, by, bw,
                selected && selectedQuickAction.enabled() ? "Disable" : "Enable", selected,
                inBox(mouseX, mouseY, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6), by, 72, "Run",
                selected, inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6), by, 72, CommandCenterDraw.BUTTON_H), false);
        CommandCenterDraw.button(context, textRenderer, palette, contentLeft + 2 * (bw + 6) + 78, by, 72, "Delete",
                selected, inBox(mouseX, mouseY, contentLeft + 2 * (bw + 6) + 78, by, 72,
                        CommandCenterDraw.BUTTON_H), false);
        if (quickEditorOpen) {
            renderQuickEditor(context, palette, contentLeft + 2 * (bw + 6) + 156, by);
        }
    }

    private void renderQuickEditor(DrawContext context, Palette palette, int x, int y) {
        QuickAction draft = quickDraft == null ? new QuickAction("new", "New action") : quickDraft;
        int w = 240;
        int h = 128;
        int px = Math.min(x, contentRight - w);
        int py = Math.max(contentTop, y - h - 4);
        RenderUtil.softShadow(context, px, py, w, h, palette.radius(5), 80);
        RenderUtil.roundedPanel(context, px, py, w, h, palette.radius(5), palette.panel(), palette.border());
        context.drawTextWithShadow(textRenderer, "\u2661 Quick action", px + 8, py + 6, palette.primary());
        int ry = py + 20;
        context.drawTextWithShadow(textRenderer, "name: " + draft.name(), px + 8, ry, palette.text());
        ry += 12;
        context.drawTextWithShadow(textRenderer, "trigger: " + draft.trigger().label(), px + 8, ry, palette.text());
        ry += 12;
        context.drawTextWithShadow(textRenderer, "action: " + draft.type().label(), px + 8, ry, palette.text());
        ry += 12;
        context.drawTextWithShadow(textRenderer, "argument: " + (draft.argument().isBlank() ? "\u2014"
                : draft.argument()), px + 8, ry, palette.text());
        ry += 12;
        context.drawTextWithShadow(textRenderer, "cooldown: " + (draft.cooldownMillis() / 1000) + "s", px + 8, ry,
                palette.textDim());
        ry += 12;
        context.drawTextWithShadow(textRenderer, draft.runnable() ? "ready" : "needs an argument",
                px + 8, ry, draft.runnable() ? palette.enabled() : palette.warning());
        ry += 14;
        context.drawTextWithShadow(textRenderer, "Save", px + 8, ry, palette.primary());
        context.drawTextWithShadow(textRenderer, "Cancel", px + 60, ry, palette.textDim());
    }

    // ------------------------------------------------------------------ search

    private void renderSearchResults(DrawContext context, Palette palette, int mouseX, int mouseY) {
        UnifiedSearchIndex index = client.searchIndex();
        int y = contentTop;
        if (searchText.isBlank()) {
            context.drawTextWithShadow(textRenderer, "type to search modules, settings, commands, workspaces "
                    + "and actions", contentLeft, y, palette.textDim());
            return;
        }
        List<SearchEntry> results = index == null ? List.of() : index.search(searchText);
        context.drawTextWithShadow(textRenderer, results.size() + " result(s) for \"" + searchText + "\"",
                contentLeft, y, palette.secondary());
        y += 14;
        int rowH = 16;
        for (int i = searchScroll; i < results.size(); i++) {
            int ry = y + (i - searchScroll) * rowH;
            if (ry + rowH > contentBottom - 4) {
                break;
            }
            SearchEntry entry = results.get(i);
            boolean hovered = inBox(mouseX, mouseY, contentLeft, ry, contentRight - contentLeft, rowH);
            RenderUtil.roundedPanel(context, contentLeft, ry, contentRight - contentLeft, rowH - 1,
                    palette.radius(3), hovered ? palette.panelHover() : palette.card(), palette.borderSoft());
            context.drawTextWithShadow(textRenderer, entry.category().glyph() + " " + entry.title(),
                    contentLeft + 6, ry + 4, palette.text());
            String sub = entry.subtitle();
            context.drawTextWithShadow(textRenderer, sub, contentRight - 6 - textRenderer.getWidth(sub),
                    ry + 4, palette.textDim());
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int x = (int) click.x();
        int y = (int) click.y();
        float scale = uiScale();
        int vx = (int) Math.round(x / scale);
        int vy = (int) Math.round(y / scale);

        if (inBox(vx, vy, searchBoxX, searchBoxY, searchBoxW, CommandCenterDraw.BUTTON_H)) {
            searchFocused = true;
            searchScroll = 0;
            return true;
        }
        CommandCenterPreferences.Workspace[] spaces = CommandCenterPreferences.Workspace.values();
        for (int i = 0; i < spaces.length; i++) {
            if (inBox(vx, vy, navX[i], navY[i], navW[i], 18)) {
                workspace = spaces[i];
                searchFocused = false;
                searchText = "";
                if (client.theme().settings().guiSounds) {
                    GuiSounds.click();
                }
                return true;
            }
        }
        searchFocused = false;
        if (!searchText.isBlank()) {
            searchClick(vx, vy);
            return true;
        }
        switch (workspace) {
            case DASHBOARD -> dashboardClick(vx, vy);
            case AI -> aiClick(vx, vy);
            case MODULES -> modulesClick(vx, vy);
            case PERFORMANCE -> performanceClick(vx, vy);
            case BARITONE -> baritoneClick(vx, vy);
            case CONFIG -> configClick(vx, vy);
            case CRASHES -> crashesClick(vx, vy);
            case NOTIFICATIONS -> notificationsClick(vx, vy);
            case QUICK_ACTIONS -> quickClick(vx, vy);
        }
        return super.mouseClicked(click, doubled);
    }

    private void dashboardClick(int x, int y) {
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 96;
        if (inBox(x, y, contentLeft, by, bw, CommandCenterDraw.BUTTON_H)) {
            dashboardEditorOpen = !dashboardEditorOpen;
            return;
        }
        if (inBox(x, y, contentLeft + bw + 6, by, 80, CommandCenterDraw.BUTTON_H)) {
            service().snapshot();
            setStatus("Status refreshed");
            return;
        }
        if (dashboardEditorOpen) {
            int panelX = contentLeft + 2 * (bw + 6);
            int rows = DashboardWidget.values().length;
            int rowH = 13;
            int panelY = by - (rows * rowH + 8) - 4;
            if (inBox(x, y, panelX, panelY, 150, rows * rowH + 8)) {
                int index = (y - panelY - 4) / rowH;
                DashboardWidget[] widgets = DashboardWidget.values();
                if (index >= 0 && index < widgets.length) {
                    client.dashboardLayout().toggle(widgets[index]);
                    client.saveCommandCenter();
                    return;
                }
            }
        }
    }

    private void aiClick(int x, int y) {
        AssistantService assistant = client.assistant();
        if (assistant == null) {
            return;
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 108;
        if (inBox(x, y, contentLeft, by, bw, CommandCenterDraw.BUTTON_H)) {
            setStatus(assistant.ask().message());
            return;
        }
        if (inBox(x, y, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H)) {
            setStatus(assistant.askAboutPerformance().message());
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6), by, 80, CommandCenterDraw.BUTTON_H)) {
            assistant.clearAnswer();
            setStatus("Cleared");
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6) + 86, by, 96, CommandCenterDraw.BUTTON_H)) {
            openScreen(new AIAssistantScreen(theme, client));
        }
    }

    private void modulesClick(int x, int y) {
        ModuleManager modules = client.modules();
        int listWidth = Math.min(210, (contentRight - contentLeft) / 2);
        int listTop = contentTop + 14;
        int listBottom = contentBottom - CommandCenterDraw.BUTTON_H - 6;
        int rowH = 14;
        if (x >= contentLeft && x <= contentLeft + listWidth && y >= listTop && y <= listBottom) {
            int row = (y - listTop) / rowH + moduleScroll;
            List<Module> all = modules.all();
            if (row >= 0 && row < all.size()) {
                selectedModule = all.get(row);
                return;
            }
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 84;
        if (inBox(x, y, contentLeft, by, bw, CommandCenterDraw.BUTTON_H)) {
            if (selectedModule != null) {
                selectedModule.toggle(modules);
                setStatus(selectedModule.name() + (selectedModule.isEnabled() ? " enabled" : " disabled"));
            }
            return;
        }
        if (inBox(x, y, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H)) {
            if (selectedModule != null && client.assistant() != null) {
                setStatus(client.assistant().askAboutModule(selectedModule.name()).message());
            }
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6), by, 118, CommandCenterDraw.BUTTON_H)) {
            if (selectedModule != null) {
                QuickAction action = new QuickAction("qa-" + System.currentTimeMillis(),
                        selectedModule.name() + " helper");
                action.type(QuickActionType.TOGGLE_MODULE).argument(selectedModule.name());
                action.trigger(QuickTrigger.PROBLEM_APPEARS).requiresConfirmation(true);
                client.quickActions().upsert(action);
                client.saveCommandCenter();
                selectedQuickAction = action;
                setStatus("Added a quick action for " + selectedModule.name());
            }
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6) + 124, by, 84, CommandCenterDraw.BUTTON_H)) {
            client.commands().runFromHost("conflicts");
            setStatus("Conflicts printed to chat");
        }
    }

    private void performanceClick(int x, int y) {
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        if (inBox(x, y, contentLeft, by, 96, CommandCenterDraw.BUTTON_H)) {
            if (client.performanceHistory() != null) {
                client.performanceHistory().clear();
                setStatus("Cleared the performance history");
            }
            return;
        }
        if (inBox(x, y, contentLeft + 102, by, 96, CommandCenterDraw.BUTTON_H)) {
            client.samplePerformanceNow();
            setStatus("Sampled performance");
        }
    }

    private void baritoneClick(int x, int y) {
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 88;
        var baritone = client.baritone();
        if (baritone == null) {
            return;
        }
        if (inBox(x, y, contentLeft, by, bw, CommandCenterDraw.BUTTON_H)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) {
                setStatus("Not in a world");
            } else {
                var pos = mc.player.getBlockPos();
                String error = baritone.goTo(com.logvex.crystal.integration.baritone.BaritoneGoal.block(
                        pos.getX(), pos.getY(), pos.getZ()));
                setStatus(error == null ? "Pathing to your position" : error);
            }
            return;
        }
        if (inBox(x, y, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H)) {
            baritone.cancel();
            setStatus("Cancelled");
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6), by, 96, CommandCenterDraw.BUTTON_H)) {
            openScreen(new BaritoneScreen(theme, baritone));
        }
    }

    private void configClick(int x, int y) {
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 92;
        if (inBox(x, y, contentLeft, by, bw, CommandCenterDraw.BUTTON_H)) {
            client.saveAll();
            setStatus("Configuration saved");
            return;
        }
        if (inBox(x, y, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H)) {
            client.reloadAll();
            setStatus("Configuration reloaded");
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6), by, 108, CommandCenterDraw.BUTTON_H)) {
            client.clickGui().showAppearance();
        }
    }

    private void crashesClick(int x, int y) {
        List<com.logvex.crystal.ai.crash.CrashRecord> history = client.crashMonitor().history();
        int listWidth = Math.min(190, (contentRight - contentLeft) / 2);
        int listTop = contentTop + 14;
        int listBottom = contentBottom - CommandCenterDraw.BUTTON_H - 6;
        int rowH = 14;
        if (x >= contentLeft && x <= contentLeft + listWidth && y >= listTop && y <= listBottom) {
            int row = (y - listTop) / rowH + crashScroll;
            int index = history.size() - 1 - row;
            if (index >= 0 && index < history.size()) {
                selectedCrash = index;
                return;
            }
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 96;
        boolean has = selectedCrash >= 0 && selectedCrash < history.size();
        if (inBox(x, y, contentLeft, by, bw, CommandCenterDraw.BUTTON_H)) {
            if (has) {
                client.askAiAboutCrash(history.get(selectedCrash),
                        client.crashAnalyzer().diagnose(history.get(selectedCrash)));
                setStatus("Asking the local model\u2026");
            }
            return;
        }
        if (inBox(x, y, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H)) {
            if (has) {
                var record = history.get(selectedCrash);
                boolean queued = client.reportService().queueReport(record,
                        client.crashAnalyzer().diagnose(record)) != null;
                setStatus(queued ? "Queued a report" : "A report is already queued");
            }
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6), by, 84, CommandCenterDraw.BUTTON_H)) {
            if (has) {
                client.crashMonitor().remove(history.get(selectedCrash).id());
                selectedCrash = -1;
                setStatus("Deleted the crash record");
            }
        }
    }

    private void notificationsClick(int x, int y) {
        var settings = client.notifications().settings();
        int y0 = contentTop;
        int[] rows = {y0, y0 + 14, y0 + 28, y0 + 42};
        for (int i = 0; i < rows.length; i++) {
            if (inBox(x, y, contentLeft, rows[i] - 2, 186, 14)) {
                switch (i) {
                    case 0 -> settings.sound = !settings.sound;
                    case 1 -> settings.suppressDuplicates = !settings.suppressDuplicates;
                    case 2 -> {
                        theme.settings().catCosmetics = !theme.settings().catCosmetics;
                        theme.update();
                    }
                    case 3 -> {
                        preferences().notificationsEnabled = !preferences().notificationsEnabled;
                        client.saveCommandCenter();
                    }
                    default -> {
                    }
                }
                return;
            }
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        if (inBox(x, y, contentLeft, by, 96, CommandCenterDraw.BUTTON_H)) {
            client.notifications().clearHistory();
            setStatus("Cleared notification history");
            return;
        }
        if (inBox(x, y, contentLeft + 102, by, 108, CommandCenterDraw.BUTTON_H)) {
            client.notifications().cute("Command Center test notice \u2661");
            setStatus("Posted a test notice");
        }
    }

    private void quickClick(int x, int y) {
        QuickActionEngine engine = client.quickActions();
        if (engine == null) {
            return;
        }
        int listTop = contentTop + 26;
        int listBottom = contentBottom - CommandCenterDraw.BUTTON_H - 6;
        int rowH = 16;
        List<QuickAction> actions = engine.actions();
        if (x >= contentLeft && x <= contentRight && y >= listTop && y <= listBottom) {
            int row = (y - listTop) / rowH + quickScroll;
            if (row >= 0 && row < actions.size()) {
                selectedQuickAction = actions.get(row);
                return;
            }
        }
        int by = contentBottom - CommandCenterDraw.BUTTON_H;
        int bw = 96;
        if (inBox(x, y, contentLeft, by, bw, CommandCenterDraw.BUTTON_H)) {
            quickDraft = new QuickAction("qa-" + System.currentTimeMillis(), "New action");
            quickEditorOpen = true;
            setStatus("Configure the new action, then Save");
            return;
        }
        if (inBox(x, y, contentLeft + bw + 6, by, bw, CommandCenterDraw.BUTTON_H)) {
            if (selectedQuickAction != null) {
                selectedQuickAction.enabled(!selectedQuickAction.enabled());
                engine.upsert(selectedQuickAction);
                client.saveCommandCenter();
                setStatus(selectedQuickAction.name() + (selectedQuickAction.enabled() ? " enabled" : " disabled"));
            }
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6), by, 72, CommandCenterDraw.BUTTON_H)) {
            if (selectedQuickAction != null) {
                engine.runManually(selectedQuickAction.id(), System.currentTimeMillis());
                setStatus("Ran " + selectedQuickAction.name());
            }
            return;
        }
        if (inBox(x, y, contentLeft + 2 * (bw + 6) + 78, by, 72, CommandCenterDraw.BUTTON_H)) {
            if (selectedQuickAction != null) {
                engine.remove(selectedQuickAction.id());
                client.saveCommandCenter();
                selectedQuickAction = null;
                setStatus("Deleted the quick action");
            }
            return;
        }
        if (quickEditorOpen) {
            int w = 240;
            int px = Math.min(contentLeft + 2 * (bw + 6) + 156, contentRight - w);
            int py = Math.max(contentTop, by - 128 - 4);
            if (inBox(x, y, px + 8, py + 104, 44, 12)) {
                if (quickDraft != null) {
                    quickDraft.enabled(true);
                    engine.upsert(quickDraft);
                    client.saveCommandCenter();
                    selectedQuickAction = quickDraft;
                    setStatus("Saved the quick action");
                }
                quickEditorOpen = false;
                return;
            }
            if (inBox(x, y, px + 60, py + 104, 52, 12)) {
                quickEditorOpen = false;
                quickDraft = null;
                return;
            }
            // Cycle the editor fields on click so a workflow can be built without a keyboard.
            if (inBox(x, y, px, py, w, 128) && quickDraft != null) {
                cycleQuickDraft(quickDraft, (y - py - 18) / 12);
            }
        }
    }

    private void cycleQuickDraft(QuickAction draft, int field) {
        switch (field) {
            case 1 -> draft.trigger(QuickTrigger.values()[(draft.trigger().ordinal() + 1)
                    % QuickTrigger.values().length]);
            case 2 -> draft.type(QuickActionType.values()[(draft.type().ordinal() + 1)
                    % QuickActionType.values().length]);
            case 3 -> {
                if (draft.type() == QuickActionType.TOGGLE_MODULE
                        || draft.type() == QuickActionType.ENABLE_MODULE
                        || draft.type() == QuickActionType.DISABLE_MODULE) {
                    List<Module> all = client.modules().all();
                    if (!all.isEmpty()) {
                        int index = 0;
                        for (int i = 0; i < all.size(); i++) {
                            if (all.get(i).name().equalsIgnoreCase(draft.argument())) {
                                index = i + 1;
                                break;
                            }
                        }
                        draft.argument(all.get(index % all.size()).name());
                    }
                }
            }
            default -> {
            }
        }
    }

    private void searchClick(int x, int y) {
        UnifiedSearchIndex index = client.searchIndex();
        if (index == null) {
            return;
        }
        List<SearchEntry> results = index.search(searchText);
        int rowH = 16;
        int listTop = contentTop + 14;
        if (x >= contentLeft && x <= contentRight && y >= listTop) {
            int row = (y - listTop) / rowH + searchScroll;
            if (row >= 0 && row < results.size()) {
                activateSearchResult(results.get(row));
            }
        }
    }

    private void activateSearchResult(SearchEntry entry) {
        switch (entry.category()) {
            case MODULE -> {
                workspace = CommandCenterPreferences.Workspace.MODULES;
                selectedModule = client.modules().byName(entry.title());
            }
            case WORKSPACE -> workspace = CommandCenterPreferences.Workspace.parse(entry.target());
            case COMMAND -> {
                client.commands().runFromHost(entry.target());
                setStatus("Ran " + entry.target());
            }
            case ACTION -> {
                var result = service().run(entry.actionId());
                setStatus(result.message());
            }
            case PROFILE -> {
                boolean ok = client.profiles().apply(entry.title());
                setStatus(ok ? "Applied profile " + entry.title() : "Could not apply the profile");
            }
            case SETTING, QUICK_ACTION -> {
                workspace = CommandCenterPreferences.Workspace.MODULES;
                selectedModule = client.modules().byName(entry.target());
            }
        }
        searchFocused = false;
        searchText = "";
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (searchFocused || !searchText.isEmpty()) {
                searchFocused = false;
                searchText = "";
                return true;
            }
            if (quickEditorOpen) {
                quickEditorOpen = false;
                return true;
            }
            close();
            return true;
        }
        if (searchFocused) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                }
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                List<SearchEntry> results = client.searchIndex() == null ? List.of()
                        : client.searchIndex().search(searchText);
                if (!results.isEmpty()) {
                    activateSearchResult(results.get(0));
                }
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_DOWN) {
                searchScroll++;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_UP) {
                searchScroll = Math.max(0, searchScroll - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchFocused) {
            String typed = input.asString();
            if (!typed.isEmpty() && typed.charAt(0) >= ' ') {
                searchText += typed;
                searchScroll = 0;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int delta = (int) Math.signum(verticalAmount);
        switch (workspace) {
            case DASHBOARD -> dashboardScroll = Math.max(0, dashboardScroll - delta * 12);
            case MODULES -> moduleScroll = Math.max(0, moduleScroll - delta);
            case CRASHES -> crashScroll = Math.max(0, crashScroll - delta);
            case NOTIFICATIONS -> notificationScroll = Math.max(0, notificationScroll - delta);
            case QUICK_ACTIONS -> quickScroll = Math.max(0, quickScroll - delta);
            case CONFIG -> configScroll = Math.max(0, configScroll - delta);
            default -> {
            }
        }
        if (searchFocused || !searchText.isBlank()) {
            searchScroll = Math.max(0, searchScroll - delta);
        }
        return true;
    }

    @Override
    public void close() {
        if (client.theme().settings().guiSounds) {
            GuiSounds.close();
        }
        super.close();
    }

    private void openScreen(Screen screen) {
        if (ScreenGuard.fontsReady()) {
            MinecraftClient.getInstance().setScreen(screen);
        }
    }

    // ------------------------------------------------------------------ helpers

    private float uiScale() {
        float s = theme.settings().uiScale;
        return s <= 0.1f ? 1f : s;
    }

    private int vw() {
        return Math.round(width / uiScale());
    }

    private int vh() {
        return Math.round(height / uiScale());
    }

    private static boolean inBox(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
