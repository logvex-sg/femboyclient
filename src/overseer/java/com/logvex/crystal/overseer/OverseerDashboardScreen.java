package com.logvex.crystal.overseer;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.remote.controller.InstanceRecord;
import com.logvex.crystal.remote.controller.RemoteController;
import com.logvex.crystal.release.FleetUpdateModel;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The Overseer fleet dashboard: the paired instances, their Minecraft/client versions and status,
 * and a per-instance action bar. It is a read-mostly view over the live {@link RemoteController};
 * it never keeps its own copy of instance state, so it can never show a stale fleet.
 *
 * <p>Selecting an instance and choosing an action dispatches through the controller, which owns
 * the authentication, capability checks and acknowledgement tracking. The dashboard only decides
 * what to ask for.
 */
public final class OverseerDashboardScreen extends Screen {

    private static final int ROW_HEIGHT = 22;

    private final OverseerFeature feature;

    private double scroll;
    private int maxScroll;
    private String selected = "";
    private String status = "";
    private long statusAt;

    public OverseerDashboardScreen(OverseerFeature feature) {
        super(Text.literal("Overseer"));
        this.feature = feature;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void setStatus(String message) {
        this.status = message;
        this.statusAt = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        if (textRenderer == null) {
            return;
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        context.fill(0, 0, width, height, ColorUtil.withAlpha(0xFF000000, 170));

        int left = 40;
        int top = 30;
        int right = width - 40;
        int bottom = height - 30;
        RenderUtil.softShadow(context, left, top, right - left, bottom - top, palette.radius(10), 110);
        RenderUtil.roundedGradientPanel(context, left, top, right - left, bottom - top, palette.radius(10),
                palette.panel(), palette.panelAlt(), palette.border());

        RemoteController controller = feature.controller();
        boolean listening = controller != null && controller.running();
        context.drawTextWithShadow(textRenderer, "\u2661 Overseer", left + 14, top + 12, palette.primary());
        String subtitle = listening
                ? "listening on 127.0.0.1:" + controller.port() + " \u00B7 "
                        + controller.onlineCount() + "/" + controller.instances().size() + " online"
                : "controller is off \u2014 enable the Overseer module to listen";
        context.drawTextWithShadow(textRenderer, subtitle, left + 14, top + 24,
                listening ? palette.textDim() : palette.warning());

        int listTop = top + 44;
        int listBottom = bottom - 22;
        List<InstanceRecord> rows = controller == null ? List.of() : controller.instances();
        Map<String, FleetUpdateModel.Decision> updates = new HashMap<>();
        for (FleetUpdateModel.Decision decision : feature.fleetUpdateDecisions()) {
            updates.put(decision.member().instanceId(), decision);
        }

        if (rows.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "no paired instances yet",
                    left + 14, listTop + 8, palette.textDim());
        }

        context.enableScissor(left + 6, listTop, right - 6, listBottom);
        int y = (int) (listTop - scroll);
        for (InstanceRecord record : rows) {
            if (y + ROW_HEIGHT >= listTop && y <= listBottom) {
                drawRow(context, palette, record, updates.get(record.instanceId()),
                        left + 10, y, right - left - 20, mouseX, mouseY);
            }
            y += ROW_HEIGHT;
        }
        context.disableScissor();

        maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - listTop));
        if (!status.isEmpty() && System.currentTimeMillis() - statusAt < 4000) {
            context.drawTextWithShadow(textRenderer, status, left + 14, bottom - 16, palette.secondary());
        }
        String updateSummary = FleetUpdateModel.summarise(feature.fleetUpdateDecisions());
        context.drawTextWithShadow(textRenderer, updateSummary, left + 14, top + 36, palette.textDim());
        context.drawTextWithShadow(textRenderer, "click a row to select \u00B7 D diagnostics \u00B7 Esc closes",
                right - 300, top + 12, palette.textDim());
    }

    private void drawRow(DrawContext context, Palette palette, InstanceRecord record,
                         FleetUpdateModel.Decision update, int x, int y, int w,
                         int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + ROW_HEIGHT - 2;
        boolean isSelected = record.instanceId().equals(selected);
        int fill = isSelected ? palette.panelHover() : (hovered ? palette.card() : palette.panelAlt());
        RenderUtil.roundedPanel(context, x, y, w, ROW_HEIGHT - 2, palette.radius(4), fill, palette.borderSoft());
        int statusColor = switch (record.status()) {
            case ONLINE -> palette.secondary();
            case OFFLINE, DISCONNECTED -> palette.textDim();
            case AWAITING_PAIRING, CONNECTING -> palette.primary();
            case INCOMPATIBLE, UNAUTHORIZED -> palette.warning();
        };
        context.drawTextWithShadow(textRenderer, "\u25CF", x + 6, y + 7, statusColor);
        context.drawTextWithShadow(textRenderer, record.name(), x + 18, y + 7, palette.text());
        String detail = record.minecraftVersion().isBlank() ? "\u2014" : "MC " + record.minecraftVersion();
        if (!record.clientVersion().isBlank()) {
            detail += " \u00B7 v" + record.clientVersion();
        }
        context.drawTextWithShadow(textRenderer, detail, x + w - 300, y + 7, palette.textDim());
        if (update != null) {
            context.drawTextWithShadow(textRenderer, updateLabel(update), x + w - 170, y + 7,
                    updateColor(palette, update));
        }
        context.drawTextWithShadow(textRenderer, record.status().label(), x + w - 90, y + 7, statusColor);
    }

    private static String updateLabel(FleetUpdateModel.Decision update) {
        return switch (update.status()) {
            case UPDATE_AVAILABLE -> "\u2191 " + update.target().clientVersion();
            case UP_TO_DATE -> "\u2713 up to date";
            case UNSUPPORTED -> "\u2014 unsupported";
            case INCOMPATIBLE -> "\u26A0 incompatible";
        };
    }

    private static int updateColor(Palette palette, FleetUpdateModel.Decision update) {
        return switch (update.status()) {
            case UPDATE_AVAILABLE -> palette.primary();
            case UP_TO_DATE -> palette.secondary();
            case UNSUPPORTED, INCOMPATIBLE -> palette.warning();
        };
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        RemoteController controller = feature.controller();
        if (controller == null) {
            return super.mouseClicked(click, doubled);
        }
        int left = 40;
        int top = 30;
        int bottom = height - 30;
        int listTop = top + 44;
        int listBottom = bottom - 22;
        if (click.y() >= listTop && click.y() <= listBottom) {
            int index = (int) ((click.y() - listTop + scroll) / ROW_HEIGHT);
            List<InstanceRecord> rows = controller.instances();
            if (index >= 0 && index < rows.size()) {
                selected = rows.get(index).instanceId();
                setStatus("Selected " + rows.get(index).name());
            }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_D && !selected.isEmpty()) {
            RemoteController controller = feature.controller();
            if (controller != null) {
                controller.dispatch(List.of(selected),
                        com.logvex.crystal.remote.protocol.RemoteAction.REQUEST_DIAGNOSTICS, java.util.Map.of());
                setStatus("Requested diagnostics from the selected instance");
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return super.charTyped(input);
    }
}
