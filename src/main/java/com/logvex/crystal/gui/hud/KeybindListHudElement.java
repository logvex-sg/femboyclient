package com.logvex.crystal.gui.hud;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.HudElement;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.theme.Palette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Lists every module that has a keybind, with the bound key, sorted by module name.
 *
 * <p>Complements {@link KeystrokesHudElement}: that grid shows which movement keys are held,
 * while this shows which keys the client is listening for. The line list is cached and only
 * rebuilt when the set of bound keys changes, so it costs nothing on a steady frame.
 */
public final class KeybindListHudElement implements HudElement {

    private final ModuleManager modules;
    private List<String> cached;
    private int cachedSignature;

    public KeybindListHudElement(ModuleManager modules) {
        this.modules = modules;
    }

    @Override
    public String id() {
        return "keybind-list";
    }

    @Override
    public Column column() {
        return Column.RIGHT;
    }

    @Override
    public void render(DrawContext context, MinecraftClient client, float tickDelta, int x, int y, boolean rightAligned) {
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        if (palette == null) {
            return;
        }
        List<String> lines = boundLines();
        if (lines.isEmpty()) {
            return;
        }
        int width = HudText.width(client.textRenderer, lines.toArray(new String[0]));
        int lineY = HudText.panel(context, client.textRenderer, palette, x, y, width, lines.size(), true, false);
        for (String line : lines) {
            HudText.line(context, client.textRenderer, line, x, lineY, true, palette.text());
            lineY += HudText.LINE;
        }
    }

    private List<String> boundLines() {
        int signature = 0;
        List<KeybindReadouts.Entry> entries = new ArrayList<>();
        for (Module module : modules.all()) {
            if (!module.keybind().isBound()) {
                continue;
            }
            String key = module.keybind().displayValue();
            signature = signature * 31 + module.name().hashCode() * 31 + key.hashCode();
            entries.add(new KeybindReadouts.Entry(module.name(), key));
        }
        if (cached == null || signature != cachedSignature) {
            cached = KeybindReadouts.lines(entries);
            cachedSignature = signature;
        }
        return cached;
    }

    @Override
    public int height(MinecraftClient client) {
        return Math.max(0, boundLines().size()) * HudText.LINE + 6;
    }

    @Override
    public int width(MinecraftClient client) {
        List<String> lines = boundLines();
        if (lines.isEmpty()) {
            return 72;
        }
        return HudText.width(client.textRenderer, lines.toArray(new String[0]));
    }
}
