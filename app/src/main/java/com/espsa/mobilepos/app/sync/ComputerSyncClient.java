package com.espsa.mobilepos.app.sync;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ComputerSyncClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    public JSONObject health(ComputerSyncConfig config) throws ComputerSyncException {
        return getJson(config, "/health");
    }

    public ComputerSyncManifest manifest(ComputerSyncConfig config) throws ComputerSyncException {
        return ComputerSyncManifest.fromJson(getJson(config, "/manifest.json"));
    }

    public void downloadLatestDb(
            ComputerSyncConfig config,
            ComputerSyncManifest manifest,
            File target
    ) throws ComputerSyncException {
        HttpURLConnection connection = openConnection(config, manifest.downloadPath());
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new ComputerSyncException("访问令牌不正确，请重新扫码");
            }
            if (status < 200 || status >= 300) {
                throw new ComputerSyncException("下载数据库失败，HTTP " + status);
            }
            String headerHash = connection.getHeaderField("X-File-Sha256");
            if (headerHash != null && !headerHash.trim().isEmpty()
                    && !headerHash.trim().equalsIgnoreCase(manifest.sha256())) {
                throw new ComputerSyncException("下载文件 hash header 与 manifest 不一致");
            }
            writeResponseToFile(connection, target);
        } catch (ComputerSyncException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ComputerSyncException("下载数据库失败", ex);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject getJson(ComputerSyncConfig config, String path) throws ComputerSyncException {
        HttpURLConnection connection = openConnection(config, path);
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new ComputerSyncException("访问令牌不正确，请重新扫码");
            }
            if (status < 200 || status >= 300) {
                throw new ComputerSyncException("电脑同步工具返回 HTTP " + status);
            }
            return new JSONObject(readResponse(connection));
        } catch (ComputerSyncException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ComputerSyncException("无法读取电脑同步工具响应", ex);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(ComputerSyncConfig config, String path) throws ComputerSyncException {
        if (config == null || !config.configured()) {
            throw new ComputerSyncException("电脑同步尚未配置");
        }
        try {
            URL url = new URL(config.baseUrl() + normalizePath(path) + "?token=" + encode(config.token()));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            return connection;
        } catch (Exception ex) {
            throw new ComputerSyncException("无法连接电脑同步工具", ex);
        }
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
