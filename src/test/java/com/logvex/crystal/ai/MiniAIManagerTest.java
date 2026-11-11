package com.logvex.crystal.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.ai.model.InferenceException;
import com.logvex.crystal.ai.model.MiniAIModel;
import com.logvex.crystal.ai.model.ModelCatalog;
import com.logvex.crystal.ai.model.ModelSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The model lifecycle: lazy load, single copy, unload, dedup, cancellation and fallback. */
class MiniAIManagerTest {

    @TempDir
    Path dir;

    private static final class FakeModel implements MiniAIModel {
        volatile boolean loaded = true;
        final AtomicInteger calls = new AtomicInteger();
        volatile String reply = "a helpful answer";
        /** When set, {@link #complete} blocks until it is counted down, to keep a request in flight. */
        volatile CountDownLatch gate;

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public long loadedBytes() {
            return 123_456L;
        }

        @Override
        public String complete(String prompt, int maxTokens, float temperature) throws InferenceException {
            calls.incrementAndGet();
            CountDownLatch open = gate;
            if (open != null) {
                try {
                    open.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return reply;
        }

        @Override
        public void unload() {
            loaded = false;
        }
    }

    /** A tiny stand-in spec so isInstalled() size checks pass with a small test file. */
    private ModelSpec tinySpec() {
        return new ModelSpec("tiny", "Tiny", "", "http://127.0.0.1/x", "tiny.gguf", 4096, "",
                "Apache-2.0", "Q4_K_M", "0.01B", 512);
    }

    private MiniAIManager manager(MiniAIManager.Loader loader) {
        return new MiniAIManager(dir, settings(), loader, this::tinySpec);
    }

    private void installTiny() throws IOException {
        ModelSpec spec = tinySpec();
        byte[] bytes = new byte[8192];
        bytes[0] = 'G'; bytes[1] = 'G'; bytes[2] = 'U'; bytes[3] = 'F';
        Files.write(dir.resolve(spec.fileName()), bytes);
    }

    private AISettings settings() {
        AISettings settings = new AISettings();
        settings.enabled = true;
        settings.localAnalysis = true;
        return settings;
    }

    @Test
    void doesNotLoadWhenDisabled() {
        AISettings settings = settings();
        settings.enabled = false;
        MiniAIManager manager = new MiniAIManager(dir, settings, (spec, threads) -> new FakeModel());
        manager.ensureLoaded();
        assertEquals(MiniAIManager.State.DISABLED, manager.state());
        assertFalse(manager.isLoaded());
        manager.close();
    }

    @Test
    void reportsNotInstalledWithoutFile() {
        MiniAIManager manager = new MiniAIManager(dir, settings(), (spec, threads) -> new FakeModel());
        manager.ensureLoaded();
        assertEquals(MiniAIManager.State.NOT_INSTALLED, manager.state());
        manager.close();
    }

    @Test
    void loadsLazilyThenServesInference() throws Exception {
        installTiny();
        AtomicInteger loads = new AtomicInteger();
        MiniAIManager manager = manager((spec, threads) -> {
            loads.incrementAndGet();
            return new FakeModel();
        });
        assertEquals(MiniAIManager.State.IDLE, manager.state(), "no load until requested");

        manager.ensureLoaded();
        waitFor(() -> manager.state() == MiniAIManager.State.READY);
        assertTrue(manager.isLoaded());
        assertEquals(1, loads.get(), "exactly one load");

        AtomicReference<String> answer = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        manager.submitOnce("crash-1", "explain this", text -> {
            answer.set(text);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals("a helpful answer", answer.get());
        manager.close();
    }

    @Test
    void identicalRequestsAreDeduplicated() throws Exception {
        installTiny();
        FakeModel model = new FakeModel();
        MiniAIManager manager = manager((spec, threads) -> model);
        // Block the worker so the first request stays in flight while we submit a duplicate.
        CountDownLatch gate = new CountDownLatch(1);
        model.gate = gate;
        manager.ensureLoaded();
        waitFor(() -> manager.state() == MiniAIManager.State.READY);

        Future<?> first = manager.submitOnce("same", "prompt", text -> { });
        Future<?> duplicate = manager.submitOnce("same", "prompt", text -> { });
        assertNotNull(first);
        assertNull(duplicate, "the identical request must be dropped");
        gate.countDown();
        manager.close();
    }

    @Test
    void initializationFailureFallsBackCleanly() {
        try {
            installTiny();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MiniAIManager manager = manager((spec, threads) -> {
            throw new InferenceException("No local inference runtime available");
        });
        manager.ensureLoaded();
        waitFor(() -> manager.state() == MiniAIManager.State.RUNTIME_UNAVAILABLE);
        assertFalse(manager.isLoaded());
        // A submission while unavailable must not throw; it just returns null.
        assertNull(manager.submitOnce("k", "p", text -> { }));
        manager.close();
    }

    @Test
    void unloadReleasesTheModel() throws Exception {
        installTiny();
        FakeModel model = new FakeModel();
        MiniAIManager manager = manager((spec, threads) -> model);
        manager.ensureLoaded();
        waitFor(() -> manager.state() == MiniAIManager.State.READY);
        manager.unload();
        waitFor(() -> !model.loaded);
        assertFalse(manager.isLoaded());
        assertEquals(MiniAIManager.State.IDLE, manager.state());
        manager.close();
    }

    @Test
    void idleTimeoutUnloadsAfterPeriod() throws Exception {
        installTiny();
        FakeModel model = new FakeModel();
        AISettings settings = settings();
        settings.idleUnloadMinutes = 1;
        MiniAIManager manager = new MiniAIManager(dir, settings, (spec, threads) -> model, this::tinySpec);
        manager.ensureLoaded();
        waitFor(() -> manager.state() == MiniAIManager.State.READY);
        // Simulate a use in the past, then tick well beyond the idle window. The result callback
        // fires from inside the worker, so the executor can still look busy for a moment after the
        // latch opens; production ticks every frame, so mirror that by ticking inside the wait.
        CountDownLatch latch = new CountDownLatch(1);
        manager.submitOnce("k", "p", text -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
        long future = System.currentTimeMillis() + 10 * 60_000L;
        waitFor(() -> {
            manager.tick(future);
            return !model.loaded;
        });
        assertFalse(manager.isLoaded());
        manager.close();
    }

    @Test
    void queueIsBounded() throws Exception {
        installTiny();
        MiniAIManager manager = manager((spec, threads) -> new FakeModel());
        assertTrue(manager.queueLimit() <= AISettings.MAX_QUEUE);
        assertTrue(manager.queueLimit() >= AISettings.MIN_QUEUE);
        manager.close();
    }

    @Test
    void disabledManagerReportsZeroQueue() {
        AISettings settings = settings();
        settings.enabled = false;
        MiniAIManager manager = new MiniAIManager(dir, settings, (spec, threads) -> new FakeModel());
        assertEquals(0, manager.queueLimit());
        manager.close();
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
