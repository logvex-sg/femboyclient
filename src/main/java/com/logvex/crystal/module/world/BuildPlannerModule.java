package com.logvex.crystal.module.world;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.render.esp.WorldEspCollector;
import com.logvex.crystal.render.esp.WorldEspProvider;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.setting.IntSetting;
import com.logvex.crystal.theme.Palette;
import com.logvex.crystal.util.UpdateClock;
import com.logvex.crystal.world.build.BlueprintLibrary;
import com.logvex.crystal.world.build.BlueprintStore;
import com.logvex.crystal.world.build.Schematic;
import com.logvex.crystal.config.ConfigPaths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;

/**
 * The build planner: the world-space half of the building toolkit. It owns the shared
 * {@link BlueprintLibrary}, draws the placement previews as a bounded ghost outline and reports
 * the live build progress and missing materials.
 *
 * <p>Nothing is ever placed in the world automatically. The preview is an overlay of the planned
 * blocks within a configurable radius of the player, recomputed on an interval rather than every
 * frame and capped by a block budget, so a large schematic cannot make rendering cost grow with
 * its size.
 */
public final class BuildPlannerModule extends Module implements WorldEspProvider {

    private final IntSetting radius = add("PreviewRadius", "Horizontal radius to preview around the player.", 48, 8, 192);
    private final IntSetting maxBlocks = add("MaxPreview", "Cap on previewed blocks.", 2048, 64, 8192);
    private final IntSetting interval = add("RefreshMs", "Minimum interval between preview rebuilds.", 500, 100, 5000);
    private final BooleanSetting showProgress = add("Progress", "Show build progress as HUD lines.", true);

    private final UpdateClock clock = new UpdateClock(500);
    // Resolved lazily: ConfigPaths touches the Fabric loader, which is absent in unit tests.
    private BlueprintStore store;
    private final BlueprintLibrary library = new BlueprintLibrary();

    private List<BlueprintLibrary.PreviewBlock> cached = List.of();
    private boolean loaded;

    public BuildPlannerModule() {
        super(Category.WORLD, "BuildPlanner",
                "Schematic viewer, placement preview, material calculator and build progress.");
    }

    @Override
    protected void onEnable() {
        ensureLoaded();
        context().hudManager().registerWorldEsp(this);
    }

    @Override
    protected void onDisable() {
        context().hudManager().removeWorldEsp(this);
    }

    /** The shared blueprint library, for the GUI, the commands and the placement assistant. */
    public BlueprintLibrary library() {
        ensureLoaded();
        return library;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            store = new BlueprintStore(ConfigPaths.directory().resolve("blueprints.json"));
        } catch (RuntimeException e) {
            store = null;
        }
        library.addSchematic(Schematic.of("demo-3x3-floor", demoFloor()));
        if (store == null) {
            return;
        }
        try {
            BlueprintLibrary persisted = store.load();
            for (Schematic schematic : persisted.schematics()) {
                library.addSchematic(schematic);
            }
            for (BlueprintLibrary.Placement placement : persisted.placements()) {
                if (library.schematic(placement.schematicName()).isPresent()) {
                    BlueprintLibrary.Placement copy = library.place(placement.schematicName(),
                            placement.x(), placement.y(), placement.z());
                    for (int i = 0; i < placement.rotation(); i++) {
                        library.rotate(copy.id());
                    }
                    if (placement.mirrorX()) {
                        library.mirror(copy.id());
                    }
                    library.setVisible(copy.id(), placement.visible());
                }
            }
            library.history().clear();
        } catch (RuntimeException ignored) {
            // A corrupt plan must not stop the module from working with the demo library.
        }
    }

    /** Saves the current plan. Called from the GUI and commands; never on a tick. */
    public boolean save() {
        ensureLoaded();
        if (store == null) {
            return false;
        }
        try {
            return store.save(library);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static List<Schematic.Block> demoFloor() {
        List<Schematic.Block> blocks = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                blocks.add(new Schematic.Block(x, 0, z, "minecraft:stone_bricks"));
            }
        }
        return blocks;
    }

    @Override
    public void collectEsp(WorldEspCollector out, MinecraftClient client) {
        ensureLoaded();
        if (client == null || client.player == null || library.placements().isEmpty()) {
            return;
        }
        int centerX = client.player.getBlockX();
        int centerZ = client.player.getBlockZ();
        clock.setIntervalMs(interval.value());
        if (clock.ready()) {
            cached = new ArrayList<>();
            int budget = maxBlocks.value();
            for (BlueprintLibrary.Placement placement : library.placements()) {
                if (budget <= 0) {
                    break;
                }
                List<BlueprintLibrary.PreviewBlock> slice =
                        library.previewBlocks(placement, centerX, centerZ, radius.value(), budget);
                cached.addAll(slice);
                budget -= slice.size();
            }
        }
        CrystalClient bootstrap = CrystalClient.get();
        Palette palette = bootstrap == null ? null : bootstrap.theme().palette();
        int color = palette == null ? 0xFF9AD0C2 : palette.primary();
        for (BlueprintLibrary.PreviewBlock block : cached) {
            out.addBox(block.x(), block.y(), block.z(), block.x() + 1, block.y() + 1, block.z() + 1,
                    color, 1.0f);
        }
    }

    /** The live build-progress lines the HUD element and {@code ; world build} both render. */
    public String[] progressLines(MinecraftClient client) {
        ensureLoaded();
        if (!showProgress.value() || client == null || client.player == null) {
            return new String[0];
        }
        var have = client.player.getInventory();
        java.util.Map<String, Integer> totals = library.plannedMaterials();
        int planned = library.plannedBlockCount();
        int held = 0;
        for (String item : totals.keySet()) {
            int count = 0;
            for (int slot = 0; slot < com.logvex.crystal.player.InventoryUtil.MAIN_SIZE; slot++) {
                var stack = have.getStack(slot);
                if (!stack.isEmpty() && itemOf(stack).equals(item)) {
                    count += stack.getCount();
                }
            }
            held += Math.min(count, totals.get(item));
        }
        List<String> lines = new ArrayList<>();
        lines.add("build " + planned + " block(s), " + library.placements().size() + " placement(s)");
        if (planned > 0) {
            double progress = Math.min(1.0, held / (double) planned);
            lines.add(String.format(Locale.ROOT, "ready %.0f%% (%d/%d)", progress * 100, held, planned));
        }
        for (var entry : library.plannedMaterials().entrySet()) {
            lines.add("  " + entry.getKey() + " x" + entry.getValue());
        }
        return lines.toArray(new String[0]);
    }

    private static String itemOf(net.minecraft.item.ItemStack stack) {
        return net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
    }
}
