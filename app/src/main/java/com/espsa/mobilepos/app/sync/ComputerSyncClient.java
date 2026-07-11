package com.espsa.mobilepos.app.sync;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ComputerSyncClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    public ComputerSyncHealth health(ComputerSyncConfig config) throws ComputerSyncException {
        JSONObject response = getJson(config, "/health");
        validateHealthResponse(response);
        return ComputerSyncHealth.fromValidatedResponse(response);
    }

    public ComputerSyncManifest manifest(ComputerSyncConfig config) throws ComputerSyncException {
        return ComputerSyncManifest.fromJson(getJson(config, "/manifest.json"));
    }

    public void downloadLatestDb(
            ComputerSyncConfig config,
            ComputerSyncManifest manifest,
            File target
    ) throws ComputerSyncException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(config, manifest.downloadPath());
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpStatusException(status, "下载数据库失败");
            }
            String headerHash = connection.getHeaderField("X-File-Sha256");
            if (headerHash != null && !headerHash.trim().isEmpty()
                    && !headerHash.trim().equalsIgnoreCase(manifest.sha256())) {
                throw new ComputerSyncException(
                        ComputerSyncFailureReason.INVALID_RESPONSE,
                        "下载文件 hash header 与 manifest 不一致"
                );
            }
            writeResponseToFile(connection, target);
        } catch (ComputerSyncException ex) {
            throw ex;
        } catch (Exception ex) {
            throw connectionFailureFor(ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject getJson(ComputerSyncConfig config, String path) throws ComputerSyncException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(config, path);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpStatusException(status, "电脑同步工具返回");
            }
            return new JSONObject(readResponse(connection));
        } catch (ComputerSyncException ex) {
            throw ex;
        } catch (JSONException ex) {
            throw invalidResponseException();
        } catch (Exception ex) {
            throw connectionFailureFor(ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(ComputerSyncConfig config, String path) throws ComputerSyncException {
        if (config == null || !config.configured()) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_CONFIG,
                    "电脑同步尚未配置"
            );
        }
        try {
            URL url = new URL(config.baseUrl() + normalizePath(path) + "?token=" + encode(config.token()));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            return connection;
        } catch (Exception ex) {
            throw connectionFailureFor(ex);
        }
    }

    static void validateHealthResponse(JSONObject response) throws ComputerSyncException {
        if (response == null) {
            throw invalidResponseException();
        }
        validateHealthValues(
                response.opt("ok"),
                response.opt("app"),
                response.opt("version"),
                response.opt("host"),
                response.opt("port")
        );
    }

    static void validateHealthValues(
            Object okValue,
            Object appValue,
            Object versionValue,
            Object hostValue,
            Object portValue
    ) throws ComputerSyncException {
        if (!Boolean.TRUE.equals(okValue)
                || !(appValue instanceof String)
                || !"MobilePosSync".equals(appValue)
                || !(versionValue instanceof String)
                || ((String) versionValue).trim().isEmpty()
                || !(hostValue instanceof String)
                || !ComputerSyncConfig.isIpv4Address((String) hostValue)) {
            throw invalidResponseException();
        }
        if (!(portValue instanceof Number)) {
            throw invalidResponseException();
        }
        double portNumber = ((Number) portValue).doubleValue();
        if (portNumber != Math.rint(portNumber) || portNumber < 1 || portNumber > 65535) {
            throw invalidResponseException();
        }
    }

    static ComputerSyncFailureReason failureReasonFor(Throwable failure) {
        if (hasCleartextBlock(failure)) {
            return ComputerSyncFailureReason.CLEAR_TEXT_BLOCKED;
        }
        if (hasCause(failure, SocketTimeoutException.class)) {
            return ComputerSyncFailureReason.CONNECTION_TIMEOUT;
        }
        if (hasCause(failure, ConnectException.class)) {
            return ComputerSyncFailureReason.CONNECTION_REFUSED;
        }
        if (hasCause(failure, UnknownHostException.class)
                || hasCause(failure, MalformedURLException.class)) {
            return ComputerSyncFailureReason.UNKNOWN_HOST;
        }
        return ComputerSyncFailureReason.UNKNOWN;
    }

    static ComputerSyncException connectionFailureFor(Throwable failure) {
        ComputerSyncFailureReason reason = failureReasonFor(failure);
        return new ComputerSyncException(reason, messageFor(reason), failure);
    }

    static ComputerSyncException httpStatusException(int status, String operation) {
        if (status == HttpURLConnection.HTTP_FORBIDDEN) {
            return new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_TOKEN,
                    "访问令牌不正确，请检查电脑端 Token"
            );
        }
        return new ComputerSyncException(
                ComputerSyncFailureReason.HTTP_ERROR,
                operation + " HTTP " + status
        );
    }

    private static ComputerSyncException invalidResponseException() {
        return new ComputerSyncException(
                ComputerSyncFailureReason.INVALID_RESPONSE,
                "电脑同步工具返回的响应无效"
        );
    }

    private static String messageFor(ComputerSyncFailureReason reason) {
        switch (reason) {
            case CLEAR_TEXT_BLOCKED:
                return "当前应用未允许局域网 HTTP 连接";
            case CONNECTION_TIMEOUT:
                return "连接电脑同步工具超时";
            case CONNECTION_REFUSED:
                return "电脑同步工具拒绝连接";
            case UNKNOWN_HOST:
                return "电脑同步 IP 地址无效或无法解析";
            default:
                return "无法连接电脑同步工具";
        }
    }

    private static boolean hasCleartextBlock(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof UnknownServiceException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("cleartext")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/latest.db";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        InputStream input = new BufferedInputStream(connection.getInputStream());
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }

    private void writeResponseToFile(HttpURLConnection connection, File target) throws Exception {
        InputStream input = new BufferedInputStream(connection.getInputStream());
        BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target, false));
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            output.close();
            input.close();
        }
    }
}
