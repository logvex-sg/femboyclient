package com.logvex.crystal.gui;

import com.logvex.crystal.automation.AutomationModule;
import com.logvex.crystal.automation.AutomationService;
import com.logvex.crystal.automation.task.TaskQueue;
import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.setting.Setting;
import com.logvex.crystal.setting.SettingGroup;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The dedicated Automation panel: a live view of the automation hub and every automation module.
 *
 * <p>It is the one place that shows the whole subsystem at once - each module's enable state and
 * its own status line, the task currently running with a progress bar, the queue behind it, the
 * recent task history with reasons, and the module errors that explain a failure. The global
 * controls (pause, resume, emergency stop, clear history) drive the real
 * {@link AutomationService}, so what the panel shows is exactly what the client is doing.
 *
 * <p>Every row is a real module; clicking one toggles it, and right-clicking expands its settings
 * inline, so no part of this screen is decorative.
 */
public final class AutomationPanelScreen extends Screen {

    private static final int ROW_H = 14;
    private static final int BUTTON_H = 16;
    private static final int SETTING_ROW_H = 12;

    private final ThemeManager theme;
    private final ModuleManager modules;
    private final AutomationService hub;

    private double scroll;
    private int maxScroll;
    private Module expanded;
    private String status = "";
    private long statusAt;

    // Hit boxes captured during render so a click can never land on a stale layout.
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int buttonY;

    public AutomationPanelScreen(ThemeManager theme, ModuleManager modules, AutomationService hub) {
        super(Text.literal("Automation"));
        this.theme = theme;
        this.modules = modules;
        this.hub = hub;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String message) {
        this.status = message == null ? "" : message;
        this.statusAt = System.currentTimeMillis();
    }

