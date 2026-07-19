package com.espsa.mobilepos.app.sync;

import android.content.Context;

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
import java.security.MessageDigest;

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

    public ComputerSyncManifestV2 manifestV2(ComputerSyncConfig config) throws ComputerSyncException {
        HttpURLConnection connection = null;
        try {
            connection = openV2Connection(config, "/v2/manifest.json");
            int status = connection.getResponseCode();
            if (!isSuccessfulV2Status(status)) {
                throw httpStatusException(status, "读取 v2 manifest 失败");
            }
            return ComputerSyncManifestV2.fromUtf8Json(readManifestResponse(connection.getInputStream(), validateManifestContentLength(connection.getHeaderField("Content-Length"))));
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

    File downloadV2Snapshot(
            Context context,
            ComputerSyncConfig config,
            ComputerSyncManifestV2 manifest,
            V2DownloadCancellation cancellation
    ) throws ComputerSyncException {
        HttpURLConnection connection = null;
        File temporaryTarget = null;
        boolean complete = false;
        try {
            if (context == null || manifest == null) {
                throw invalidResponseException();
            }
            File temporaryDirectory = new File(context.getCacheDir(), "computer-sync-v2/tmp");
            if (!temporaryDirectory.isDirectory()) {
                throw invalidResponseException();
            }
            temporaryTarget = File.createTempFile("snapshot-v2-", ".part", temporaryDirectory);
            connection = openV2Connection(config, manifest.downloadPath());
            V2DownloadCancellation activeCancellation = cancellation == null
                    ? new V2DownloadCancellation() : cancellation;
            activeCancellation.attach(connection);
            checkCancelled(activeCancellation);
            int status = connection.getResponseCode();
            if (!isSuccessfulV2Status(status)) {
                throw httpStatusException(status, "下载 v2 快照失败");
            }
            streamV2Snapshot(
                    manifest,
                    requiredContentLength(connection.getHeaderField("Content-Length")),
                    connection.getHeaderField("X-File-Sha256"),
                    connection.getHeaderField("X-Snapshot-Id"),
                    connection.getInputStream(),
                    temporaryTarget,
                    activeCancellation
            );
            complete = true;
            return temporaryTarget;
        } catch (ComputerSyncException ex) {
            throw ex;
        } catch (Exception ex) {
            throw connectionFailureFor(ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (cancellation != null) {
                cancellation.detach(connection);
            }
            if (!complete) deleteIfExists(temporaryTarget);
        }
    }

    private static void streamV2Snapshot(
            ComputerSyncManifestV2 manifest,
            long contentLength,
            String headerSha256,
            String headerSnapshotId,
            InputStream response,
            File temporaryTarget
    ) throws ComputerSyncException {
        streamV2Snapshot(
                manifest, contentLength, headerSha256, headerSnapshotId, response,
                temporaryTarget, new V2DownloadCancellation()
        );
    }

    private static void streamV2Snapshot(
            ComputerSyncManifestV2 manifest,
            long contentLength,
            String headerSha256,
            String headerSnapshotId,
            InputStream response,
            File temporaryTarget,
            V2DownloadCancellation cancellation
    ) throws ComputerSyncException {
        boolean complete = false;
        try {
            if (manifest == null || response == null || temporaryTarget == null
                    || contentLength != manifest.sizeBytes()
                    || !manifest.sha256().equals(headerSha256)
                    || !manifest.snapshotId().equals(headerSnapshotId)) {
                throw invalidResponseException();
            }
            checkCancelled(cancellation);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written = 0L;
            try (InputStream input = new BufferedInputStream(response); BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporaryTarget, false))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    checkCancelled(cancellation);
                    try {
                        written = Math.addExact(written, read);
                    } catch (ArithmeticException exception) {
                        throw invalidResponseException();
                    }
                    if (written > manifest.sizeBytes()) {
                        throw invalidResponseException();
                    }
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
                checkCancelled(cancellation);
                output.flush();
            }
            if (written != manifest.sizeBytes()
                    || !manifest.sha256().equals(hex(digest.digest()))) {
                throw invalidResponseException();
            }
            complete = true;
        } catch (ComputerSyncException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_RESPONSE,
                    "下载 v2 快照时发生错误",
                    exception
            );
        } finally {
            if (!complete) {
                deleteIfExists(temporaryTarget);
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
            connection.setUseCaches(false);
            return connection;
        } catch (Exception ex) {
            throw connectionFailureFor(ex);
        }
    }

    private HttpURLConnection openV2Connection(ComputerSyncConfig config, String path) throws ComputerSyncException {
        if (config == null || !config.configured()) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_CONFIG,
                    "电脑同步尚未配置"
            );
        }
        try {
            URL url = new URL(config.baseUrl() + normalizePath(path));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("Authorization", v2AuthorizationHeader(config));
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

    static byte[] readManifestResponse(InputStream response, long declaredBytes) throws ComputerSyncException {
        try (InputStream input = new BufferedInputStream(response)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long byteCount = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                checkCancelled(null);
                byteCount = Math.addExact(byteCount, read);
                if (byteCount > 256L * 1024L) {
                    throw invalidResponseException();
                }
                output.write(buffer, 0, read);
            }
            checkCancelled(null);
            if (byteCount != declaredBytes) throw invalidResponseException();
            return output.toByteArray();
        } catch (ComputerSyncException e) { throw e; } catch (Exception e) { throw invalidResponseException(); }
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

    private static long requiredContentLength(String value) throws ComputerSyncException {
        if (value == null || !value.matches("[0-9]+")) {
            throw invalidResponseException();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalidResponseException();
        }
    }

    static long validateManifestContentLength(String value) throws ComputerSyncException {
        long length = requiredContentLength(value);
        if (length == 0 || length > 256L * 1024L) {
            throw invalidResponseException();
        }
        return length;
    }

    static boolean isSuccessfulV2Status(int status) {
        return status == HttpURLConnection.HTTP_OK;
    }

    static String v2AuthorizationHeader(ComputerSyncConfig config) throws ComputerSyncException {
        if (config == null || !config.configured()) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_CONFIG,
                    "电脑同步尚未配置"
            );
        }
        return "Bearer " + config.token();
    }

    private static void checkCancelled(V2DownloadCancellation cancellation) throws ComputerSyncException {
        if (Thread.currentThread().isInterrupted()
                || (cancellation != null && cancellation.cancelled())) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.UNKNOWN,
                    "v2 快照下载已取消"
            );
        }
    }

    private static void deleteIfExists(File file) throws ComputerSyncException {
        if (file != null && file.exists()) {
            if (!file.delete() && file.exists()) {
                throw new ComputerSyncException(
                        ComputerSyncFailureReason.INVALID_RESPONSE,
                        "无法清理未完成的 v2 快照临时文件"
                );
            }
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(64);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    /** Cancellation has no file path authority; it can only disconnect its registered HTTP request. */
    public static final class V2DownloadCancellation {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        public void cancel() {
            cancelled = true;
            HttpURLConnection active = connection;
            if (active != null) {
                active.disconnect();
            }
        }

        boolean cancelled() {
            return cancelled;
        }

        void attach(HttpURLConnection value) {
            connection = value;
            if (cancelled && value != null) {
                value.disconnect();
            }
        }

        void detach(HttpURLConnection value) {
            if (connection == value) {
                connection = null;
            }
        }
    }
}
