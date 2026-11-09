package com.logvex.crystal.commandcenter.assistant;

import com.logvex.crystal.ai.MiniAIManager;
import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.PerformanceReport;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The service behind the AI assistant workspace: it produces the deterministic diagnosis, and
 * optionally asks the local model to explain the same evidence in prose.
 *
 * <p>It never runs inference on the game thread. {@link #ask} builds a bounded prompt and hands
 * it to {@link MiniAIManager}, whose worker thread calls back with the text; the result is
 * published into a volatile field the screen reads on the next frame. When no model is loaded
 * the workspace still works, because the deterministic half needs nothing.
 *
 * <p>The suppliers keep the service Minecraft-independent and unit testable: a test supplies a
 * fixed status model and a stub manager, and the production wiring supplies the live client.
 */
public final class AssistantService {

    /** What happened when the user asked a question. */
    public enum AskResult {
        SUBMITTED("Asking the local model\u2026"),
        NO_MODEL("No local model is loaded \u2014 showing the built-in diagnosis"),
        DISABLED("Local analysis is turned off in the AI settings"),
        BUSY("A request is already in flight");

        private final String message;

        AskResult(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        public boolean submitted() {
            return this == SUBMITTED;
        }
    }

    private final Supplier<ClientStatusModel> status;
    private final Supplier<PerformanceReport> performance;
    private final Supplier<MiniAIManager> ai;
    private final BooleanSupplier localAnalysisEnabled;

    private volatile String lastAnswer = "";
    private volatile String lastError = "";
    private volatile boolean pending;

    public AssistantService(Supplier<ClientStatusModel> status, Supplier<PerformanceReport> performance,
                            Supplier<MiniAIManager> ai, BooleanSupplier localAnalysisEnabled) {
        this.status = status == null ? () -> null : status;
        this.performance = performance == null ? () -> null : performance;
        this.ai = ai == null ? () -> null : ai;
        this.localAnalysisEnabled = localAnalysisEnabled == null ? () -> false : localAnalysisEnabled;
    }

    /** The ranked deterministic findings, always available. */
    public List<DeterministicDiagnostics.Finding> findings() {
        return DeterministicDiagnostics.analyse(status.get(), performance.get());
    }

    /** The plain-language deterministic summary, always available. */
    public String summary() {
        return DeterministicDiagnostics.summarise(status.get(), performance.get());
    }

    public String lastAnswer() {
        return lastAnswer;
    }

    public String lastError() {
        return lastError;
    }

    public boolean pending() {
        return pending;
    }

    public void clearAnswer() {
        lastAnswer = "";
        lastError = "";
    }

    /** The prompt the "diagnose" question would send, exposed so the UI can show exactly what is sent. */
    public String diagnosisPrompt() {
        return DiagnosticPrompts.diagnosis(status.get(), performance.get());
    }

    /** Asks the local model to explain the current status. Returns what happened. */
    public AskResult ask() {
        return ask(DiagnosticPrompts.diagnosis(status.get(), performance.get()), "diagnosis");
    }

    /** Asks the local model a specific question about a module. */
    public AskResult askAboutModule(String moduleName) {
        return ask(DiagnosticPrompts.moduleProblem(moduleName, status.get(), performance.get()),
                "module:" + moduleName);
    }

    /** Asks the local model why performance is degraded. */
    public AskResult askAboutPerformance() {
        return ask(DiagnosticPrompts.performance(status.get(), performance.get()), "performance");
    }

    private AskResult ask(String prompt, String key) {
        MiniAIManager manager = ai.get();
        if (manager == null || !localAnalysisEnabled.getAsBoolean()) {
            return manager == null ? AskResult.NO_MODEL : AskResult.DISABLED;
        }
        if (manager.state() != MiniAIManager.State.READY) {
            return AskResult.NO_MODEL;
        }
        pending = true;
        lastError = "";
        java.util.concurrent.Future<?> future = manager.submitOnce(key, prompt, text -> {
            pending = false;
            if (text == null || text.isBlank()) {
                lastError = "the model returned no text";
            } else {
                lastAnswer = text.trim();
            }
        });
        if (future == null) {
            pending = false;
            return AskResult.BUSY;
        }
        return AskResult.SUBMITTED;
    }
}
