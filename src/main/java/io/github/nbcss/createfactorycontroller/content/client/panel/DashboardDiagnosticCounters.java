package io.github.nbcss.createfactorycontroller.content.client.panel;

/** Plain counters: observations only, never used to decide rendering or synchronization. */
public final class DashboardDiagnosticCounters {
    private long visibilityChecks, allowedChecks, renderStarted, renderCompleted, requests, snapshots, deltas, unavailable;
    public record Snapshot(long visibilityChecks, long allowedChecks, long renderStarted, long renderCompleted,
                           long requests, long snapshots, long deltas, long unavailable) {}
    public void visibility(boolean allowed) { visibilityChecks++; if (allowed) allowedChecks++; }
    public void renderStarted() { renderStarted++; }
    public void renderCompleted() { renderCompleted++; }
    public void request() { requests++; }
    public void snapshot() { snapshots++; }
    public void delta() { deltas++; }
    public void unavailable() { unavailable++; }
    public Snapshot drain() {
        var result = new Snapshot(visibilityChecks, allowedChecks, renderStarted, renderCompleted, requests, snapshots, deltas, unavailable);
        visibilityChecks = allowedChecks = renderStarted = renderCompleted = requests = snapshots = deltas = unavailable = 0;
        return result;
    }
}
