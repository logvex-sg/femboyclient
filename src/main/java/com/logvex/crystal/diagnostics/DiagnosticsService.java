package com.logvex.crystal.diagnostics;

import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.PerformanceSample;
import com.logvex.crystal.logging.ModuleErrorLog;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers the client's runtime state into a {@link DiagnosticsReport} and can export it.
 *
 * <p>It owns the two pieces the report needs that no other service keeps: a registry of
 * named cache counters (so a cache can advertise its size for the diagnostics page) and a
 * debug-mode flag that gates verbose collection. Everything else is read from the services
 * it is handed, so this class holds no duplicated state and cannot drift from the live
 * client.
 *
 * <p>Collection is cheap: it walks the module list once and copies the error ring. It is
 * expected to run at most once a second (the caller throttles it), never per frame.
 */
public final class DiagnosticsService {

    private final ModuleManager modules;
    private final ModuleErrorLog errors;

    /** Named counters a subsystem can publish, e.g. "waypoints" → 42. */
    private final Map<String, Long> caches = new LinkedHashMap<>();
    private final Map<String, String> environment = new LinkedHashMap<>();

    private boolean debugMode;
    private long lastBuildMillis;
    private DiagnosticsReport cached;
    private long reportCount;
    private RenderStats renderStats;

    public DiagnosticsService(ModuleManager modules, ModuleErrorLog errors) {
        this.modules = modules;
        this.errors = errors;
    }

    /** Records a named counter (last write wins). Values below zero are stored as zero. */
    public void publishCache(String name, long size) {
        if (name != null) {
            caches.put(name, Math.max(0L, size));
        }
    }

    /** Records a named environment fact (version, game version, loader). */
    public void publishEnvironment(String key, String value) {
        if (key != null) {
            environment.put(key, value == null ? "" : value);
        }
    }

    public Map<String, Long> caches() {
        return Map.copyOf(caches);
    }

    /**
     * Attaches the world-render timings, so the report can show how much of a frame the
     * Crystal ESP pass costs. Optional: a report built without it simply omits the section.
     */
    public void setRenderStats(RenderStats renderStats) {
        this.renderStats = renderStats;
    }

    public RenderStats renderStats() {
        return renderStats;
    }

    /** Whether verbose diagnostics collection is on. */
    public boolean debugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean value) {
        this.debugMode = value;
    }

    public boolean toggleDebugMode() {
        this.debugMode = !debugMode;
        return debugMode;
    }

    /** How many reports have been built this session. */
    public long reportCount() {
        return reportCount;
    }

    /**
     * Builds a report from the given performance verdict. Cheap enough to call on demand;
     * the caller should not call it per frame.
     */
    public DiagnosticsReport build(PerformanceReport performance) {
        lastBuildMillis = System.currentTimeMillis();
        reportCount++;
        List<DiagnosticsReport.ModuleHealth> health = new ArrayList<>();
        for (Module module : modules.all()) {
            health.add(new DiagnosticsReport.ModuleHealth(module.name(), module.isEnabled(),
                    module.errorCount(), module.lastTickNanos() / 1_000_000.0, module.lastError()));
        }
        List<DiagnosticsReport.ErrorLine> errorLines = new ArrayList<>();
        for (ModuleErrorLog.Entry entry : errors.entries()) {
            errorLines.add(new DiagnosticsReport.ErrorLine(entry.module(), entry.message(), entry.atMillis()));
        }
        return new DiagnosticsReport(performance, health, errorLines,
                new LinkedHashMap<>(environment), new LinkedHashMap<>(caches), List.of(),
                renderLines());
    }

    /** The world-render section of the report, or an empty list when nothing was measured. */
    private List<String> renderLines() {
        if (renderStats == null || renderStats.sampleCount() == 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format("  world esp collect: %.2fms avg", renderStats.avgCollectMillis()));
        lines.add(String.format("  world esp draw: %.2fms avg", renderStats.avgRenderMillis()));
        lines.add("  last pass: " + renderStats.lastBoxes() + " box(es), "
                + renderStats.lastLabels() + " label(s)");
        lines.add("  window: " + renderStats.sampleCount() + " frame(s)");
        return lines;
    }

    /** Builds and caches a report, returning the cached one when it is younger than a second. */
    public DiagnosticsReport buildThrottled(PerformanceReport performance) {
        long now = System.currentTimeMillis();
        if (cached != null && now - lastBuildMillis < 1000L) {
            return cached;
        }
        cached = build(performance);
        return cached;
    }

    /** Writes the report to {@code file} atomically (temp file then move). */
    public void export(DiagnosticsReport report, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, report.text(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Exposed for tests: when the last report was built. */
    public long lastBuildMillis() {
        return lastBuildMillis;
    }
}
