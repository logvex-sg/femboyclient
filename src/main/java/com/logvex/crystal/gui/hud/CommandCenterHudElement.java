package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.CommandCenterService;
import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.theme.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * A compact, always-available roll-up of the Command Center: the overall health, the number
 * of problems, the frame rate and the top performance finding.
 *
 * <p>It is a pure renderer over the cached Command Center snapshot, so it never triggers a
 * scan while drawing. Hidden by default; the {@code CommandCenter} module reveals it. The
 * colours come from the active palette, so it stays legible under every theme and variant.
 */
public final class CommandCenterHudElement implements HudElement {

    public static final String ID = "command-center";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Column column() {
        return Column.LEFT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y,
                       boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        CommandCenterService service = bootstrap == null ? null : bootstrap.commandCenter();
        if (palette == null || service == null) {
            return;
        }
        String[] lines = lines(service);
        int width = HudText.width(client.textRenderer, lines);
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.length,
                rightAligned, true);
        for (int i = 0; i < lines.length; i++) {
            HudText.line(context, client.textRenderer, lines[i], x, lineY, rightAligned, palette.text());
            lineY += HudText.LINE;
        }
    }

    private static String[] lines(CommandCenterService service) {
        ClientStatusModel model = service.statusModel();
        PerformanceReport performance = service.performance();
        HealthStatus health = model == null ? HealthStatus.OK : model.overall();
        String header = "\u2661 center \u00B7 " + health.label();
        String problems = model == null ? "no data"
                : model.problemCount() == 0 ? "all clear" : model.problemCount() + " problem(s)";
        String perf = performance == null || !performance.sample().hasFrameData()
                ? "perf -"
                : performance.sample().fps() + " fps";
        boolean showFindings = CrystalClient.get() == null
                || CrystalClient.get().commandCenterPreferences().hudShowFindings;
        String top = showFindings && performance != null && !performance.findings().isEmpty()
                ? performance.findings().get(0).observation() : null;
        if (top == null) {
            return new String[]{header, problems + " \u00B7 " + perf};
        }
        return new String[]{header, problems + " \u00B7 " + perf, top};
    }

    @Override
    public int height(MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        CommandCenterService service = bootstrap == null ? null : bootstrap.commandCenter();
        int count = service == null ? 2 : lines(service).length;
        return count * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        CrystalClient bootstrap = CrystalClient.get();
        CommandCenterService service = bootstrap == null ? null : bootstrap.commandCenter();
        return Math.max(104, HudText.width(client.textRenderer,
                service == null ? new String[]{"center"} : lines(service)));
    }
}
