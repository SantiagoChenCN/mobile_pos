package com.espsa.mobilepos.app.sync;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.time.Instant;

public final class ComputerSyncService {
    private static final String SETUP_SCHEME = "mobilepos-sync";

    private final ComputerSyncStore store;
    private final ComputerSyncClient client;

    public ComputerSyncService(ComputerSyncStore store, ComputerSyncClient client) {
        this.store = store;
        this.client = client;
    }

    public ComputerSyncConfig config(Context context) {
        return store.load(context);
    }

    public ComputerSyncConfig configureFromSetupUri(Context context, String setupUri) throws ComputerSyncException {
        ComputerSyncConfig parsed = parseSetupUri(setupUri);
        store.save(context, parsed);
        return parsed;
    }

    public boolean testConnection(Context context) throws ComputerSyncException {
        client.health(requireConfig(context));
        return true;
    }

    public ComputerSyncManifest checkManifest(Context context) throws ComputerSyncException {
        ComputerSyncConfig config = requireConfig(context);
        ComputerSyncManifest manifest = client.manifest(config);
        if (manifest.ok() && !manifest.sha256().isEmpty()) {
            store.save(context, config.withLastSeen(manifest.sha256(), nowIso()));
        }
        return manifest;
    }

    public boolean hasNewVersion(Context context, ComputerSyncManifest manifest) {
        if (manifest == null || !manifest.ok() || manifest.sha256().isEmpty()) {
            return false;
        }
        return !manifest.sha256().equalsIgnoreCase(store.load(context).lastSyncedSha256());
    }

    public File downloadLatestDatabase(Context context, ComputerSyncManifest manifest) throws ComputerSyncException {
        ComputerSyncConfig config = requireConfig(context);
        validateManifestForDownload(manifest);
        File target = tempDatabaseFile(context);
        client.downloadLatestDb(config, manifest, target);
        String actualHash = sha256(target);
        if (!actualHash.equalsIgnoreCase(manifest.sha256())) {
            target.delete();
            throw new ComputerSyncException("下载文件校验失败，请重新同步");
        }
        return target;
    }

    public void markSynced(Context context, ComputerSyncManifest manifest) {
        if (manifest != null && manifest.ok() && !manifest.sha256().isEmpty()) {
            store.save(context, store.load(context).withLastSynced(manifest.sha256(), nowIso()));
        }
    }

    private ComputerSyncConfig parseSetupUri(String setupUri) throws ComputerSyncException {
        Uri uri = Uri.parse(setupUri == null ? "" : setupUri.trim());
        if (!SETUP_SCHEME.equals(uri.getScheme())) {
            throw new ComputerSyncException("不是有效的电脑同步二维码");
        }
        String host = query(uri, "host");
        String token = query(uri, "token");
        int port = parsePort(query(uri, "port"));
        ComputerSyncConfig config = new ComputerSyncConfig(host, port, token, "", "", "", "");
        if (!config.configured()) {
            throw new ComputerSyncException("电脑同步二维码缺少 host、port 或 token");
        }
        return config;
    }

    private int parsePort(String value) throws ComputerSyncException {
        try {
            int port = Integer.parseInt(value);
            if (port <= 0 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
            return port;
        } catch (Exception ex) {
            throw new ComputerSyncException("电脑同步端口无效");
        }
    }

    private String query(Uri uri, String key) {
        String value = uri.getQueryParameter(key);
        return value == null ? "" : value.trim();
    }

    private ComputerSyncConfig requireConfig(Context context) throws ComputerSyncException {
        ComputerSyncConfig config = store.load(context);
        if (!config.configured()) {
            throw new ComputerSyncException("电脑同步尚未配置");
        }
        return config;
    }

    private void validateManifestForDownload(ComputerSyncManifest manifest) throws ComputerSyncException {
        if (manifest == null) {
            throw new ComputerSyncException("电脑端未返回 manifest");
        }
        if (!manifest.ok()) {
            throw new ComputerSyncException(manifest.error().isEmpty() ? "电脑端还没有可同步备份" : manifest.error());
        }
        if (manifest.sha256().isEmpty() || manifest.sizeBytes() <= 0) {
            throw new ComputerSyncException("电脑端 manifest 缺少 hash 或文件大小");
        }
    }

    private File tempDatabaseFile(Context context) throws ComputerSyncException {
        File directory = new File(context.getCacheDir(), "computer-sync");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ComputerSyncException("无法创建电脑同步缓存目录");
        }
        File file = new File(directory, "latest-sync.db");
        if (file.exists()) {
            file.delete();
        }
        return file;
    }

    private String sha256(File file) throws ComputerSyncException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            return hex(digest.digest());
        } catch (Exception ex) {
            throw new ComputerSyncException("计算下载文件 SHA-256 失败", ex);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private String nowIso() {
        return Instant.now().toString();
    }
}
