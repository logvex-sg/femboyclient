package com.logvex.crystal.world.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, dimension-independent block volume: the data model behind the schematic viewer,
 * the placement assistant and the material calculator.
 *
 * <p>A schematic stores only the blocks that are <em>not</em> air, so a large hollow build does
 * not pay for its empty space. Every block is addressed by an offset from the schematic's own
 * origin, so a schematic can be moved, rotated and mirrored without touching its storage.
 *
 * <p>This class holds no client state and performs no I/O, which makes the rotation, mirroring
 * and material counting testable directly.
 */
public final class Schematic {

    /** A single non-air block: an item id plus a palette index for grouping. */
    public record Block(int x, int y, int z, String itemId) {
        public Block {
            Objects.requireNonNull(itemId, "itemId");
        }
    }

    private final String name;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<Block> blocks;

    private Schematic(String name, int sizeX, int sizeY, int sizeZ, List<Block> blocks) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = List.copyOf(blocks);
    }

    /**
     * Builds a schematic from a sparse block list. The bounds are derived from the blocks
     * themselves, so callers never have to declare a size that disagrees with the contents.
     */
    public static Schematic of(String name, List<Block> blocks) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(blocks, "blocks");
        if (blocks.isEmpty()) {
            return new Schematic(name, 0, 0, 0, List.of());
        }
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (Block block : blocks) {
            minX = Math.min(minX, block.x());
            minY = Math.min(minY, block.y());
            minZ = Math.min(minZ, block.z());
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }
        List<Block> normalised = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            normalised.add(new Block(block.x() - minX, block.y() - minY, block.z() - minZ,
                    block.itemId()));
        }
        return new Schematic(name, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, normalised);
    }

    public String name() {
        return name;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int blockCount() {
        return blocks.size();
    }

    public List<Block> blocks() {
        return blocks;
    }

    /** The number of blocks a full solid box of this size would hold, for a fill percentage. */
    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    /** How many of each item the schematic needs, preserving first-seen order. */
    public Map<String, Integer> materialTotals() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Block block : blocks) {
            totals.merge(block.itemId(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(totals);
    }

    /** The distinct item ids, in first-seen order. */
    public List<String> palette() {
        return List.copyOf(materialTotals().keySet());
    }

    /** The block at an offset, or null when that position is air/absent. */
    public Block at(int x, int y, int z) {
        for (Block block : blocks) {
            if (block.x() == x && block.y() == y && block.z() == z) {
                return block;
            }
        }
        return null;
    }

    /**
     * Returns a copy rotated 90 degrees clockwise about the vertical axis {@code times} times.
     * A rotation maps {@code (x, z) -> (sizeZ - 1 - z, x)}, which also swaps the X and Z extents.
     */
    public Schematic rotated(int times) {
        int turns = Math.floorMod(times, 4);
        if (turns == 0) {
            return this;
        }
        List<Block> result = blocks;
        int sx = sizeX;
        int sz = sizeZ;
        for (int i = 0; i < turns; i++) {
            List<Block> next = new ArrayList<>(result.size());
            for (Block block : result) {
                next.add(new Block(sz - 1 - block.z(), block.y(), block.x(), block.itemId()));
            }
            result = next;
            int newSx = sz;
            sz = sx;
            sx = newSx;
        }
        return new Schematic(name, sx, sizeY, sz, result);
    }

    /** Returns a copy mirrored across the X axis, which reverses the Z extent's order. */
    public Schematic mirroredX() {
        List<Block> result = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            result.add(new Block(block.x(), block.y(), sizeZ - 1 - block.z(), block.itemId()));
        }
        return new Schematic(name, sizeX, sizeY, sizeZ, result);
    }

    /** Returns a copy mirrored across the Z axis, which reverses the X extent's order. */
    public Schematic mirroredZ() {
        List<Block> result = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            result.add(new Block(sizeX - 1 - block.x(), block.y(), block.z(), block.itemId()));
        }
        return new Schematic(name, sizeX, sizeY, sizeZ, result);
    }

    /** A compact one-line summary for the viewer header. */
    public String describe() {
        return name + " " + sizeX + "x" + sizeY + "x" + sizeZ + " (" + blocks.size() + " blocks, "
                + palette().size() + " types)";
    }
}
