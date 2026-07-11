package com.espsa.mobilepos.app.sync;

import org.json.JSONObject;

public final class ComputerSyncHealth {
    private final String version;
    private final String host;
    private final int port;

    private ComputerSyncHealth(String version, String host, int port) {
        this.version = clean(version);
        this.host = clean(host);
        this.port = port;
    }

    static ComputerSyncHealth fromValidatedResponse(JSONObject response) {
        return new ComputerSyncHealth(
                response.optString("version"),
                response.optString("host"),
                response.optInt("port")
        );
    }

    public String version() {
        return version;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
