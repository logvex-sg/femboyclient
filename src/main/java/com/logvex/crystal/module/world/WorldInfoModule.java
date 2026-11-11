package com.logvex.crystal.module.world;

import com.logvex.crystal.client.CrystalClient;
import com.logvex.crystal.entity.EntityInfo;
import com.logvex.crystal.entity.EntityQuery;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.setting.BooleanSetting;
import com.logvex.crystal.util.Format;
import com.logvex.crystal.world.WorldInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * A single, configurable world-information panel: dimension and biome, coordinates and
 * facing, time and weather, the local light level, the block or entity the crosshair is on,
 * local entity counts and loaded-chunk diagnostics.
 *
 * <p>Everything it reports is state the client already holds: the loaded world, the player,
 * the shared {@link EntityQuery} snapshot and the crosshair target the client computes every
 * frame. It never force-loads a chunk and never scans the world - the chunk diagnostics read
 * the client's own loaded-chunk counter, and the counts come from the shared snapshot, so the
 * cost is constant regardless of how large the world is.
 *
 * <p>The same {@link #buildLines} method feeds both the HUD element and the {@code ; world}
 * command, so the two can never disagree.
 */
public final class WorldInfoModule extends Module {

    private final BooleanSetting showDimension = add("Dimension", "Show dimension and biome.", true);
    private final BooleanSetting showCoords = add("Coords", "Show coordinates and facing.", true);
    private final BooleanSetting showTime = add("Time", "Show world time and weather.", true);
    private final BooleanSetting showLight = add("Light", "Show the local light level.", true);
    private final BooleanSetting showTarget = add("Target", "Show the targeted block or entity.", true);
    private final BooleanSetting showCounts = add("Counts", "Show local entity counts.", true);
    private final BooleanSetting showChunks = add("Chunks", "Show loaded-chunk diagnostics.", false);

    public WorldInfoModule() {
        super(Category.WORLD, "WorldInfo", "A configurable world-information panel and the ; world command.");
    }

    @Override
    protected void onEnable() {
        context().hudManager().setElementVisible("worldinfo", true);
    }

    @Override
    protected void onDisable() {
        context().hudManager().setElementVisible("worldinfo", false);
    }

    /** The lines the HUD panel and the command both render, honouring the toggles. */
    public String[] buildLines(MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        if (client == null || client.world == null || client.player == null) {
            return new String[0];
        }
        if (showDimension.value()) {
            lines.add("dim " + WorldInfo.dimension(client));
            lines.add("biome " + WorldInfo.biome(client));
        }
        if (showCoords.value()) {
            Vec3d pos = client.player.getEntityPos();
            lines.add("xyz " + Format.coords(pos.x, pos.y, pos.z));
            lines.add("facing " + WorldInfo.facing(client.player.getYaw()));
        }
        if (showTime.value()) {
            lines.add("time " + WorldInfo.dayPhase(client)
                    + (WorldInfo.raining(client) ? " (rain)" : ""));
        }
        if (showLight.value()) {
            lines.add("light " + WorldInfo.blockLight(client) + " / sky " + WorldInfo.skyLight(client));
        }
        if (showTarget.value()) {
            lines.addAll(targetLines(client));
        }
        if (showCounts.value()) {
            var counts = EntityQuery.get().counts();
            lines.add("players " + counts.getOrDefault(com.logvex.crystal.entity.EntityCategory.PLAYER, 0)
                    + " mobs " + (counts.getOrDefault(com.logvex.crystal.entity.EntityCategory.HOSTILE, 0)
                    + counts.getOrDefault(com.logvex.crystal.entity.EntityCategory.PASSIVE, 0))
                    + " items " + counts.getOrDefault(com.logvex.crystal.entity.EntityCategory.ITEM, 0));
        }
        if (showChunks.value()) {
            lines.addAll(chunkLines(client));
        }
        return lines.toArray(new String[0]);
    }

    private static List<String> targetLines(MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        BlockPos block = WorldInfo.targetBlock(client);
        if (block != null) {
            BlockState state = client.world.getBlockState(block);
            String id = Registries.BLOCK.getId(state.getBlock()).getPath();
            lines.add("block " + id + " @ " + Format.coords(block.getX(), block.getY(), block.getZ()));
            String props = describeProperties(state);
            if (!props.isEmpty()) {
                lines.add("  " + props);
            }
            lines.add("  " + resistanceLine(client, block, state));
            return lines;
        }
        Entity entity = WorldInfo.targetEntity(client);
        if (entity != null) {
            EntityInfo info = null;
            for (EntityInfo candidate : EntityQuery.get().all()) {
                if (candidate.runtimeId() == entity.getId()) {
                    info = candidate;
                    break;
                }
            }
            String name = entity.getName().getString();
            if (info != null) {
                lines.add("entity " + name + " (" + info.category().label() + ")");
                if (info.living()) {
                    lines.add("  " + Format.percent(info.healthFraction()) + " hp "
                            + (int) info.health() + "/" + (int) info.maxHealth());
                }
            } else {
                lines.add("entity " + name);
            }
            return lines;
        }
        lines.add("target none");
        return lines;
    }

    /** The supported state properties of a block, e.g. {@code facing=north powered=true}. */
    static String describeProperties(BlockState state) {
        StringBuilder builder = new StringBuilder();
        int shown = 0;
        for (Property<?> property : state.getProperties()) {
            if (shown++ >= 4) {
                builder.append("\u2026");
                break;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(property.getName()).append('=').append(propertyValue(state, property));
        }
        return builder.toString();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propertyValue(BlockState state, Property property) {
        Comparable<?> value = (Comparable<?>) state.get(property);
        return property.name(value);
    }

    /** Blast resistance and hardness of the targeted block, so mining/explosion cost is visible. */
    static String resistanceLine(MinecraftClient client, BlockPos pos, BlockState state) {
        float resistance = state.getBlock().getBlastResistance();
        float hardness = state.getHardness(client.world, pos);
        String hardnessText = hardness < 0 ? "unbreakable"
                : String.format(java.util.Locale.ROOT, "%.1f", hardness);
        return String.format(java.util.Locale.ROOT, "res %.1f \u00b7 hardness %s", resistance, hardnessText);
    }

    private static List<String> chunkLines(MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        try {
            int loaded = client.world.getChunkManager().getLoadedChunkCount();
            lines.add("chunks " + loaded);
        } catch (RuntimeException ignored) {
            // Some environments do not expose the chunk manager; skip the line rather than fail.
        }
        BlockPos pos = client.player.getBlockPos();
        lines.add("chunk " + (pos.getX() >> 4) + "," + (pos.getZ() >> 4));
        return lines;
    }

    /** Whether the panel would currently show anything, so the HUD can skip the draw. */
    public boolean hasContent(MinecraftClient client) {
        return buildLines(client).length > 0;
    }

    /** Exposed for tests: the block-property description of a state. */
    public static String propertiesOf(BlockState state) {
        return describeProperties(state);
    }

    /** True when a tracker is wired (used by the browser/command to guard reads). */
    public static boolean trackerAvailable() {
        return CrystalClient.get() != null;
    }
}
