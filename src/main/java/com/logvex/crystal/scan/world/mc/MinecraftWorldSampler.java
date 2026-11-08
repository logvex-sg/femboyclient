package com.logvex.crystal.scan.world.mc;

import com.logvex.crystal.scan.world.BlockFamilies;
import com.logvex.crystal.scan.world.ChunkSample;
import com.logvex.crystal.scan.world.ScanConfig;
import com.logvex.crystal.scan.world.StructureClassifier;
import com.logvex.crystal.scan.world.TerrainType;
import com.logvex.crystal.scan.world.WorldSampler;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/**
 * The Minecraft side of the scanner's world access.
 *
 * <p>It walks a bounded grid of columns inside the chunk and counts block families; it reads
 * only chunks the client already has loaded and never forces a load. Every block access is a
 * read of state the client already holds, so this cannot bypass a server-side protection or pull
 * data the player could not otherwise see.
 *
 * <p>The sampling cost per chunk is bounded by {@link ScanConfig#sampleStep()} (columns) and
 * {@link ScanConfig#verticalRange()} (height window), and the scanner additionally caps how many
 * chunks are sampled per tick, so a tick's total block reads are bounded regardless of world
 * size.
 */
public final class MinecraftWorldSampler implements WorldSampler {

    private final MinecraftClient client;
    private final BlockPos.Mutable pos = new BlockPos.Mutable();

    public MinecraftWorldSampler(MinecraftClient client) {
        this.client = client;
    }

    private ClientWorld world() {
        return client == null ? null : client.world;
    }

    private ClientPlayerEntity player() {
        return client == null ? null : client.player;
    }

    @Override
    public String dimension() {
        ClientWorld world = world();
        return world == null ? "" : world.getRegistryKey().getValue().toString();
    }

    @Override
    public int playerBlockX() {
        ClientPlayerEntity player = player();
        return player == null ? 0 : player.getBlockX();
    }

    @Override
    public int playerBlockY() {
        ClientPlayerEntity player = player();
        return player == null ? 0 : player.getBlockY();
    }

    @Override
    public int playerBlockZ() {
        ClientPlayerEntity player = player();
        return player == null ? 0 : player.getBlockZ();
    }

    @Override
    public boolean chunkLoaded(int chunkX, int chunkZ) {
        ClientWorld world = world();
        return world != null && world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public boolean worldLoaded() {
        return world() != null && player() != null;
    }

    @Override
    public boolean playerAlive() {
        ClientPlayerEntity player = player();
        return player != null && player.isAlive();
    }

    @Override
    public ChunkSample sample(int chunkX, int chunkZ, ScanConfig config, long nowMillis) {
        ClientWorld world = world();
        if (world == null || !world.isChunkLoaded(chunkX, chunkZ)) {
            return ChunkSample.unloaded(dimension(), chunkX, chunkZ, nowMillis);
        }
        int step = Math.max(1, config.sampleStep());
        int verticalRange = Math.max(1, config.verticalRange());
        int centerY = player() == null ? world.getTopYInclusive() : playerBlockY();
        int minY = Math.max(world.getBottomY(), centerY - verticalRange);
        int maxY = Math.min(world.getTopYInclusive(), centerY + verticalRange);

        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int yStep = Math.max(1, step / 2);

        int sampled = 0;
        int nonAir = 0;
        int solid = 0;
        int transparent = 0;
        int water = 0;
        int logs = 0;
        int leaves = 0;
        int crops = 0;
        int building = 0;
        int ores = 0;
        int chests = 0;
        int lights = 0;
        int redstone = 0;
        int spawners = 0;
        int portals = 0;
        int bells = 0;
        boolean flowingLiquids = false;
        int surfaceY = Integer.MIN_VALUE;
        String surfaceBlock = "";

        for (int x = baseX; x < baseX + 16; x += step) {
            for (int z = baseZ; z < baseZ + 16; z += step) {
                // Vertical family counts over the window.
                for (int y = minY; y <= maxY; y += yStep) {
                    BlockState state = world.getBlockState(pos.set(x, y, z));
                    sampled++;
                    if (state.isAir()) {
                        continue;
                    }
                    nonAir++;
                    String id = Registries.BLOCK.getId(state.getBlock()).getPath();
                    var fluidState = state.getFluidState();
                    boolean fluid = !fluidState.isEmpty();
                    if (fluid) {
                        water++;
                        if (fluidState.getLevel() > 0) {
                            flowingLiquids = true;
                        }
                    }
                    if (state.isOpaqueFullCube()) {
                        solid++;
                    } else if (!fluid) {
                        transparent++;
                    }
                    if (BlockFamilies.isLog(id)) {
                        logs++;
                    }
                    if (BlockFamilies.isLeaves(id)) {
                        leaves++;
                    }
                    if (BlockFamilies.isCrop(id)) {
                        crops++;
                    }
                    if (BlockFamilies.isBuilding(id)) {
                        building++;
                    }
                    if (BlockFamilies.isOre(id)) {
                        ores++;
                    }
                    if (BlockFamilies.isContainer(id)) {
                        chests++;
                    }
                    if (BlockFamilies.isLight(id)) {
                        lights++;
                    }
                    if (BlockFamilies.isRedstone(id)) {
                        redstone++;
                    }
                    if (BlockFamilies.isSpawner(id)) {
                        spawners++;
                    }
                    if (BlockFamilies.isPortal(id)) {
                        portals++;
                    }
                    if (BlockFamilies.isVillageMarker(id)) {
                        bells++;
                    }
                }
                // Surface detection at full vertical resolution, top-down within the window.
                for (int y = maxY; y >= minY; y--) {
                    BlockState state = world.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) {
                        if (y > surfaceY) {
                            surfaceY = y;
                            surfaceBlock = Registries.BLOCK.getId(state.getBlock()).getPath();
                        }
                        break;
                    }
                }
            }
        }

        boolean deep = surfaceY != Integer.MIN_VALUE && surfaceY < 50;
        TerrainType terrain = StructureClassifier.classifyTerrain(surfaceBlock, water, nonAir, logs, leaves, deep);
        WorldChunk chunk = world.getChunk(chunkX, chunkZ);
        long inhabitedTicks = -1L;
        Boolean lightComplete = null;
        if (chunk != null) {
            // Both are values the client already holds: inhabited time is synced with the chunk,
            // and the light flag reflects whether the server sent a populated light layer. Neither
            // requires a request, so this cannot bypass a server-side protection.
            inhabitedTicks = chunk.getInhabitedTime();
            lightComplete = chunk.isLightOn();
        }
        return new ChunkSample(dimension(), chunkX, chunkZ, true, surfaceY, surfaceBlock, sampled, nonAir,
                solid, transparent, water, logs, leaves, crops, building, ores, chests, lights, redstone,
                spawners, portals, bells, terrain, flowingLiquids, inhabitedTicks, lightComplete, nowMillis);
    }
}
