package com.logvex.crystal.gui;

import com.logvex.crystal.compat.Compat;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.gui.hud.CoordsHudElement;
import com.logvex.crystal.gui.hud.CrystalDamageHudElement;
import com.logvex.crystal.gui.hud.KeystrokesHudElement;
import com.logvex.crystal.gui.hud.ModuleListHudElement;
import com.logvex.crystal.gui.hud.NotificationHudElement;
import com.logvex.crystal.gui.hud.PerformanceHudElement;
import com.logvex.crystal.gui.hud.SimpleHudElement;
import com.logvex.crystal.gui.hud.TargetHudElement;
import com.logvex.crystal.gui.hud.WatermarkHudElement;
import com.logvex.crystal.module.ModuleManager;
import com.logvex.crystal.render.RenderUtil;
import com.logvex.crystal.render.esp.WorldEspProvider;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.ColorUtil;
import com.logvex.crystal.theme.ThemeManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Owns the HUD: the set of elements, their layout, and how they stack.
 *
 * <p>Elements render on the render thread through the Fabric HUD callback. An element
 * with a custom position is drawn exactly where the user dragged it; otherwise it stacks
 * top-down in its column. The manager is also the authority used by the drag editor and
 * by commands that toggle individual elements.
 */
public final class HudManager {

    private final Map<String, HudElement> elements = new LinkedHashMap<>();
    private final List<OverlayRenderer> overlays = new ArrayList<>();
    private final List<WorldEspProvider> worldEspProviders = new ArrayList<>();
    /** Reusable draw-order buffer, refilled each frame; avoids per-frame allocation. */
    private final List<HudElement> drawOrder = new ArrayList<>();
    private final ModuleManager modules;
    private final ThemeManager theme;
    private final HudLayout layout = new HudLayout();

    private boolean hudVisible = true;
    private int leftMargin = 4;
    private int rightMargin = 4;
    private float currentOpacity = 1f;

    public HudManager(ModuleManager modules, ThemeManager theme) {
        this.modules = modules;
        this.theme = theme;
        register(new WatermarkHudElement());
        register(new ModuleListHudElement(modules));
        register(new TargetHudElement());
        register(new CrystalDamageHudElement());
        register(new CoordsHudElement());
        register(new PerformanceHudElement());
        register(new NotificationHudElement());
        registerReadouts();
    }

