package com.logvex.crystal.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceIntelligence;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.PerformanceSample;
import com.logvex.crystal.logging.ModuleErrorLog;
import com.logvex.crystal.module.Category;
import com.logvex.crystal.module.Module;
import com.logvex.crystal.module.ModuleManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The diagnostics report and its gathering service. */
class DiagnosticsServiceTest {

    private static class StubModule extends Module {
        StubModule(String name) {
            super(Category.MISC, name, "");
        }

        void fail(String message) {
            reportError(message, new RuntimeException(message));
        }
    }

    private static PerformanceReport healthy(long now) {
        PerformanceSample sample = new PerformanceSample(120, 8.0, 10.0, 1.0, "", 0.2, 1.0,
                3, 0, 0, 0, 0, now);
        return new PerformanceIntelligence().analyse(sample);
    }

    private static PerformanceReport unhealthy(long now) {
        PerformanceSample sample = new PerformanceSample(20, 55.0, 80.0, 20.0, "AutoCrystal", 6.0,
                9.0, 3, 4, 2, 5, 3, now);
        return new PerformanceIntelligence().analyse(sample);
    }

    @Test
    void reportIncludesEverySection() {
        ModuleManager modules = new ModuleManager();
        modules.register(new StubModule("AutoCrystal"));
        ModuleErrorLog errors = new ModuleErrorLog(16);
        DiagnosticsService service = new DiagnosticsService(modules, errors);
        service.publishEnvironment("minecraft", "1.21.11");
        service.publishCache("waypoints", 42L);

        DiagnosticsReport report = service.build(healthy(System.currentTimeMillis()));
        String text = report.text();
        assertTrue(text.contains("[environment]"));
        assertTrue(text.contains("minecraft: 1.21.11"));
        assertTrue(text.contains("[performance]"));
        assertTrue(text.contains("[modules]"));
        assertTrue(text.contains("[caches]"));
        assertTrue(text.contains("waypoints: 42"));
        assertTrue(text.contains("[config]"));
        assertTrue(text.contains("[recent errors]"));
    }

    @Test
    void aHealthyClientReportsNoFindings() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        DiagnosticsReport report = service.build(healthy(System.currentTimeMillis()));
        assertTrue(report.text().contains("no module failures"));
        assertTrue(report.summary().contains("diag ok"));
    }

    @Test
    void anUnhealthyClientSurfacesFindings() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        DiagnosticsReport report = service.build(unhealthy(System.currentTimeMillis()));
        assertTrue(report.text().contains("low-fps-critical"));
        assertTrue(report.summary().contains("critical"));
    }

    @Test
    void moduleFailuresAreListed() {
        ModuleManager modules = new ModuleManager();
        StubModule module = new StubModule("Broken");
        modules.register(module);
        ModuleErrorLog errors = new ModuleErrorLog(8);
        errors.record("Broken", "boom");
        module.fail("boom");

        DiagnosticsService service = new DiagnosticsService(modules, errors);
        DiagnosticsReport report = service.build(healthy(System.currentTimeMillis()));
        assertTrue(report.text().contains("Broken \u00d71"));
    }

    @Test
    void exportWritesTheDocumentAtomically(@TempDir Path dir) throws IOException {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        DiagnosticsReport report = service.build(healthy(System.currentTimeMillis()));
        Path file = dir.resolve("diag/diagnostics.txt");
        service.export(report, file);
        assertTrue(Files.exists(file));
        String written = Files.readString(file);
        assertEquals(report.text(), written);
        assertFalse(Files.exists(file.resolveSibling("diagnostics.txt.tmp")));
    }

    @Test
    void buildIsThrottledToOnePerSecond() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        var first = service.buildThrottled(healthy(1_000));
        var second = service.buildThrottled(healthy(1_000));
        assertEquals(1, service.reportCount());
        assertTrue(first == second);
    }

    @Test
    void debugModeToggles() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        assertFalse(service.debugMode());
        assertTrue(service.toggleDebugMode());
        assertTrue(service.debugMode());
        service.setDebugMode(false);
        assertFalse(service.debugMode());
    }

    @Test
    void cachesAreBoundedToNonNegative() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        service.publishCache("bad", -5);
        assertEquals(0L, service.caches().get("bad"));
    }

    @Test
    void reportHandlesMissingPerformance() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        DiagnosticsReport report = service.build(null);
        assertTrue(report.text().contains("no frame data yet"));
    }

    @Test
    void moduleHealthCarriesTickAndErrorCounts() {
        ModuleManager modules = new ModuleManager();
        StubModule module = new StubModule("AutoCrystal");
        modules.register(module);
        module.enable(modules);
        module.tickSafely();
        DiagnosticsService service = new DiagnosticsService(modules, new ModuleErrorLog(4));
        DiagnosticsReport report = service.build(healthy(System.currentTimeMillis()));
        assertTrue(report.text().contains("1 enabled"));
    }

    @Test
    void severityRollsUpToTheWorstFinding() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        DiagnosticsReport report = service.build(unhealthy(System.currentTimeMillis()));
        assertEquals(HealthStatus.CRITICAL, report.lines() == null ? null
                : worstSeverity(report));
    }

    private static HealthStatus worstSeverity(DiagnosticsReport report) {
        HealthStatus worst = HealthStatus.OK;
        for (String line : report.lines()) {
            for (HealthStatus status : HealthStatus.values()) {
                if (line.contains("[" + status.name() + "]")) {
                    worst = worst.worseOf(status);
                }
            }
        }
        return worst;
    }

    @Test
    void environmentMapIsCopiedNotShared() {
        DiagnosticsService service = new DiagnosticsService(new ModuleManager(), new ModuleErrorLog(4));
        service.publishEnvironment("minecraft", "1.21.11");
        DiagnosticsReport report = service.build(healthy(System.currentTimeMillis()));
        List<String> lines = report.lines();
        assertTrue(lines.stream().anyMatch(l -> l.contains("minecraft: 1.21.11")));
        // A later mutation must not change an already-built report.
        service.publishEnvironment("minecraft", "changed");
        assertTrue(lines.stream().anyMatch(l -> l.contains("minecraft: 1.21.11")));
    }

    @Test
    void emptyReportHasNoCrash() {
        DiagnosticsReport report = new DiagnosticsReport(null, null, null, null, null, null);
        assertTrue(report.text().contains("LOGVEX CRYSTAL diagnostics"));
        assertFalse(report.text().isBlank());
        assertTrue(report.summary().contains("0 modules"));
    }

    @Test
    void cachesMapIsDefensive() {
        DiagnosticsReport report = new DiagnosticsReport(healthy(1), List.of(), List.of(),
                Map.of(), Map.of(), List.of());
        assertTrue(report.text().contains("(none registered)"));
    }
}
