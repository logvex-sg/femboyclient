package com.logvex.crystal.commandcenter.assistant;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusSection;
import java.util.ArrayList;
import java.util.List;

/**
 * The deterministic half of the AI assistant workspace: a rule-based diagnosis that works with
 * no model present.
 *
 * <p>The Command Center's AI workspace must be useful to a user who has never downloaded a
 * model. This class answers "what is wrong and what should I do" from the status model and the
 * performance report alone, using the same kind of explicit, threshold-based rules the
 * performance intelligence uses. It is deliberately not clever: it groups the problems that
 * need attention by section, ranks them by severity, and attaches a concrete suggestion to
 * each known condition. When the optional local model <em>is</em> available the workspace can
 * additionally ask it to explain this same evidence in prose, but the fallback is complete on
 * its own.
 *
 * <p>Pure and Minecraft-independent, so the exact output is unit testable.
 */
public final class DeterministicDiagnostics {

    /** One ranked finding: a problem, the section it came from, and what to do. */
    public record Finding(String section, HealthStatus severity, String problem, String suggestion) {
        public String describe() {
            return problem + (suggestion.isBlank() ? "" : " \u2014 " + suggestion);
        }
    }

    private DeterministicDiagnostics() {
    }

    /** The ranked findings across the model and the performance report, most severe first. */
    public static List<Finding> analyse(ClientStatusModel model, PerformanceReport performance) {
        List<Finding> findings = new ArrayList<>();
        if (model != null) {
            for (StatusSection section : model.sections()) {
                for (StatusEntry entry : section.problems()) {
                    findings.add(new Finding(section.title(), entry.status(), entry.describe(),
                            suggestionFor(section.id(), entry)));
                }
            }
        }
        if (performance != null) {
            performance.findings().forEach(f -> findings.add(new Finding("Performance", f.severity(),
                    f.observation(), f.suggestion())));
        }
        findings.sort((a, b) -> Integer.compare(b.severity().ordinal(), a.severity().ordinal()));
        return findings;
    }

    /** A one-paragraph plain-language summary, used as the workspace's headline and as the model prompt. */
    public static String summarise(ClientStatusModel model, PerformanceReport performance) {
        if (model == null) {
            return "No status has been captured yet.";
        }
        if (model.healthy() && (performance == null || performance.clean())) {
            return "Everything looks healthy: all " + model.sections().size()
                    + " subsystems report OK and the performance intelligence has no findings.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(model.summary()).append('.');
        List<Finding> findings = analyse(model, performance);
        if (!findings.isEmpty()) {
            sb.append(" Most important: ").append(findings.get(0).describe()).append('.');
        }
        if (performance != null && !performance.clean()) {
            sb.append(" Performance: ").append(performance.summary()).append('.');
        }
        return sb.toString();
    }

    /** A concrete suggestion for a known condition, or a generic one when nothing specific applies. */
    static String suggestionFor(String sectionId, StatusEntry entry) {
        String label = entry.label().toLowerCase(java.util.Locale.ROOT);
        String detail = entry.detail();
        if (sectionId == null) {
            sectionId = "";
        }
        if (sectionId.equals("modules") && label.contains("error")) {
            return "open the Modules workspace and review the failing module, or run ; errors";
        }
        if (sectionId.equals("modules") && label.contains("keybind")) {
            return "open the Keybinds screen and resolve the clash";
        }
        if (sectionId.equals("coordination")) {
            return "open the Modules workspace to see which modules are fighting";
        }
        if (sectionId.equals("performance") && label.contains("fps")) {
            return "disable heavy render modules before fighting";
        }
        if (sectionId.equals("ai") && label.contains("error")) {
            return "open the AI assistant workspace for the model state and queue";
        }
        if (sectionId.equals("baritone") && label.contains("last action")) {
            return "open the Baritone workspace to see the navigation history";
        }
        if (sectionId.equals("profiles") || sectionId.equals("config")) {
            return "open the Configuration workspace to save or reload";
        }
        return detail.isBlank() ? "" : detail;
    }
}
