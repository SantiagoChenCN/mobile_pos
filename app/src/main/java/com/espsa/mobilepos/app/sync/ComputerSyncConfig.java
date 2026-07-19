package com.espsa.mobilepos.app.sync;

public final class ComputerSyncConfig {
    public static final int DEFAULT_FOREGROUND_INTERVAL_SECONDS = 30;
    public static final int MIN_FOREGROUND_INTERVAL_SECONDS = 5;
    public static final int MAX_FOREGROUND_INTERVAL_SECONDS = 86400;
    private final String host;
    private final int port;
    private final String token;
    private final String lastSeenSha256;
    private final String lastSyncedSha256;
    private final String lastCheckedAt;
    private final String lastSyncedAt;
    private final int foregroundIntervalSeconds;
    private final int coordinatorConsecutiveFailures;
    private final String coordinatorLastCheckAtUtc;
    private final String coordinatorLastSuccessAtUtc;
    private final String coordinatorSnapshotId;

    public ComputerSyncConfig(
            String host,
            int port,
            String token,
            String lastSeenSha256,
            String lastSyncedSha256,
            String lastCheckedAt,
            String lastSyncedAt
    ) {
        this(host, port, token, lastSeenSha256, lastSyncedSha256, lastCheckedAt, lastSyncedAt,
                DEFAULT_FOREGROUND_INTERVAL_SECONDS, 0, "", "", "");
    }

    public ComputerSyncConfig(
            String host,
            int port,
            String token,
            String lastSeenSha256,
            String lastSyncedSha256,
            String lastCheckedAt,
            String lastSyncedAt,
            int foregroundIntervalSeconds,
            int coordinatorConsecutiveFailures,
            String coordinatorLastCheckAtUtc,
            String coordinatorLastSuccessAtUtc,
            String coordinatorSnapshotId
    ) {
        this.host = clean(host);
        this.port = port;
        this.token = clean(token);
        this.lastSeenSha256 = clean(lastSeenSha256);
        this.lastSyncedSha256 = clean(lastSyncedSha256);
        this.lastCheckedAt = clean(lastCheckedAt);
        this.lastSyncedAt = clean(lastSyncedAt);
        this.foregroundIntervalSeconds = requireForegroundIntervalSeconds(foregroundIntervalSeconds);
        if (coordinatorConsecutiveFailures < 0) {
            throw new IllegalArgumentException("Coordinator failure count cannot be negative");
        }
        this.coordinatorConsecutiveFailures = coordinatorConsecutiveFailures;
        this.coordinatorLastCheckAtUtc = clean(coordinatorLastCheckAtUtc);
        this.coordinatorLastSuccessAtUtc = clean(coordinatorLastSuccessAtUtc);
        this.coordinatorSnapshotId = clean(coordinatorSnapshotId);
    }

    public static ComputerSyncConfig empty() {
        return new ComputerSyncConfig("", 8765, "", "", "", "", "");
    }

    public boolean configured() {
        return !host.isEmpty() && port > 0 && port <= 65535 && !token.isEmpty();
    }

    public ComputerSyncConfig withLastSeen(String sha256, String checkedAtIso) {
        return copy(sha256, lastSyncedSha256, checkedAtIso, lastSyncedAt,
                foregroundIntervalSeconds, coordinatorConsecutiveFailures, coordinatorLastCheckAtUtc,
                coordinatorLastSuccessAtUtc, coordinatorSnapshotId);
    }

    public ComputerSyncConfig withLastSynced(String sha256, String syncedAtIso) {
        return copy(lastSeenSha256, sha256, lastCheckedAt, syncedAtIso,
                foregroundIntervalSeconds, coordinatorConsecutiveFailures, coordinatorLastCheckAtUtc,
                coordinatorLastSuccessAtUtc, coordinatorSnapshotId);
    }

    public ComputerSyncConfig withForegroundIntervalSeconds(int value) {
        return copy(lastSeenSha256, lastSyncedSha256, lastCheckedAt, lastSyncedAt,
                value, coordinatorConsecutiveFailures, coordinatorLastCheckAtUtc,
                coordinatorLastSuccessAtUtc, coordinatorSnapshotId);
    }

    public ComputerSyncConfig withCoordinatorState(
            int failures,
            String lastCheckAtUtc,
            String lastSuccessAtUtc,
            String snapshotId
    ) {
        return copy(lastSeenSha256, lastSyncedSha256, lastCheckedAt, lastSyncedAt,
                foregroundIntervalSeconds, failures, lastCheckAtUtc, lastSuccessAtUtc, snapshotId);
    }

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String token() {
        return token;
    }

    static boolean isIpv4Address(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        String[] parts = value.trim().split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            int number = 0;
            for (int index = 0; index < part.length(); index++) {
                char character = part.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
                number = number * 10 + (character - '0');
            }
            if (number > 255) {
                return false;
            }
        }
        return true;
    }

    public String lastSeenSha256() {
        return lastSeenSha256;
    }

    public String lastSyncedSha256() {
        return lastSyncedSha256;
    }

    public String lastCheckedAt() {
        return lastCheckedAt;
    }

    public String lastSyncedAt() {
        return lastSyncedAt;
    }

    public int foregroundIntervalSeconds() { return foregroundIntervalSeconds; }
    public int coordinatorConsecutiveFailures() { return coordinatorConsecutiveFailures; }
    public String coordinatorLastCheckAtUtc() { return coordinatorLastCheckAtUtc; }
    public String coordinatorLastSuccessAtUtc() { return coordinatorLastSuccessAtUtc; }
    public String coordinatorSnapshotId() { return coordinatorSnapshotId; }
    int coordinatorStateFieldCount() { return 4; }

    static int storedForegroundIntervalSeconds(int value) {
        return value == 0 || (value >= MIN_FOREGROUND_INTERVAL_SECONDS && value <= MAX_FOREGROUND_INTERVAL_SECONDS)
                ? value : DEFAULT_FOREGROUND_INTERVAL_SECONDS;
    }

    private ComputerSyncConfig copy(
            String nextLastSeenSha256,
            String nextLastSyncedSha256,
            String nextLastCheckedAt,
            String nextLastSyncedAt,
            int nextForegroundIntervalSeconds,
            int nextCoordinatorConsecutiveFailures,
            String nextCoordinatorLastCheckAtUtc,
            String nextCoordinatorLastSuccessAtUtc,
            String nextCoordinatorSnapshotId
    ) {
        return new ComputerSyncConfig(host, port, token, nextLastSeenSha256, nextLastSyncedSha256,
                nextLastCheckedAt, nextLastSyncedAt, nextForegroundIntervalSeconds,
                nextCoordinatorConsecutiveFailures, nextCoordinatorLastCheckAtUtc,
                nextCoordinatorLastSuccessAtUtc, nextCoordinatorSnapshotId);
    }

    private static int requireForegroundIntervalSeconds(int value) {
        if (value == 0 || (value >= MIN_FOREGROUND_INTERVAL_SECONDS && value <= MAX_FOREGROUND_INTERVAL_SECONDS)) {
            return value;
        }
        throw new IllegalArgumentException("Foreground interval must be 0 or 5..86400 seconds");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
