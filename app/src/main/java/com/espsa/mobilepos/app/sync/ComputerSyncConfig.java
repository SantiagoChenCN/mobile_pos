package com.espsa.mobilepos.app.sync;

public final class ComputerSyncConfig {
    private final String host;
    private final int port;
    private final String token;
    private final String lastSeenSha256;
    private final String lastSyncedSha256;
    private final String lastCheckedAt;
    private final String lastSyncedAt;

    public ComputerSyncConfig(
            String host,
            int port,
            String token,
            String lastSeenSha256,
            String lastSyncedSha256,
            String lastCheckedAt,
            String lastSyncedAt
    ) {
        this.host = clean(host);
        this.port = port;
        this.token = clean(token);
        this.lastSeenSha256 = clean(lastSeenSha256);
        this.lastSyncedSha256 = clean(lastSyncedSha256);
        this.lastCheckedAt = clean(lastCheckedAt);
        this.lastSyncedAt = clean(lastSyncedAt);
    }

    public static ComputerSyncConfig empty() {
        return new ComputerSyncConfig("", 8765, "", "", "", "", "");
    }

    public boolean configured() {
        return !host.isEmpty() && port > 0 && port <= 65535 && !token.isEmpty();
    }

    public ComputerSyncConfig withLastSeen(String sha256, String checkedAtIso) {
        return new ComputerSyncConfig(host, port, token, sha256, lastSyncedSha256, checkedAtIso, lastSyncedAt);
    }

    public ComputerSyncConfig withLastSynced(String sha256, String syncedAtIso) {
        return new ComputerSyncConfig(host, port, token, lastSeenSha256, sha256, lastCheckedAt, syncedAtIso);
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

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
