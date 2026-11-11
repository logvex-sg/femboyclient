package com.logvex.crystal.diagnostics;

import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceFinding;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.PerformanceSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds a plain-text diagnostic report from the numbers the client already measures.
 *
 * <p>It is a pure formatter: the caller gathers the sample, the module health rows, the
 * recent errors, the cache counters and the config-file status, and this class lays them out
 * as a stable, greppable document. That keeps the report reproducible (a test can assert on
 * exact output) and keeps the client-side gathering out of the formatting.
 *
 * <p>The report deliberately contains nothing sensitive: no server address, no world seed,
 * no tokens - only the client's own counters and module names.
 */
public final class DiagnosticsReport {

    /** One module's health row. */
    public record ModuleHealth(String name, boolean enabled, int errorCount, double tickMillis,
                               String lastError) {
    }

    /** A recent error line. */
    public record ErrorLine(String module, String message, long atMillis) {
    }

    private final PerformanceReport performance;
    private final List<ModuleHealth> modules;
    private final List<ErrorLine> errors;
    private final Map<String, String> environment;
    private final Map<String, Long> caches;
    private final List<String> configLines;
    private final List<String> renderLines;

    public DiagnosticsReport(PerformanceReport performance, List<ModuleHealth> modules,
                             List<ErrorLine> errors, Map<String, String> environment,
                             Map<String, Long> caches, List<String> configLines) {
        this(performance, modules, errors, environment, caches, configLines, List.of());
    }

    public DiagnosticsReport(PerformanceReport performance, List<ModuleHealth> modules,
                             List<ErrorLine> errors, Map<String, String> environment,
                             Map<String, Long> caches, List<String> configLines,
                             List<String> renderLines) {
        this.performance = performance;
        this.modules = modules == null ? List.of() : modules;
        this.errors = errors == null ? List.of() : errors;
        this.environment = environment == null ? Map.of() : environment;
        this.caches = caches == null ? Map.of() : caches;
        this.configLines = configLines == null ? List.of() : configLines;
        this.renderLines = renderLines == null ? List.of() : renderLines;
    }

    /** The full report as a list of lines. */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("LOGVEX CRYSTAL diagnostics");
        out.add("========================");
        out.add("");
        out.add("[environment]");
        environment.forEach((key, value) -> out.add("  " + key + ": " + value));
        out.add("");
        out.add("[performance]");
        PerformanceSample sample = performance == null ? null : performance.sample();
        if (sample == null || !sample.hasFrameData()) {
            out.add("  no frame data yet");
        } else {
            out.add(String.format("  fps: %d", sample.fps()));
            out.add(String.format("  frame: %.2fms avg, %.2fms p95, %.2fms jitter",
                    sample.avgFrameMillis(), sample.p95FrameMillis(), sample.jitterMillis()));
        }
        if (sample != null) {
            out.add("  modules enabled: " + sample.activeModules());
            if (!sample.worstModuleName().isBlank()) {
                out.add(String.format("  worst module tick: %s %.2fms",
                        sample.worstModuleName(), sample.worstModuleTickMillis()));
            }
            out.add("  ai queue: " + sample.aiQueueDepth() + " (" + sample.aiRejected() + " dropped)");
            out.add("  event backlog: " + sample.eventBacklog());
            out.add("  actions in flight: " + sample.activeActions());
        }
        if (performance != null && !performance.findings().isEmpty()) {
            out.add("  findings:");
            for (PerformanceFinding finding : performance.findings()) {
                out.add("    [" + finding.severity().name() + "] " + finding.rule()
                        + ": " + finding.describe());
            }
        }
        if (!renderLines.isEmpty()) {
            out.add("");
            out.add("[render]");
            out.addAll(renderLines);
        }
        out.add("");
        out.add("[modules] " + modules.size() + " tracked, "
                + modules.stream().filter(ModuleHealth::enabled).count() + " enabled");
        List<ModuleHealth> unhealthy = new ArrayList<>();
        for (ModuleHealth module : modules) {
            if (module.errorCount() > 0) {
                unhealthy.add(module);
            }
        }
        if (unhealthy.isEmpty()) {
            out.add("  no module failures \u2661");
        } else {
            unhealthy.sort((a, b) -> Integer.compare(b.errorCount(), a.errorCount()));
            for (ModuleHealth module : unhealthy) {
                out.add("  " + module.name() + " \u00d7" + module.errorCount()
                        + (module.lastError() == null || module.lastError().isBlank()
                        ? "" : " \u2014 " + module.lastError()));
            }
        }
        out.add("");
        out.add("[caches]");
        if (caches.isEmpty()) {
            out.add("  (none registered)");
        } else {
            caches.forEach((key, value) -> out.add("  " + key + ": " + value));
        }
        out.add("");
        out.add("[config]");
        if (configLines.isEmpty()) {
            out.add("  (none)");
        } else {
            configLines.forEach(line -> out.add("  " + line));
        }
        out.add("");
        out.add("[recent errors] " + errors.size());
        int shown = 0;
        for (ErrorLine error : errors) {
            if (shown++ >= 20) {
                out.add("  \u2026 " + (errors.size() - 20) + " more");
                break;
            }
            out.add("  " + error.module() + ": " + error.message());
        }
        return out;
    }

    /** True when the performance intelligence found nothing to report. */
    public boolean findingsAreClean() {
        return performance == null || performance.findings().isEmpty();
    }

    /** The report as one newline-joined string, for export to a file. */
    public String text() {
        return String.join("\n", lines()) + "\n";
    }

    /** A one-line roll-up for the HUD and the command surface. */
    public String summary() {
        PerformanceSample sample = performance == null ? null : performance.sample();
        HealthStatus status = performance == null ? HealthStatus.OK : performance.status();
        String fps = sample != null && sample.hasFrameData() ? sample.fps() + " fps" : "no fps";
        long failing = modules.stream().filter(m -> m.errorCount() > 0).count();
        return "diag " + status.name().toLowerCase(java.util.Locale.ROOT) + " \u00b7 " + fps
                + " \u00b7 " + modules.size() + " modules \u00b7 " + failing + " failing"
                + " \u00b7 " + errors.size() + " error(s)";
    }
}
