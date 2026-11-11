package com.logvex.crystal.gui.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the pure keybind-list formatter. */
class KeybindReadoutsTest {

    @Test
    void dropsUnboundAndBlankEntries() {
        List<String> lines = KeybindReadouts.lines(List.of(
                new KeybindReadouts.Entry("KillAura", "R"),
                new KeybindReadouts.Entry("AutoTotem", "none"),
                new KeybindReadouts.Entry("AutoEat", ""),
                new KeybindReadouts.Entry("   ", "X")));
        assertEquals(List.of("KillAura  R"), lines);
    }

    @Test
    void sortsCaseInsensitivelyByName() {
        List<String> lines = KeybindReadouts.lines(List.of(
                new KeybindReadouts.Entry("zoom", "C"),
                new KeybindReadouts.Entry("AutoSprint", "V"),
                new KeybindReadouts.Entry("maceSwap", "X")));
        assertEquals(List.of("AutoSprint  V", "maceSwap    X", "zoom        C"), lines);
    }

    @Test
    void alignsTheKeyColumnToTheWidestName() {
        List<String> lines = KeybindReadouts.lines(List.of(
                new KeybindReadouts.Entry("Fly", "G"),
                new KeybindReadouts.Entry("CrystalAura", "R")));
        // "CrystalAura" is 11 wide, so "Fly" is padded to the same width before the key.
        assertEquals("CrystalAura  R", lines.get(0));
        assertEquals("Fly          G", lines.get(1));
    }

    @Test
    void emptyInputProducesNoLines() {
        assertTrue(KeybindReadouts.lines(List.of()).isEmpty());
    }

    @Test
    void noneKeyIsTreatedAsUnboundRegardlessOfCase() {
        assertTrue(KeybindReadouts.lines(List.of(
                new KeybindReadouts.Entry("AutoWalk", "NONE"))).isEmpty());
    }
}
