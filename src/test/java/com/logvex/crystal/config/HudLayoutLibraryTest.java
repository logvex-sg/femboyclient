package com.logvex.crystal.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.gui.HudLayout;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers the named HUD-layout library: save/load/list, clashing names and import/export. */
class HudLayoutLibraryTest {

    private static HudLayout layoutWith(String id, int x, int y, float scale) {
        HudLayout layout = new HudLayout();
        HudLayout.Entry entry = layout.entry(id);
        entry.customPosition = true;
        entry.x = x;
        entry.y = y;
        entry.scale = scale;
        return layout;
    }

    @Test
    void sanitizeKeepsSafeStemsAndFallsBack(@TempDir Path dir) {
        assertEquals("pvp-layout", HudLayoutLibrary.sanitize("  PvP Layout "));
        assertEquals("layout", HudLayoutLibrary.sanitize("   "));
        assertEquals("a.b_c-d", HudLayoutLibrary.sanitize("a.b_c-d"));
    }

    @Test
    void saveThenLoadRoundTripsTheLayout(@TempDir Path dir)  throws java.io.IOException {
        HudLayoutLibrary library = new HudLayoutLibrary(dir);
        assertTrue(library.save("combat", layoutWith("coords", 12, 34, 1.5f), false));
        assertTrue(library.has("combat"));
        assertEquals(java.util.List.of("combat"), library.names());

        HudLayout restored = new HudLayout();
        assertTrue(library.load("combat", restored));
        HudLayout.Entry entry = restored.entry("coords");
        assertEquals(12, entry.x);
        assertEquals(34, entry.y);
        assertEquals(1.5f, entry.scale, 1e-4f);
    }

    @Test
    void saveRefusesToOverwriteUnlessAsked(@TempDir Path dir)  throws java.io.IOException {
        HudLayoutLibrary library = new HudLayoutLibrary(dir);
        assertTrue(library.save("mine", layoutWith("coords", 1, 1, 1f), false));
        assertFalse(library.save("mine", layoutWith("coords", 99, 99, 1f), false));

        HudLayout stillFirst = new HudLayout();
        assertTrue(library.load("mine", stillFirst));
        assertEquals(1, stillFirst.entry("coords").x);

        assertTrue(library.save("mine", layoutWith("coords", 99, 99, 1f), true));
        HudLayout overwritten = new HudLayout();
        assertTrue(library.load("mine", overwritten));
        assertEquals(99, overwritten.entry("coords").x);
    }

    @Test
    void loadingMissingLayoutReturnsFalse(@TempDir Path dir) {
        HudLayoutLibrary library = new HudLayoutLibrary(dir);
        assertFalse(library.load("nope", new HudLayout()));
        assertNull(library.export("nope"));
        assertFalse(library.delete("nope"));
    }

    @Test
    void exportWritesAnExportDocumentThatImportRestores(@TempDir Path dir)  throws java.io.IOException {
        HudLayoutLibrary library = new HudLayoutLibrary(dir);
        assertTrue(library.save("shared", layoutWith("fps", 5, 6, 1f), false));

        Path exported = library.export("shared");
        assertNotNull(exported);
        assertTrue(exported.getFileName().toString().endsWith(HudLayoutLibrary.EXPORT_SUFFIX));

        // Deleting the original then importing the export brings it back.
        assertTrue(library.delete("shared"));
        assertFalse(library.has("shared"));
        var imported = library.importAll();
        assertEquals(java.util.List.of("shared"), imported);
        assertTrue(library.has("shared"));
    }

    @Test
    void importNeverOverwritesAnExistingLayout(@TempDir Path dir)  throws java.io.IOException {
        HudLayoutLibrary library = new HudLayoutLibrary(dir);
        assertTrue(library.save("mine", layoutWith("fps", 1, 1, 1f), false));
        library.export("mine");
        // The original still exists, so the import must land under a fresh name.
        var imported = library.importAll();
        assertEquals(1, imported.size());
        assertFalse("mine".equals(imported.get(0)));
        assertTrue(library.has("mine"));
        assertTrue(library.has(imported.get(0)));
    }

    @Test
    void namesSkipsExportDocumentsAndEmptyDirectoryIsSafe(@TempDir Path dir)  throws java.io.IOException {
        HudLayoutLibrary library = new HudLayoutLibrary(dir);
        assertTrue(library.names().isEmpty());
        assertTrue(library.importAll().isEmpty());
        assertTrue(library.save("one", layoutWith("fps", 1, 1, 1f), false));
        library.export("one");
        assertEquals(java.util.List.of("one"), library.names());
    }
}