    private List<Module> automationModules() {
        List<Module> result = new ArrayList<>();
        if (modules == null) {
            return result;
        }
        for (Module module : modules.all()) {
            if (module.category() == Category.AUTOMATION) {
                result.add(module);
            }
        }
        return result;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        Palette palette = theme.palette();
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, 170));

        int left = 24;
        int top = 20;
        int right = width - 24;
        int bottom = height - 20;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        context.drawTextWithShadow(textRenderer, "\u2699 Automation", left + 14, top + 8, palette.primary());
        context.drawTextWithShadow(textRenderer,
                "click a module to toggle \u00b7 right-click for settings \u00b7 Esc closes",
                left + 14, top + 20, palette.textDim());

        renderHub(context, palette, left + 14, top + 38, right - 14);

        buttonY = bottom - 28;
        int bx = left + 14;
        boolean paused = hub != null && hub.isPaused();
        boolean stopped = hub != null && hub.emergencyStopped();
        drawButton(context, palette, bx, buttonY, 58, "Pause", !paused && !stopped, mouseX, mouseY);
        bx += 64;
        drawButton(context, palette, bx, buttonY, 58, "Resume", paused || stopped, mouseX, mouseY);
        bx += 64;
        drawButton(context, palette, bx, buttonY, 70, "E-Stop", hub != null, mouseX, mouseY);
        bx += 76;
        drawButton(context, palette, bx, buttonY, 84, "ClearHist", hub != null, mouseX, mouseY);

        listLeft = left + 14;
        listRight = right - 14;
        listTop = top + 92;
        listBottom = buttonY - 8;
        renderModules(context, palette, mouseX, mouseY);

        String footer = !status.isEmpty() && System.currentTimeMillis() - statusAt < 4000
                ? status
                : (hub == null ? "hub unavailable" : hub.summary());
        context.drawTextWithShadow(textRenderer, footer, left + 14, bottom - 14,
                !status.isEmpty() ? palette.primary() : palette.textDim());

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderHub(DrawContext context, Palette palette, int x, int y, int right) {
        if (hub == null) {
            context.drawTextWithShadow(textRenderer, "automation hub not initialised", x, y, palette.textDim());
            return;
        }
        int midX = x + (right - x) / 2;
        context.drawTextWithShadow(textRenderer,
                (hub.isPaused() ? "\u23F8 paused" : "\u25B6 running")
                        + (hub.emergencyStopped() ? " \u00b7 EMERGENCY STOP" : ""),
                x, y, hub.isPaused() || hub.emergencyStopped() ? palette.warning() : palette.enabled());
        context.drawTextWithShadow(textRenderer,
                "tasks " + hub.queue().activeCount() + " active \u00b7 inventory "
                        + (hub.transactions().busy() ? "busy" : "idle"),
                midX, y, palette.text());

        var current = hub.queue().current();
        int rowY = y + 12;
        if (current.isPresent()) {
            TaskQueue.Entry entry = current.get();
            context.drawTextWithShadow(textRenderer, "current: " + entry.name() + " [" + entry.state().name()
                    + "] " + entry.progressDone() + "/" + entry.progressTotal(), x, rowY, palette.text());
            if (!entry.note().isEmpty()) {
                context.drawTextWithShadow(textRenderer, entry.note(), midX, rowY, palette.textDim());
            }
            int barY = rowY + 12;
            int barW = right - x;
            RenderUtil.roundedPanel(context, x, barY, barW, 6, palette.radius(3), palette.card(),
                    palette.borderSoft());
            float fraction = entry.progressFraction();
            if (fraction < 0) {
                fraction = 0;
            }
            int filled = (int) (barW * fraction);
            if (filled > 0) {
                RenderUtil.roundedRect(context, x, barY, filled, 6, palette.radius(3), palette.primary());
            }
        } else {
            context.drawTextWithShadow(textRenderer, "no task running", x, rowY, palette.textDim());
        }

        var transaction = hub.transactions().running();
        if (transaction.isPresent()) {
            context.drawTextWithShadow(textRenderer,
                    "inventory: " + transaction.get().transaction().description()
                            + " \u00b7 " + transaction.get().currentStep(),
                    x, y + 34, palette.secondary());
        }
    }

    private void renderModules(DrawContext context, Palette palette, int mouseX, int mouseY) {
        List<Module> rows = automationModules();
        context.enableScissor(listLeft, listTop, listRight, listBottom);
        int y = listTop - (int) scroll;
        for (Module module : rows) {
            if (y + ROW_H > listTop - ROW_H && y < listBottom) {
                drawModuleRow(context, palette, module, listLeft, y, listRight, mouseX, mouseY);
            }
            y += ROW_H;
            if (module == expanded) {
                for (SettingGroup group : module.groups()) {
                    for (Setting<?> setting : group.settings()) {
                        if (y + SETTING_ROW_H > listTop - SETTING_ROW_H && y < listBottom) {
                            String text = "    " + setting.name() + ": " + setting.displayValue();
                            context.drawTextWithShadow(textRenderer, text, listLeft + 24, y + 1,
                                    palette.textDim());
                        }
                        y += SETTING_ROW_H;
                    }
                }
            }
        }
        int contentHeight = y - (listTop - (int) scroll);
        maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        context.disableScissor();
    }

    private void drawModuleRow(DrawContext context, Palette palette, Module module, int x, int y,
                               int right, int mouseX, int mouseY) {
        int w = right - x;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_H - 1;
        RenderUtil.roundedPanel(context, x, y, w, ROW_H - 1, palette.radius(3),
                hovered ? palette.panelHover() : palette.card(), palette.borderSoft());
        int dot = module.isEnabled() ? palette.enabled() : palette.disabled();
        RenderUtil.roundedRect(context, x + 4, y + 4, 6, 6, palette.radius(2), dot);
        context.drawTextWithShadow(textRenderer, module.name(), x + 14, y + 3,
                module.isEnabled() ? palette.text() : palette.textDim());
        String statusText = module instanceof AutomationModule automationModule
                ? automationModule.statusLine() : "";
        if (!statusText.isEmpty()) {
            context.drawTextWithShadow(textRenderer, statusText, x + 130, y + 3, palette.secondary());
        }
        if (module.lastError() != null) {
            String error = module.lastError();
            String shortError = error.length() > 40 ? error.substring(0, 40) + "\u2026" : error;
            context.drawTextWithShadow(textRenderer, "\u26A0 " + shortError,
                    right - 10 - textRenderer.getWidth("\u26A0 " + shortError), y + 3, palette.danger());
        }
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

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Buttons.
        if (hub != null && click.y() >= buttonY && click.y() <= buttonY + BUTTON_H) {
            int bx = 24 + 14;
            if (inButton(click.x(), bx, 58)) {
                hub.setPaused(true);
                setStatus("Automation paused");
                return true;
            }
            bx += 64;
            if (inButton(click.x(), bx, 58)) {
                if (hub.emergencyStopped()) {
                    hub.clearEmergencyStop();
                }
                hub.setPaused(false);
                setStatus("Automation resumed");
                return true;
            }
            bx += 64;
            if (inButton(click.x(), bx, 70)) {
                int cancelled = hub.emergencyStop();
                setStatus("Emergency stop: " + cancelled + " cancelled");
                return true;
            }
            bx += 76;
            if (inButton(click.x(), bx, 84)) {
                hub.queue().clearHistory();
                hub.transactions().clearHistory();
                setStatus("History cleared");
                return true;
            }
        }
        // Module rows.
        if (click.y() >= listTop && click.y() <= listBottom
                && click.x() >= listLeft && click.x() <= listRight) {
            Module module = moduleAt(click.y());
            if (module != null) {
                if (click.button() == 1) {
                    expanded = expanded == module ? null : module;
                } else if (modules != null) {
                    module.toggle(modules);
                    setStatus(module.name() + (module.isEnabled() ? " enabled" : " disabled"));
                }
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private boolean inButton(double x, int bx, int w) {
        return x >= bx && x <= bx + w;
    }

    /** The module row a y coordinate falls on, accounting for the expanded settings block. */
    private Module moduleAt(double mouseY) {
        int y = listTop - (int) scroll;
        for (Module module : automationModules()) {
            if (mouseY >= y && mouseY <= y + ROW_H - 1) {
                return module;
            }
            y += ROW_H;
            if (module == expanded) {
                y += module.groups().stream()
                        .mapToInt(group -> group.settings().size() * SETTING_ROW_H).sum();
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (expanded != null) {
                expanded = null;
                return true;
            }
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return super.charTyped(input);
    }

    /** The module list as a lowercase text form, for a command or the debug overlay. */
    public static String summary(ModuleManager modules) {
        if (modules == null) {
            return "no modules";
        }
        int enabled = 0;
        int total = 0;
        for (Module module : modules.all()) {
            if (module.category() == Category.AUTOMATION) {
                total++;
                if (module.isEnabled()) {
                    enabled++;
                }
            }
        }
        return String.format(Locale.ROOT, "%d/%d automation modules enabled", enabled, total);
    }
}
