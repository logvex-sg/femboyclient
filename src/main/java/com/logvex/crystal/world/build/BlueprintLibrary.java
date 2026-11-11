package com.logvex.crystal.world.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The in-memory library of loaded schematics and their planned placements.
 *
 * <p>A placement is a reference to a schematic plus an origin, rotation and mirror. Editing a
 * placement (moving it, rotating it, deleting it) is recorded in an {@link EditHistory}, which is
 * what the "Undo/Redo for supported local planning operations" requirement means: the planner's
 * own model is undoable, the world is never touched behind the user's back.
 *
 * <p>Every mutation goes through this class so the GUI, the commands and the placement assistant
 * always observe one consistent plan.
 */
public final class BlueprintLibrary {

    /** One planned placement of a schematic in the world. */
    public static final class Placement {
        private final String id;
        private String schematicName;
        private int x;
        private int y;
        private int z;
        private int rotation;
        private boolean mirrorX;
        private boolean visible = true;

        Placement(String id, String schematicName, int x, int y, int z) {
            this.id = Objects.requireNonNull(id, "id");
            this.schematicName = Objects.requireNonNull(schematicName, "schematicName");
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String id() {
            return id;
        }

        public String schematicName() {
            return schematicName;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public int rotation() {
            return rotation;
        }

        public boolean mirrorX() {
            return mirrorX;
        }

        public boolean visible() {
            return visible;
        }

        public String describe() {
            return schematicName + " @ (" + x + ", " + y + ", " + z + ")"
                    + (rotation == 0 ? "" : " rot " + (rotation * 90) + "\u00b0")
                    + (mirrorX ? " mirrored" : "");
        }

        private Placement copy() {
            Placement clone = new Placement(id, schematicName, x, y, z);
            clone.rotation = rotation;
            clone.mirrorX = mirrorX;
            clone.visible = visible;
            return clone;
        }
    }

    private final Map<String, Schematic> schematics = new LinkedHashMap<>();
    private final Map<String, Placement> placements = new LinkedHashMap<>();
    private final EditHistory<BlueprintLibrary> history = new EditHistory<>(64);
    private int idCounter;

    /** Registers a schematic under its name, replacing any earlier one with the same name. */
    public void addSchematic(Schematic schematic) {
        Objects.requireNonNull(schematic, "schematic");
        schematics.put(schematic.name(), schematic);
    }

    public Optional<Schematic> schematic(String name) {
        return Optional.ofNullable(schematics.get(name));
    }

    public List<Schematic> schematics() {
        return List.copyOf(schematics.values());
    }

    /** Places a schematic at an origin, recording the placement for undo. */
    public Placement place(String schematicName, int x, int y, int z) {
        if (!schematics.containsKey(schematicName)) {
            throw new IllegalArgumentException("unknown schematic: " + schematicName);
        }
        Placement placement = new Placement("p" + (++idCounter), schematicName, x, y, z);
        placements.put(placement.id(), placement);
        history.record("place " + schematicName,
                lib -> lib.placements.put(placement.id(), placement),
                lib -> lib.placements.remove(placement.id()));
        return placement;
    }

    /** Moves a placement by a delta, recording the inverse move. */
    public boolean move(String placementId, int dx, int dy, int dz) {
        Placement placement = placements.get(placementId);
        if (placement == null) {
            return false;
        }
        int ox = placement.x;
        int oy = placement.y;
        int oz = placement.z;
        placement.x += dx;
        placement.y += dy;
        placement.z += dz;
        history.record("move " + placementId,
                lib -> {
                    Placement p = lib.placements.get(placementId);
                    if (p != null) {
                        p.x += dx;
                        p.y += dy;
                        p.z += dz;
                    }
                },
                lib -> {
                    Placement p = lib.placements.get(placementId);
                    if (p != null) {
                        p.x = ox;
                        p.y = oy;
                        p.z = oz;
                    }
                });
        return true;
    }

    /** Rotates a placement 90 degrees clockwise, recording the inverse rotation. */
    public boolean rotate(String placementId) {
        Placement placement = placements.get(placementId);
        if (placement == null) {
            return false;
        }
        placement.rotation = (placement.rotation + 1) % 4;
        history.record("rotate " + placementId,
                lib -> {
                    Placement p = lib.placements.get(placementId);
                    if (p != null) {
                        p.rotation = (p.rotation + 1) % 4;
                    }
                },
                lib -> {
                    Placement p = lib.placements.get(placementId);
                    if (p != null) {
                        p.rotation = Math.floorMod(p.rotation - 1, 4);
                    }
                });
        return true;
    }

    /** Flips a placement across the X axis, recording the inverse flip. */
    public boolean mirror(String placementId) {
        Placement placement = placements.get(placementId);
        if (placement == null) {
            return false;
        }
        placement.mirrorX = !placement.mirrorX;
        history.record("mirror " + placementId,
                lib -> {
                    Placement p = lib.placements.get(placementId);
                    if (p != null) {
                        p.mirrorX = !p.mirrorX;
                    }
                },
                lib -> {
                    Placement p = lib.placements.get(placementId);
                    if (p != null) {
                        p.mirrorX = !p.mirrorX;
                    }
                });
        return true;
    }

    /** Removes a placement, recording the removal so it can be undone. */
    public boolean remove(String placementId) {
        Placement placement = placements.remove(placementId);
        if (placement == null) {
            return false;
        }
        history.record("remove " + placementId,
                lib -> lib.placements.remove(placementId),
                lib -> lib.placements.put(placementId, placement));
        return true;
    }

    /** Toggles a placement's preview visibility; not an undoable edit, it is only a view flag. */
    public void setVisible(String placementId, boolean visible) {
        Placement placement = placements.get(placementId);
        if (placement != null) {
            placement.visible = visible;
        }
    }

    public String undo() {
        return history.undo(this);
    }

    public String redo() {
        return history.redo(this);
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public EditHistory<BlueprintLibrary> history() {
        return history;
    }

    public List<Placement> placements() {
        return List.copyOf(placements.values());
    }

    public Placement placement(String id) {
        return placements.get(id);
    }

    /**
     * The effective schematic for a placement: its rotation and mirror applied. Used by the
     * placement preview so the ghost blocks match what would actually be built.
     */
    public Schematic effectiveSchematic(Placement placement) {
        Schematic base = schematics.get(placement.schematicName());
        if (base == null) {
            return null;
        }
        Schematic result = base.rotated(placement.rotation());
        if (placement.mirrorX()) {
            result = result.mirroredX();
        }
        return result;
    }

    /** Total blocks across every visible placement, for the build progress tracker. */
    public int plannedBlockCount() {
        int total = 0;
        for (Placement placement : placements.values()) {
            if (!placement.visible()) {
                continue;
            }
            Schematic schematic = effectiveSchematic(placement);
            if (schematic != null) {
                total += schematic.blockCount();
            }
        }
        return total;
    }

    /** Combined material totals across every visible placement. */
    public Map<String, Integer> plannedMaterials() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (Placement placement : placements.values()) {
            if (!placement.visible()) {
                continue;
            }
            Schematic schematic = effectiveSchematic(placement);
            if (schematic == null) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : schematic.materialTotals().entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return totals;
    }

    /** One block of a placement preview, already in world coordinates. */
    public record PreviewBlock(int x, int y, int z, String itemId) {
    }

    /**
     * The blocks of a placement's effective schematic that fall inside a horizontal radius of a
     * point, capped at {@code limit}. Bounded by construction so a huge schematic near the player
     * cannot make a frame render millions of boxes: only the slice around the player is returned,
     * and the caller renders at most {@code limit} of them.
     */
    public List<PreviewBlock> previewBlocks(Placement placement, int centerX, int centerZ,
                                            int radius, int limit) {
        return previewBlocks(placement, centerX, centerZ, radius, limit, LAYER_ALL);
    }

    /** Sentinel layer value meaning "every layer". */
    public static final int LAYER_ALL = Integer.MIN_VALUE;

    /**
     * As {@link #previewBlocks(Placement, int, int, int, int)} but restricted to one world
     * Y layer when {@code onlyLayerY} is not {@link #LAYER_ALL}. This is the multi-layer viewer:
     * a builder steps a single layer at a time without the whole build occluding the view. The
     * limit is applied after the layer filter, so a thin slice still fills the budget.
     */
    public List<PreviewBlock> previewBlocks(Placement placement, int centerX, int centerZ,
                                            int radius, int limit, int onlyLayerY) {
        if (placement == null || !placement.visible()) {
            return List.of();
        }
        Schematic schematic = effectiveSchematic(placement);
        if (schematic == null) {
            return List.of();
        }
        int originX = placement.x();
        int originY = placement.y();
        int originZ = placement.z();
        long radiusSq = (long) radius * radius;
        List<PreviewBlock> result = new ArrayList<>();
        for (Schematic.Block block : schematic.blocks()) {
            int wy = originY + block.y();
            if (onlyLayerY != LAYER_ALL && wy != onlyLayerY) {
                continue;
            }
            int wx = originX + block.x();
            int wz = originZ + block.z();
            long dx = wx - centerX;
            long dz = wz - centerZ;
            if (dx * dx + dz * dz > radiusSq) {
                continue;
            }
            result.add(new PreviewBlock(wx, wy, wz, block.itemId()));
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /** A snapshot of the current plan, for the build history log. */
    public List<String> describePlan() {
        List<String> lines = new ArrayList<>();
        for (Placement placement : placements.values()) {
            lines.add(placement.describe());
        }
        return lines;
    }
}
