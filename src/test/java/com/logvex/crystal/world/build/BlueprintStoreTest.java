package com.logvex.crystal.world.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers blueprint persistence: round-trip, corruption tolerance and versioning. */
class BlueprintStoreTest {

    private static BlueprintLibrary library() {
        BlueprintLibrary library = new BlueprintLibrary();
        library.addSchematic(Schematic.of("wall", List.of(
                new Schematic.Block(0, 0, 0, "minecraft:stone"),
                new Schematic.Block(1, 0, 0, "minecraft:stone"))));
        return library;
    }

    @Test
    void roundTripsSchematicsAndPlacements(@TempDir Path dir) {
        Path file = dir.resolve("blueprints.json");
        BlueprintStore store = new BlueprintStore(file);
        BlueprintLibrary library = library();
        BlueprintLibrary.Placement placement = library.place("wall", 100, 64, -200);
        library.rotate(placement.id());
        library.mirror(placement.id());
        assertTrue(store.save(library));

        BlueprintLibrary loaded = store.load();
        assertEquals(1, loaded.schematics().size());
        assertEquals(2, loaded.schematic("wall").orElseThrow().blockCount());
        assertEquals(1, loaded.placements().size());
        BlueprintLibrary.Placement reloaded = loaded.placements().get(0);
        assertEquals(100, reloaded.x());
        assertEquals(-200, reloaded.z());
        assertEquals(1, reloaded.rotation());
        assertTrue(reloaded.mirrorX());
        // A load must not leave a dirty history.
        assertTrue(!loaded.canUndo());
    }

    @Test
    void missingFileLoadsEmpty(@TempDir Path dir) {
        BlueprintLibrary loaded = new BlueprintStore(dir.resolve("nope.json")).load();
        assertTrue(loaded.schematics().isEmpty());
        assertTrue(loaded.placements().isEmpty());
    }

    @Test
    void corruptFileLoadsEmptyInsteadOfThrowing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("blueprints.json");
        java.nio.file.Files.writeString(file, "{ this is not json ");
        BlueprintLibrary loaded = new BlueprintStore(file).load();
        assertTrue(loaded.schematics().isEmpty());
    }
}