    /**
     * Registers the compact informational readouts. Each is a {@link SimpleHudElement}
     * whose lines are produced from the shared world/player helpers, so adding a readout is
     * a few lines rather than a new class. Every one is toggled by a matching HUD module.
     */
    private void registerReadouts() {
        register(new SimpleHudElement("biome", HudElement.Column.LEFT, 60, false,
                client -> new String[]{"biome " + com.logvex.crystal.world.WorldInfo.biome(client)}));
        register(new SimpleHudElement("time", HudElement.Column.LEFT, 64, false,
                client -> new String[]{
                        "time " + com.logvex.crystal.world.WorldInfo.dayPhase(client),
                        "day " + (client.world == null ? "-" : (client.world.getTimeOfDay() / 24000L))}));
        register(new SimpleHudElement("light", HudElement.Column.LEFT, 56, false,
                client -> new String[]{
                        "light " + com.logvex.crystal.world.WorldInfo.blockLight(client),
                        "sky " + com.logvex.crystal.world.WorldInfo.skyLight(client)}));
        register(new SimpleHudElement("network", HudElement.Column.RIGHT, 70, false,
                client -> new String[]{
                        "dim " + com.logvex.crystal.world.WorldInfo.dimension(client),
                        "ping " + pingText(com.logvex.crystal.world.WorldInfo.ping(client))}));
        register(new SimpleHudElement("entities", HudElement.Column.RIGHT, 78, false,
                HudManager::entityCountLines));
        register(new SimpleHudElement("armor", HudElement.Column.RIGHT, 78, false,
                HudManager::armorLines));
        register(new SimpleHudElement("potion", HudElement.Column.RIGHT, 96, false,
                HudManager::potionLines));
        register(new SimpleHudElement("speed", HudElement.Column.RIGHT, 70, false,
                HudManager::speedLines));
        register(new SimpleHudElement("direction", HudElement.Column.LEFT, 66, false,
                client -> client.player == null ? new String[0] : new String[]{
                        "facing " + com.logvex.crystal.world.WorldInfo.facing(client.player.getYaw()),
                        com.logvex.crystal.gui.hud.HudReadouts.yawLine(client.player.getYaw())}));
        register(new SimpleHudElement("totems", HudElement.Column.RIGHT, 74, false,
                HudManager::totemLines));
        register(new SimpleHudElement("clock", HudElement.Column.RIGHT, 60, false,
                client -> new String[]{com.logvex.crystal.gui.hud.HudReadouts.clock(
                        System.currentTimeMillis(), true)}));
        register(new SimpleHudElement("server", HudElement.Column.LEFT, 96, false,
                HudManager::serverLines));
        register(new SimpleHudElement("inventory", HudElement.Column.RIGHT, 92, false,
                HudManager::inventoryLines));
        register(new com.logvex.crystal.gui.hud.CrosshairHudElement());
        register(new com.logvex.crystal.gui.hud.SessionHudElement());
        register(new com.logvex.crystal.gui.hud.EntityCountHudElement());
        register(new com.logvex.crystal.gui.hud.WorldInfoHudElement());
        register(new KeystrokesHudElement());
        register(new com.logvex.crystal.gui.hud.BaritoneHudElement());
        // The Baritone readout is opt-in: the Baritone module reveals it, so it starts hidden
        // rather than occupying HUD space for players who do not use Baritone.
        layout.entry(com.logvex.crystal.gui.hud.BaritoneHudElement.ID).visible = false;
        register(new com.logvex.crystal.gui.hud.AIHudElement());
        // The AI readout is opt-in too: the AIAssistant module reveals it.
        layout.entry(com.logvex.crystal.gui.hud.AIHudElement.ID).visible = false;
        register(new com.logvex.crystal.gui.hud.CommandCenterHudElement());
        // The Command Center roll-up is opt-in: the CommandCenter module reveals it.
        layout.entry(com.logvex.crystal.gui.hud.CommandCenterHudElement.ID).visible = false;
        register(new com.logvex.crystal.gui.hud.ScanHudElement());
        // The scan readout is opt-in: the WorldScanner module reveals it while scanning.
        layout.entry("scan").visible = false;
        register(new com.logvex.crystal.gui.hud.IntelligenceHudElement());
        // The intelligence readout is opt-in: the WorldScanner module reveals it once it has data.
        layout.entry(com.logvex.crystal.gui.hud.IntelligenceHudElement.ID).visible = false;
        register(new com.logvex.crystal.gui.hud.CompassHudElement());
        // The compass readout is opt-in: the Navigator module reveals it.
        layout.entry(com.logvex.crystal.gui.hud.CompassHudElement.ID).visible = false;
    }

    private static String pingText(int ping) {
        return ping < 0 ? "-" : ping + "ms";
    }

