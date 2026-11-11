package com.logvex.crystal.world.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the blueprint library: placement edits, undo/redo and bounded previews. */
class BlueprintLibraryTest {

    private static Schematic rod() {
        return Schematic.of("rod", List.of(
                new Schematic.Block(0, 0, 0, "minecraft:stone"),
                new Schematic.Block(1, 0, 0, "minecraft:stone"),
                new Schematic.Block(2, 0, 0, "minecraft:stone")));
    }

    private static BlueprintLibrary library() {
        BlueprintLibrary library = new BlueprintLibrary();
        library.addSchematic(rod());
        return library;
    }

    @Test
    void placingAnUnknownSchematicIsRejected() {
        BlueprintLibrary library = library();
        boolean threw = false;
        try {
            library.place("nope", 0, 0, 0);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, "placing an unknown schematic must fail loudly");
    }

    @Test
    void moveIsUndoneExactly() {
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement placement = library.place("rod", 10, 64, 10);
        library.move(placement.id(), 5, 0, -3);
        assertEquals(15, placement.x());
        assertEquals(64, placement.y());
        assertEquals(7, placement.z());
        assertEquals("move " + placement.id(), library.undo());
        assertEquals(10, placement.x());
        assertEquals(10, placement.z());
        assertEquals("move " + placement.id(), library.redo());
        assertEquals(15, placement.x());
    }

    @Test
    void rotateWrapsAndUndoesAcrossTheWrap() {
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement placement = library.place("rod", 0, 0, 0);
        for (int i = 0; i < 4; i++) {
            library.rotate(placement.id());
        }
        assertEquals(0, placement.rotation(), "four rotations return to zero");
        library.rotate(placement.id());
        assertEquals(1, placement.rotation());
        library.undo();
        assertEquals(0, placement.rotation());
        // A rotation undone from zero must wrap backwards to three, not to -1.
        library.undo();
        assertEquals(3, placement.rotation());
    }

    @Test
    void undoAndRedoStackDepthTracksEdits() {
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement placement = library.place("rod", 0, 0, 0);
        library.move(placement.id(), 1, 0, 0);
        assertEquals(2, library.history().undoDepth());
        library.undo();
        assertEquals(1, library.history().redoDepth());
        assertTrue(library.canUndo());
        assertTrue(library.canRedo());
        library.undo();
        assertFalse(library.canUndo());
        library.undo();
        assertEquals(0, library.history().undoDepth(), "an empty undo is a no-op");
    }

    @Test
    void removalIsUndoable() {
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement placement = library.place("rod", 0, 0, 0);
        assertEquals(1, library.placements().size());
        library.remove(placement.id());
        assertTrue(library.placements().isEmpty());
        library.undo();
        assertEquals(1, library.placements().size());
        assertEquals(placement.id(), library.placements().get(0).id());
    }

    @Test
    void effectiveSchematicAppliesRotationAndMirror() {
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement placement = library.place("rod", 0, 0, 0);
        library.rotate(placement.id());
        Schematic rotated = library.effectiveSchematic(placement);
        assertEquals(1, rotated.sizeX());
        assertEquals(3, rotated.sizeZ());
        library.mirror(placement.id());
        Schematic mirrored = library.effectiveSchematic(placement);
        assertEquals(3, mirrored.blockCount());
    }

    @Test
    void previewBlocksAreBoundedByRadiusAndLimit() {
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement placement = library.place("rod", 0, 0, 0);
        // All three blocks sit at z=0, x=0..2. A radius of 1 around the origin keeps x=0 and x=1.
        List<BlueprintLibrary.PreviewBlock> near = library.previewBlocks(placement, 0, 0, 1, 128);
        assertEquals(2, near.size());
        // A limit of 1 caps it even when the radius allows more.
        List<BlueprintLibrary.PreviewBlock> capped = library.previewBlocks(placement, 0, 0, 128, 1);
        assertEquals(1, capped.size());
        // An invisible placement contributes nothing.
        library.setVisible(placement.id(), false);
        assertTrue(library.previewBlocks(placement, 0, 0, 128, 128).isEmpty());
    }

    @Test
    void plannedMaterialsSumVisiblePlacementsOnly() {
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement a = library.place("rod", 0, 0, 0);
        library.place("rod", 0, 0, 10);
        assertEquals(6, library.plannedBlockCount());
        assertEquals(6, library.plannedMaterials().get("minecraft:stone"));
        library.setVisible(a.id(), false);
        assertEquals(3, library.plannedBlockCount());
    }
}
