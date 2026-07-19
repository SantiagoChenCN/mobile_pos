package com.espsa.mobilepos.app.sync;

/** Immutable, small coordinator state. Persistence is intentionally limited to these four fields. */
public final class ComputerSyncState {
    private final int consecutiveFailures;
    private final String lastCheckAtUtc;
    private final String lastSuccessAtUtc;
    private final String snapshotId;

    ComputerSyncState(int consecutiveFailures, String lastCheckAtUtc, String lastSuccessAtUtc, String snapshotId) {
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("Failure count cannot be negative");
        }
        this.consecutiveFailures = consecutiveFailures;
        this.lastCheckAtUtc = clean(lastCheckAtUtc);
        this.lastSuccessAtUtc = clean(lastSuccessAtUtc);
        this.snapshotId = clean(snapshotId);
    }

    static ComputerSyncState fromConfig(ComputerSyncConfig config) {
        ComputerSyncConfig safe = config == null ? ComputerSyncConfig.empty() : config;
        return new ComputerSyncState(safe.coordinatorConsecutiveFailures(), safe.coordinatorLastCheckAtUtc(),
                safe.coordinatorLastSuccessAtUtc(), safe.coordinatorSnapshotId());
    }

    public int consecutiveFailures() { return consecutiveFailures; }
    public String lastCheckAtUtc() { return lastCheckAtUtc; }
    public String lastSuccessAtUtc() { return lastSuccessAtUtc; }
    public String snapshotId() { return snapshotId; }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
