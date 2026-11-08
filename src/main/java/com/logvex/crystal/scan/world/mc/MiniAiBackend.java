package com.logvex.crystal.scan.world.mc;

import com.logvex.crystal.ai.MiniAIManager;
import com.logvex.crystal.scan.world.AiTextBackend;
import java.util.function.Consumer;

/**
 * The {@link AiTextBackend} the world-intelligence layer uses in game: a thin adapter over the
 * client's existing local {@link MiniAIManager}.
 *
 * <p>It adds no model of its own and performs no inference itself - it forwards a prompt to the
 * manager, which already runs completions on its own low-priority worker. This is the only place
 * the two subsystems meet, so the intelligence layer stays Minecraft-free and fully testable with
 * a fake backend.
 *
 * <p>When the manager is absent, disabled or the model is not installed, {@link #available()}
 * returns false and the intelligence layer uses its deterministic {@code WorldQuery} tier. A
 * request that the manager cannot schedule calls back with {@code null} immediately, which the AI
 * layer treats as "no model answer" and falls back from.
 */
public final class MiniAiBackend implements AiTextBackend {

    private final MiniAIManager manager;

    public MiniAiBackend(MiniAIManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean available() {
        return manager != null && manager.isLoaded();
    }

    @Override
    public String describe() {
        if (manager == null) {
            return "deterministic (no model configured)";
        }
        if (manager.isLoaded()) {
            return "local model ready";
        }
        return "local model " + manager.state().label().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void submit(String key, String prompt, Consumer<String> onResult) {
        if (manager == null) {
            onResult.accept(null);
            return;
        }
        var future = manager.submitOnce(key, prompt, onResult);
        if (future == null) {
            // The manager could not schedule the request (disabled, not ready, queue full).
            onResult.accept(null);
        }
    }
}
