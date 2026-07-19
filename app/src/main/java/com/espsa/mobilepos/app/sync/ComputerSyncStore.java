package com.espsa.mobilepos.app.sync;

import android.content.Context;
import android.content.SharedPreferences;

public final class ComputerSyncStore {
    private static final String PREFERENCES_NAME = "computer_sync";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String TOKEN = "token";
    private static final String LAST_SEEN_SHA256 = "lastSeenSha256";
    private static final String LAST_SYNCED_SHA256 = "lastSyncedSha256";
    private static final String LAST_CHECKED_AT = "lastCheckedAt";
    private static final String LAST_SYNCED_AT = "lastSyncedAt";
    private static final String FOREGROUND_INTERVAL_SECONDS = "foregroundIntervalSeconds";
    private static final String COORDINATOR_CONSECUTIVE_FAILURES = "coordinatorConsecutiveFailures";
    private static final String COORDINATOR_LAST_CHECK_AT_UTC = "coordinatorLastCheckAtUtc";
    private static final String COORDINATOR_LAST_SUCCESS_AT_UTC = "coordinatorLastSuccessAtUtc";
    private static final String COORDINATOR_SNAPSHOT_ID = "coordinatorSnapshotId";

    public ComputerSyncConfig load(Context context) {
        SharedPreferences preferences = preferences(context);
        return new ComputerSyncConfig(
                preferences.getString(HOST, ""),
                preferences.getInt(PORT, 8765),
                preferences.getString(TOKEN, ""),
                preferences.getString(LAST_SEEN_SHA256, ""),
                preferences.getString(LAST_SYNCED_SHA256, ""),
                preferences.getString(LAST_CHECKED_AT, ""),
                preferences.getString(LAST_SYNCED_AT, ""),
                ComputerSyncConfig.storedForegroundIntervalSeconds(preferences.getInt(
                        FOREGROUND_INTERVAL_SECONDS, ComputerSyncConfig.DEFAULT_FOREGROUND_INTERVAL_SECONDS)),
                Math.max(0, preferences.getInt(COORDINATOR_CONSECUTIVE_FAILURES, 0)),
                preferences.getString(COORDINATOR_LAST_CHECK_AT_UTC, ""),
                preferences.getString(COORDINATOR_LAST_SUCCESS_AT_UTC, ""),
                preferences.getString(COORDINATOR_SNAPSHOT_ID, "")
        );
    }

    public void save(Context context, ComputerSyncConfig config) {
        ComputerSyncConfig safeConfig = config == null ? ComputerSyncConfig.empty() : config;
        preferences(context)
                .edit()
                .putString(HOST, safeConfig.host())
                .putInt(PORT, safeConfig.port())
                .putString(TOKEN, safeConfig.token())
                .putString(LAST_SEEN_SHA256, safeConfig.lastSeenSha256())
                .putString(LAST_SYNCED_SHA256, safeConfig.lastSyncedSha256())
                .putString(LAST_CHECKED_AT, safeConfig.lastCheckedAt())
                .putString(LAST_SYNCED_AT, safeConfig.lastSyncedAt())
                .putInt(FOREGROUND_INTERVAL_SECONDS, safeConfig.foregroundIntervalSeconds())
                .putInt(COORDINATOR_CONSECUTIVE_FAILURES, safeConfig.coordinatorConsecutiveFailures())
                .putString(COORDINATOR_LAST_CHECK_AT_UTC, safeConfig.coordinatorLastCheckAtUtc())
                .putString(COORDINATOR_LAST_SUCCESS_AT_UTC, safeConfig.coordinatorLastSuccessAtUtc())
                .putString(COORDINATOR_SNAPSHOT_ID, safeConfig.coordinatorSnapshotId())
                .apply();
    }

    public void clear(Context context) {
        preferences(context).edit().clear().apply();
    }

    private SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
