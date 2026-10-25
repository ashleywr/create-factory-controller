package io.github.nbcss.createfactorycontroller.content.client.panel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardDiagnosticCountersTest {
    @Test void distinguishesVisibilityFromCompletedRendering() {
        var counters = new DashboardDiagnosticCounters();
        counters.visibility(false); counters.visibility(true); counters.visibility(true);
        counters.renderStarted(); counters.renderStarted(); counters.renderCompleted();
        assertEquals(new DashboardDiagnosticCounters.Snapshot(3, 2, 2, 1, 0, 0, 0, 0), counters.drain());
    }
    @Test void drainsWithoutLeakingCountsIntoNextWindow() {
        var counters = new DashboardDiagnosticCounters();
        counters.request(); counters.snapshot(); counters.delta(); counters.unavailable();
        assertEquals(new DashboardDiagnosticCounters.Snapshot(0, 0, 0, 0, 1, 1, 1, 1), counters.drain());
        assertEquals(new DashboardDiagnosticCounters.Snapshot(0, 0, 0, 0, 0, 0, 0, 0), counters.drain());
    }
}
