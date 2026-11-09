package com.logvex.crystal.commandcenter.assistant;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.CommandCenterContext;
import com.logvex.crystal.commandcenter.PerformanceReport;

/**
 * Builds the bounded prompts the AI assistant workspace sends to the optional local model.
 *
 * <p>It reuses {@link CommandCenterContext} - the same privacy-safe, size-capped block the AI
 * subsystem already consumes - so the workspace cannot accidentally send more than the
 * established context allows. Each prompt pairs that evidence with one short, specific
 * question, because a 0.5B model stays coherent only when the task is narrow.
 *
 * <p>Pure and Minecraft-independent: the exact prompt text is unit testable and auditable, and
 * it contains no server address, seed or token because the underlying context does not.
 */
public final class DiagnosticPrompts {

    /** Keeps a prompt small so the tiny model answers quickly and on topic. */
    static final int MAX_PROMPT_CHARS = 2000;

    private DiagnosticPrompts() {
    }

    /** The "what is wrong and what should I do" prompt. */
    public static String diagnosis(ClientStatusModel model, PerformanceReport performance) {
        return assemble("Explain what is wrong with my Minecraft client and give the three most "
                + "useful things I should do, in order. Be concise and concrete.", model, performance);
    }

    /** The "why is performance bad" prompt. */
    public static String performance(ClientStatusModel model, PerformanceReport performance) {
        return assemble("My client may be lagging. Explain the most likely cause of the frame-rate "
                + "or tick problem and one concrete setting to change.", model, performance);
    }

    /** The "explain this module problem" prompt. */
    public static String moduleProblem(String moduleName, ClientStatusModel model,
                                       PerformanceReport performance) {
        return assemble("Why might the module '" + moduleName + "' not work, and how do I fix it?",
                model, performance);
    }

    private static String assemble(String question, ClientStatusModel model, PerformanceReport performance) {
        String context = CommandCenterContext.build(model, performance);
        String prompt = context + "\nQuestion: " + question + "\nAnswer:";
        if (prompt.length() <= MAX_PROMPT_CHARS) {
            return prompt;
        }
        return prompt.substring(0, MAX_PROMPT_CHARS);
    }
}
