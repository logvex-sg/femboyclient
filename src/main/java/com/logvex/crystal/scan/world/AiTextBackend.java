package com.logvex.crystal.scan.world;

import java.util.function.Consumer;

/**
 * The seam between the world-intelligence layer and whatever text model may be configured.
 *
 * <p>The layer must work with no model at all, so this is an <em>optional</em> backend: when none
 * is installed {@link WorldIntelligenceAi} answers deterministically from the database instead.
 * An implementation must never run inference on the Minecraft thread — the callback is invoked
 * from the backend's own worker, and callers are responsible for marshalling the result back.
 *
 * <p>Mirrors the shape of the client's {@code MiniAIManager} without depending on it, so the
 * layer stays testable with a plain fake.
 */
public interface AiTextBackend {

    /** True when a model is loaded and ready to answer. */
    boolean available();

    /** A short description of the backend for the dashboard ("local model", "none", ...). */
    String describe();

    /**
     * Submits a prompt for completion. {@code onResult} is called exactly once, with the model's
     * text or with {@code null} when the request failed, timed out or was rejected. Implementations
     * must not call it on the caller's thread if that thread is the client thread.
     *
     * @param key      a dedupe key so the same question is not submitted twice
     * @param prompt   the prompt to complete
     * @param onResult the completion callback
     */
    void submit(String key, String prompt, Consumer<String> onResult);
}
