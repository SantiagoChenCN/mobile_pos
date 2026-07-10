package com.espsa.mobilepos.app.sync;

import org.json.JSONObject;

public final class ComputerSyncManifest {
    private final boolean ok;
    private final String error;
    private final String version;
    private final String fileName;
    private final long sizeBytes;
    private final String sha256;
    private final String createdAt;
    private final String downloadPath;

    public ComputerSyncManifest(
            boolean ok,
            String error,
            String version,
            String fileName,
            long sizeBytes,
            String sha256,
            String createdAt,
            String downloadPath
    ) {
        this.ok = ok;
        this.error = clean(error);
        this.version = clean(version);
        this.fileName = clean(fileName);
        this.sizeBytes = Math.max(0, sizeBytes);
        this.sha256 = clean(sha256);
        this.createdAt = clean(createdAt);
        this.downloadPath = clean(downloadPath);
    }

    public static ComputerSyncManifest fromJson(JSONObject object) {
        if (object == null) {
            return new ComputerSyncManifest(false, "EMPTY_RESPONSE", "", "", 0, "", "", "");
        }
        return new ComputerSyncManifest(
                object.optBoolean("ok", false),
                object.optString("error", ""),
                object.optString("version", ""),
                object.optString("fileName", ""),
                object.optLong("sizeBytes", 0),
                object.optString("sha256", ""),
                object.optString("createdAt", ""),
                object.optString("downloadPath", "/latest.db")
        );
    }

    public boolean ok() {
        return ok;
    }

    public String error() {
        return error;
    }

    public String version() {
        return version;
    }

    public String fileName() {
        return fileName;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String sha256() {
        return sha256;
    }

    public String createdAt() {
        return createdAt;
    }

    public String downloadPath() {
        return downloadPath.isEmpty() ? "/latest.db" : downloadPath;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
