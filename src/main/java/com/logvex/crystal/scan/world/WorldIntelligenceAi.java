package com.logvex.crystal.scan.world;

import java.util.function.Consumer;

/**
 * The AI layer for world intelligence: local-first, optional, and bounded.
 *
 * <p>Two tiers answer a question, in order:
 * <ol>
 *   <li><b>Deterministic local tier.</b> {@link WorldQuery} parses the question and answers it
 *       from the recorded data. This always works, needs no model, and is what makes the layer
 *       usable on a machine with no AI provider configured.</li>
 *   <li><b>Optional model tier.</b> When an {@link AiTextBackend} is installed and available, the
 *       same question is sent to it with the relevant recorded facts as context, and its answer is
 *       returned instead. A failure, timeout or rejection falls straight back to the local
 *       answer.</li>
 * </ol>
 *
 * <p>The model tier is never trusted to produce data. It is given a factual summary produced from
 * the database and asked to phrase it, and the deterministic answer is always computed too, so a
 * model can never invent a discovery: if it returns nothing useful the user still gets the true,
 * data-derived answer. The summary the model is shown is labelled so it cannot mistake a guess for
 * a finding.
 */
public final class WorldIntelligenceAi {

    /** Most recent questions kept for the AI workspace's history pane. */
    public static final int HISTORY_CAP = 24;

    /** One question/answer exchange. */
    public record Exchange(String question, String answer, boolean fromModel, long atMillis) {
    }

    private final AiTextBackend backend;
    private final WorldQuery local = new WorldQuery();
    private final java.util.ArrayDeque<Exchange> history = new java.util.ArrayDeque<>();
    private boolean modelAllowed = true;

    public WorldIntelligenceAi(AiTextBackend backend) {
        this.backend = backend;
    }

    /**
     * Enables or disables the optional model tier. When disabled, every answer comes from the
     * deterministic tier even if a model is loaded - the user's choice is honoured immediately.
     */
    public void setModelAllowed(boolean allowed) {
        this.modelAllowed = allowed;
    }

    /** True when a model is available and permitted; the deterministic tier works regardless. */
    public boolean modelAvailable() {
        return modelAllowed && backend != null && backend.available();
    }

    /** A description of the active backend, for the dashboard. */
    public String backendDescription() {
        return backend == null ? "deterministic (no model configured)" : backend.describe();
    }

    /**
     * Answers a question, preferring the model when one is available. The callback is invoked once,
     * on the backend's thread for a model answer and on the calling thread for the local answer.
     */
    public void ask(String question, WorldQuery.Context context, Consumer<WorldQuery.Answer> callback) {
        WorldQuery.Answer fallback = local.ask(question, context);
        if (!modelAvailable()) {
            record(fallback, false);
            callback.accept(fallback);
            return;
        }
        String prompt = buildPrompt(question, context, fallback);
        String key = "wi:" + question.hashCode() + ":" + System.nanoTime();
        backend.submit(key, prompt, modelAnswer -> {
            WorldQuery.Answer answer;
            if (modelAnswer == null || modelAnswer.isBlank()) {
                // The model failed or returned nothing: the deterministic answer stands.
                answer = fallback;
            } else {
                answer = new WorldQuery.Answer(question, "ai", modelAnswer.trim(),
                        fallback.observationIds(), true);
            }
            record(answer, modelAnswer != null && !modelAnswer.isBlank());
            callback.accept(answer);
        });
    }

    /** Answers a question deterministically, with no model involvement (used by tests and the CLI). */
    public WorldQuery.Answer askLocal(String question, WorldQuery.Context context) {
        return local.ask(question, context);
    }

    /**
     * Builds the prompt shown to the model. The facts come from the deterministic answer, so the
     * model is rephrasing real data rather than free-associating, and the instruction forbids it
     * from adding anything that is not in the facts.
     */
    static String buildPrompt(String question, WorldQuery.Context context, WorldQuery.Answer facts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the world-intelligence assistant for a Minecraft client.\n");
        prompt.append("Answer ONLY from the facts below. Do not invent discoveries, coordinates or "
                + "classifications. If the facts do not answer the question, say so plainly.\n\n");
        prompt.append("Current dimension: ")
                .append(WorldQuery.shortDimension(context.dimension())).append('\n');
        prompt.append("Recorded facts:\n").append(facts.text()).append("\n\n");
        prompt.append("Question: ").append(question).append('\n');
        prompt.append("Answer briefly and plainly.");
        return prompt.toString();
    }

    private void record(WorldQuery.Answer answer, boolean fromModel) {
        history.addFirst(new Exchange(answer.question(), answer.text(), fromModel,
                System.currentTimeMillis()));
        while (history.size() > HISTORY_CAP) {
            history.removeLast();
        }
    }

    /** The most recent exchanges, newest first. */
    public java.util.List<Exchange> history() {
        return java.util.List.copyOf(history);
    }

    /** Clears the history pane. */
    public void clearHistory() {
        history.clear();
    }
}