    private static String[] entityCountLines(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            return new String[0];
        }
        int players = 0;
        int mobs = 0;
        int items = 0;
        for (var entity : client.world.getEntities()) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity && entity != client.player) {
                players++;
            } else if (entity instanceof net.minecraft.entity.ItemEntity) {
                items++;
            } else if (entity instanceof net.minecraft.entity.mob.MobEntity) {
                mobs++;
            }
        }
        return new String[]{"players " + players, "mobs " + mobs, "items " + items};
    }

    private static String[] armorLines(MinecraftClient client) {
        if (client.player == null) {
            return new String[0];
        }
        int armor = client.player.getArmor();
        int totalDurability = 0;
        int maxDurability = 0;
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            var stack = client.player.getEquippedStack(slot);
            if (!stack.isEmpty() && stack.isDamageable()) {
                totalDurability += stack.getMaxDamage() - stack.getDamage();
                maxDurability += stack.getMaxDamage();
            }
        }
        if (maxDurability == 0) {
            return new String[]{"armor " + armor};
        }
        return new String[]{"armor " + armor, "dura " + com.logvex.crystal.util.Format.percent(totalDurability / (double) maxDurability)};
    }

    private static String[] potionLines(MinecraftClient client) {
        if (client.player == null) {
            return new String[0];
        }
        var effects = client.player.getStatusEffects();
        if (effects.isEmpty()) {
            return new String[]{"no effects"};
        }
        String[] lines = new String[Math.min(effects.size(), 5)];
        int i = 0;
        for (var effect : effects) {
            if (i >= lines.length) {
                break;
            }
            int seconds = effect.getDuration() / 20;
            int amplifier = effect.getAmplifier();
            lines[i++] = effect.getEffectType().value().getName().getString()
                    + (amplifier > 0 ? " " + (amplifier + 1) : "")
                    + " " + com.logvex.crystal.util.Format.duration(seconds);
        }
        return lines;
    }

    private static String[] speedLines(MinecraftClient client) {
        if (client.player == null) {
            return new String[0];
        }
        var velocity = client.player.getVelocity();
        double bps = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z) * 20.0;
        return new String[]{String.format("%.2f bps", bps)};
    }

    /** Held totem count: main inventory (including the offhand) plus any totem in the offhand. */
    private static String[] totemLines(MinecraftClient client) {
        if (client.player == null) {
            return new String[0];
        }
        int total = com.logvex.crystal.player.InventoryUtil.count(client, net.minecraft.item.Items.TOTEM_OF_UNDYING);
        if (client.player.getOffHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) {
            total += client.player.getOffHandStack().getCount();
        }
        return new String[]{com.logvex.crystal.gui.hud.HudReadouts.totemLine(total)};
    }

    /** Server host and ping, or a singleplayer marker when there is no server entry. */
    private static String[] serverLines(MinecraftClient client) {
        if (client.player == null) {
            return new String[0];
        }
        var entry = client.getCurrentServerEntry();
        String host = com.logvex.crystal.gui.hud.HudReadouts.serverLabel(entry == null ? null : entry.address);
        return new String[]{host, "ping " + com.logvex.crystal.world.WorldInfo.ping(client) + " ms"};
    }

    /** Inventory fill: how many of the 36 main slots hold an item, and how many are free. */
    private static String[] inventoryLines(MinecraftClient client) {
        if (client.player == null) {
            return new String[0];
        }
        var inventory = client.player.getInventory();
        int used = 0;
        for (int slot = 0; slot < com.logvex.crystal.player.InventoryUtil.MAIN_SIZE; slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                used++;
            }
        }
        return new String[]{com.logvex.crystal.gui.hud.HudReadouts.inventoryLine(
                used, com.logvex.crystal.player.InventoryUtil.MAIN_SIZE)};
    }

    public void register(HudElement element) {
        elements.put(element.id(), element);
        layout.entry(element.id());
    }

    /** Unregisters a HUD element by id (used by the extension API to release one). */
    public void removeElement(String id) {
        elements.remove(id);
    }

    public HudLayout layout() {
        return layout;
    }

    public boolean isHudVisible() {
        return hudVisible;
    }

    public void toggleHud() {
        hudVisible = !hudVisible;
    }

    public void setHudVisible(boolean visible) {
        this.hudVisible = visible;
    }

    public void setElementVisible(String id, boolean value) {
        if (elements.containsKey(id)) {
            layout.entry(id).visible = value;
        }
    }

    public boolean isElementVisible(String id) {
        return layout.entry(id).visible;
    }

    public HudElement element(String id) {
        return elements.get(id);
    }

    /**
     * The module that owns a HUD element, or null when the element has no owning module
     * (e.g. the notification feed). Used by the HUD editor to keep the element and its
     * module's enabled state in step when the user hides or shows an element.
     */
    public com.logvex.crystal.module.Module moduleForElement(String id) {
        for (com.logvex.crystal.module.Module module : modules.all()) {
            if (module instanceof com.logvex.crystal.module.hud.HudToggleModule toggle
                    && toggle.elementId().equals(id)) {
                return module;
            }
        }
        return null;
    }

    /** Every HUD element that has no owning toggle module (edited freely in the HUD editor). */
    public boolean isManagedElement(String id) {
        return moduleForElement(id) != null;
    }

    public List<HudElement> elements() {
        return new ArrayList<>(elements.values());
    }

    /** Registers a screen-space overlay drawn after the panels. */
    public void registerOverlay(OverlayRenderer overlay) {
        overlays.add(overlay);
    }

    /** Removes a previously registered overlay. */
    public void removeOverlay(OverlayRenderer overlay) {
        overlays.remove(overlay);
    }

    // ------------------------------------------------------------ world ESP

    /**
     * Registers a world-space ESP provider. The client's world-render hook collects every
     * provider into one buffer and draws it in a single pass, so a module only has to say
     * what to show. Guarded against double registration so a module that is toggled twice
     * cannot accumulate duplicate providers.
     */
    public void registerWorldEsp(WorldEspProvider provider) {
        if (!worldEspProviders.contains(provider)) {
            worldEspProviders.add(provider);
        }
    }

    public void removeWorldEsp(WorldEspProvider provider) {
        worldEspProviders.remove(provider);
    }

    public boolean hasWorldEsp() {
        return !worldEspProviders.isEmpty();
    }

    /**
     * Fills {@code out} from every registered provider. Each provider is isolated so one
     * failing ESP module cannot stop the others or corrupt the frame.
     */
    public void collectWorldEsp(com.logvex.crystal.render.esp.WorldEspCollector out, MinecraftClient client) {
        for (WorldEspProvider provider : worldEspProviders) {
            try {
                provider.collectEsp(out, client);
            } catch (Throwable t) {
                CrystalClient.LOG.error("World ESP provider failed", t);
            }
        }
    }

    /** Draws every visible element, honouring custom positions, then auto-stacked columns. */
    public void render(DrawContext context, MinecraftClient client, float tickDelta) {
        if (!hudVisible || client.player == null) {
            return;
        }
        Palette palette = theme.palette();
        int screenWidth = context.getScaledWindowWidth();
        int leftY = leftMargin;
        int rightY = rightMargin;
        for (HudElement element : drawOrder()) {
            HudLayout.Entry entry = layout.entry(element.id());
            if (!entry.visible) {
                continue;
            }
            try {
                boolean right = element.column() == HudElement.Column.RIGHT;
                int x;
                int y;
                if (entry.customPosition) {
                    x = entry.x;
                    y = entry.y;
                } else if (right) {
                    x = screenWidth - rightMargin;
                    y = rightY;
                } else {
                    x = leftMargin;
                    y = leftY;
                }
                renderScaled(context, element, entry, client, tickDelta, x, y, right);
                if (!entry.customPosition) {
                    int advance = heightOf(element, entry, client) + 2;
                    if (right) {
                        rightY += advance;
                    } else {
                        leftY += advance;
                    }
                }
            } catch (Throwable t) {
                // A broken HUD element must never crash the frame. The element keeps its
                // visibility so a single bad frame (e.g. transient state mid-world-load)
                // cannot permanently hide a panel or desync it from its owning module;
                // the failure is recorded so ; errors can surface it.
                CrystalClient.LOG.error("HUD element {} failed to render", element.id(), t);
                CrystalClient.errors().record(element.id(), t);
            }
        }
        for (OverlayRenderer overlay : overlays) {
            try {
                overlay.renderOverlay(context, client, tickDelta);
            } catch (Throwable t) {
                CrystalClient.LOG.error("HUD overlay failed", t);
            }
        }
    }

    /**
     * The elements in draw order: sorted by layer (ascending), then by registration order so
     * the default layout is unchanged. The buffer is reused between frames to avoid a
     * per-frame allocation; it is only rebuilt when a layer actually differs from insertion
     * order.
     */
    public List<HudElement> drawOrder() {
        drawOrder.clear();
        drawOrder.addAll(elements.values());
        boolean anyLayer = false;
        for (HudElement element : drawOrder) {
            if (layout.entry(element.id()).layer != 0) {
                anyLayer = true;
                break;
            }
        }
        if (anyLayer) {
            drawOrder.sort((a, b) -> Integer.compare(layout.entry(a.id()).layer, layout.entry(b.id()).layer));
        }
        return drawOrder;
    }

    /** The effective height of an element, honouring a per-element override when set. */
    public int heightOf(HudElement element, HudLayout.Entry entry, MinecraftClient client) {
        return entry.height > 0 ? entry.height : element.height(client);
    }

    /** The effective width of an element, honouring a per-element override when set. */
    public int widthOf(HudElement element, HudLayout.Entry entry, MinecraftClient client) {
        return entry.width > 0 ? entry.width : element.width(client);
    }

    /**
     * Computes the on-screen anchor of every element using the same stacking rules as
     * {@link #render}. The editor overlay and its hit-testing use this so their frames sit
     * where the elements are (or would be) drawn.
     *
     * <p>Unlike {@link #render}, hidden elements are included and still occupy a slot, so
     * the layout editor can always reach an element to re-show it and positions do not jump
     * when visibility is toggled.
     */
    public java.util.Map<String, int[]> anchors(MinecraftClient client, int screenWidth) {
        java.util.Map<String, int[]> result = new java.util.LinkedHashMap<>();
        int leftY = leftMargin;
        int rightY = rightMargin;
        for (HudElement element : drawOrder()) {
            HudLayout.Entry entry = layout.entry(element.id());
            boolean right = element.column() == HudElement.Column.RIGHT;
            int x;
            int y;
            if (entry.customPosition) {
                x = entry.x;
                y = entry.y;
            } else if (right) {
                x = screenWidth - rightMargin;
                y = rightY;
            } else {
                x = leftMargin;
                y = leftY;
            }
            result.put(element.id(), new int[]{x, y});
            if (!entry.customPosition) {
                int advance = heightOf(element, entry, client) + 2;
                if (right) {
                    rightY += advance;
                } else {
                    leftY += advance;
                }
            }
        }
        return result;
    }

    /**
     * Renders one element with its per-element scale applied about its anchor, and exposes
     * its opacity for the shared text helpers. The matrix is pushed/popped in a finally so
     * a throwing element can never leave the render matrix stack unbalanced.
     */
    private void renderScaled(DrawContext context, HudElement element, HudLayout.Entry entry,
                              MinecraftClient client, float tickDelta, int x, int y, boolean right) {
        float scale = entry.scale <= 0.05f ? 1f : entry.scale;
        var matrices = context.getMatrices();
        Compat.matrixPush(matrices);
        try {
            if (scale != 1f) {
                Compat.matrixTranslate(matrices, x, y);
                Compat.matrixScale(matrices, scale);
                Compat.matrixTranslate(matrices, -x, -y);
            }
            this.currentOpacity = clamp01(entry.opacity);
            if (entry.background || entry.border) {
                int w = widthOf(element, entry, client);
                int h = heightOf(element, entry, client);
                if (w > 0 && h > 0) {
                    Palette palette = theme.palette();
                    if (entry.background) {
                        com.logvex.crystal.render.RenderUtil.roundedPanel(context, x - 3, y - 2, w + 6, h + 4,
                                palette.radius(4), ColorUtil.scaleAlpha(palette.panel(), currentOpacity * 0.72f),
                                palette.borderSoft());
                    } else {
                        com.logvex.crystal.render.RenderUtil.roundedPanel(context, x - 3, y - 2, w + 6, h + 4,
                                palette.radius(4), 0, ColorUtil.scaleAlpha(palette.borderSoft(), currentOpacity));
                    }
                }
            }
            element.render(context, client, tickDelta, x, y, right);
        } finally {
            this.currentOpacity = 1f;
            Compat.matrixPop(matrices);
        }
    }

    /** Opacity of the element currently being drawn; read by the shared text helpers. */
    public float currentOpacity() {
        return currentOpacity;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** Draws the drag-editor overlay: a soft frame and label around each element. */
    public void renderEditorOverlay(DrawContext context, MinecraftClient client, Palette palette,
                                    int mouseX, int mouseY) {
        int screenWidth = context.getScaledWindowWidth();
        java.util.Map<String, int[]> anchors = anchors(client, screenWidth);
        for (HudElement element : drawOrder()) {
            if (!element.draggable()) {
                continue;
            }
            int[] anchor = anchors.getOrDefault(element.id(), new int[]{leftMargin, leftMargin});
            HudLayout.Entry entry = layout.entry(element.id());
            int x = anchor[0];
            int y = anchor[1];
            int w = Math.max(40, widthOf(element, entry, client));
            int h = Math.max(12, heightOf(element, entry, client));
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            int border = hovered ? palette.primary() : com.logvex.crystal.util.ColorUtil.scaleAlpha(palette.border(), 0.6f);
            RenderUtil.roundedOutline(context, x - 2, y - 2, w + 4, h + 4, palette.radius(4), border);
            context.drawTextWithShadow(client.textRenderer, element.id(), x, y - 9, palette.textDim());
        }
    }

    public ModuleManager modules() {
        return modules;
    }
}
