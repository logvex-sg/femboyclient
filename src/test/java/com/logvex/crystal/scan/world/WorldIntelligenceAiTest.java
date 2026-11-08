package com.logvex.crystal.scan.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests the AI layer: the deterministic tier always answers, the model tier is optional, and a
 * model failure or an empty answer falls back to the deterministic answer rather than inventing
 * anything.
 */
class WorldIntelligenceAiTest {

    private static DiscoveryDatabase seeded() {
        DiscoveryDatabase db = new DiscoveryDatabase(java.nio.file.Path.of("unused"));
        db.add(new Observation("a", "s", "minecraft:overworld", 0, 0, 10L, StructureCategory.STORAGE,
                0.9, VerificationStatus.CONFIRMED, List.of("chests"), null, ""));
        return db;
    }

    private static WorldQuery.Context context() {
        return new WorldQuery.Context(seeded(), new RegionTracker(), "minecraft:overworld",
                new ScanBounds(0, 0, 15, 15));
    }

    /** A backend that records the prompts it was given and answers from a scripted list. */
    private static final class FakeBackend implements AiTextBackend {
        boolean available = true;
        final List<String> prompts = new ArrayList<>();
        String reply = "model answer";

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public String describe() {
            return available ? "fake model" : "none";
        }

        @Override
        public void submit(String key, String prompt, java.util.function.Consumer<String> onResult) {
            prompts.add(prompt);
            onResult.accept(reply);
        }
    }

    @Test
    void withoutABackendItAnswersDeterministically() {
        WorldIntelligenceAi ai = new WorldIntelligenceAi(null);
        assertFalse(ai.modelAvailable());
        AtomicReference<WorldQuery.Answer> result = new AtomicReference<>();
        ai.ask("how many discoveries are recorded", context(), result::set);
        assertTrue(result.get().supported());
        assertTrue(result.get().text().contains("observation"));
    }

    @Test
    void modelAnswerIsUsedWhenAvailable() {
        FakeBackend backend = new FakeBackend();
        WorldIntelligenceAi ai = new WorldIntelligenceAi(backend);
        assertTrue(ai.modelAvailable());
        AtomicReference<WorldQuery.Answer> result = new AtomicReference<>();
        ai.ask("how many discoveries are recorded", context(), result::set);
        assertEquals("model answer", result.get().text());
        assertEquals("ai", result.get().intent());
        assertFalse(backend.prompts.isEmpty());
        // The prompt carries the recorded facts so the model is rephrasing, not inventing.
        assertTrue(backend.prompts.get(0).contains("Recorded facts"));
    }

    @Test
    void emptyModelAnswerFallsBackToDeterministic() {
        FakeBackend backend = new FakeBackend();
        backend.reply = "   ";
        WorldIntelligenceAi ai = new WorldIntelligenceAi(backend);
        AtomicReference<WorldQuery.Answer> result = new AtomicReference<>();
        ai.ask("how many discoveries are recorded", context(), result::set);
        assertTrue(result.get().text().contains("observation"));
    }

    @Test
    void nullModelAnswerFallsBackToDeterministic() {
        FakeBackend backend = new FakeBackend();
        backend.reply = null;
        WorldIntelligenceAi ai = new WorldIntelligenceAi(backend);
        AtomicReference<WorldQuery.Answer> result = new AtomicReference<>();
        ai.ask("show uncertain observations", context(), result::set);
        assertTrue(result.get().supported());
    }

    @Test
    void historyIsRecordedAndCapped() {
        WorldIntelligenceAi ai = new WorldIntelligenceAi(null);
        for (int i = 0; i < 40; i++) {
            ai.askLocal("how many discoveries are recorded", context());
            ai.ask("how many discoveries are recorded", context(), a -> {
            });
        }
        assertTrue(ai.history().size() <= WorldIntelligenceAi.HISTORY_CAP);
        ai.clearHistory();
        assertTrue(ai.history().isEmpty());
    }

    @Test
    void backendDescriptionReflectsAvailability() {
        assertTrue(new WorldIntelligenceAi(null).backendDescription().contains("deterministic"));
        assertEquals("fake model", new WorldIntelligenceAi(new FakeBackend()).backendDescription());
    }

    @Test
    void disablingTheModelTierForcesDeterministicAnswers() {
        FakeBackend backend = new FakeBackend();
        WorldIntelligenceAi ai = new WorldIntelligenceAi(backend);
        assertTrue(ai.modelAvailable());
        ai.setModelAllowed(false);
        assertFalse(ai.modelAvailable());
        AtomicReference<WorldQuery.Answer> result = new AtomicReference<>();
        ai.ask("how many discoveries are recorded", context(), result::set);
        assertTrue(result.get().text().contains("observation"), "the deterministic answer is used");
        assertTrue(backend.prompts.isEmpty(), "the model is never called while disallowed");
        ai.setModelAllowed(true);
        assertTrue(ai.modelAvailable());
    }
}
