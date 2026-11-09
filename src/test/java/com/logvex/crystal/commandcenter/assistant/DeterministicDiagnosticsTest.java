package com.logvex.crystal.commandcenter.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.commandcenter.ClientStatusModel;
import com.logvex.crystal.commandcenter.HealthStatus;
import com.logvex.crystal.commandcenter.PerformanceFinding;
import com.logvex.crystal.commandcenter.PerformanceReport;
import com.logvex.crystal.commandcenter.PerformanceSample;
import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusSection;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the deterministic diagnostics: ranking, suggestions and the plain-language summary. */
class DeterministicDiagnosticsTest {

    private static ClientStatusModel modelWithProblems() {
        return new ClientStatusModel(List.of(
                StatusSection.of("modules", "Modules",
                        StatusEntry.critical("Module error", "NPE", "Sprint failed")),
                StatusSection.of("performance", "Performance",
                        StatusEntry.attention("FPS", "18", "low"))), 1L);
    }

    @Test
    void healthyClientReportsCleanly() {
        ClientStatusModel healthy = new ClientStatusModel(List.of(
                StatusSection.of("modules", "Modules", StatusEntry.ok("A", "on"))), 1L);
        PerformanceReport clean = new PerformanceReport(PerformanceSample.empty(1L), List.of());
        assertTrue(DeterministicDiagnostics.analyse(healthy, clean).isEmpty());
        assertTrue(DeterministicDiagnostics.summarise(healthy, clean).contains("healthy"));
    }

    @Test
    void findingsAreRankedMostSevereFirst() {
        List<DeterministicDiagnostics.Finding> findings =
                DeterministicDiagnostics.analyse(modelWithProblems(), null);
        assertEquals(2, findings.size());
        assertEquals(HealthStatus.CRITICAL, findings.get(0).severity());
        assertEquals(HealthStatus.ATTENTION, findings.get(1).severity());
    }

    @Test
    void findingsCarryConcreteSuggestions() {
        List<DeterministicDiagnostics.Finding> findings =
                DeterministicDiagnostics.analyse(modelWithProblems(), null);
        assertTrue(findings.get(0).suggestion().toLowerCase().contains("module"));
        assertTrue(findings.get(1).suggestion().toLowerCase().contains("render")
                || findings.get(1).suggestion().toLowerCase().contains("heavy")
                || !findings.get(1).suggestion().isBlank());
    }

    @Test
    void performanceFindingsAreIncludedAndRanked() {
        PerformanceReport report = new PerformanceReport(PerformanceSample.empty(1L), List.of(
                new PerformanceFinding("fps", HealthStatus.ATTENTION, "fps dropped", "close modules", "fps")));
        List<DeterministicDiagnostics.Finding> findings =
                DeterministicDiagnostics.analyse(modelWithProblems(), report);
        assertEquals(3, findings.size());
        // The CRITICAL module error still outranks the ATTENTION performance finding.
        assertEquals(HealthStatus.CRITICAL, findings.get(0).severity());
        assertTrue(findings.stream().anyMatch(f -> f.section().equals("Performance")
                && f.describe().contains("fps dropped")));
    }

    @Test
    void summaryMentionsTheMostImportantProblem() {
        String summary = DeterministicDiagnostics.summarise(modelWithProblems(), null);
        assertFalse(summary.isBlank());
        assertTrue(summary.toLowerCase().contains("most important"));
    }

    @Test
    void nullInputsAreSafe() {
        assertTrue(DeterministicDiagnostics.analyse(null, null).isEmpty());
        assertFalse(DeterministicDiagnostics.summarise(null, null).isBlank());
    }
}
