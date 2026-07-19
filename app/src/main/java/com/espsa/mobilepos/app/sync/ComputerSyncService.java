package com.espsa.mobilepos.app.sync;

import android.content.Context;

import com.espsa.mobilepos.core.model.V2Contract;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;

public final class ComputerSyncService {
    private static final String MANUAL_CONFIG_MISSING_MESSAGE = "连接信息缺少 IP、端口或 Token";
    private static final String INVALID_IP_MESSAGE = "电脑同步 IP 地址无效，请输入电脑工具显示的局域网 IPv4 地址";
    private static final String LOOPBACK_IP_MESSAGE = "127.0.0.1 代表手机自身，请输入电脑工具显示的局域网 IP";

    private final ComputerSyncStore store;
    private final ComputerSyncClient client;

    public ComputerSyncService(ComputerSyncStore store, ComputerSyncClient client) {
        this.store = store;
        this.client = client;
    }

    public ComputerSyncConfig config(Context context) {
        return store.load(context);
    }

    public ComputerSyncConfig configureManual(
            Context context,
            String host,
            int port,
            String token
    ) throws ComputerSyncException {
        ComputerSyncConfig config = validateManualConfig(host, port, token);
        store.save(context, config);
        return config;
    }

    public ComputerSyncHealth testConnection(Context context) throws ComputerSyncException {
        return client.health(requireConfig(context));
    }

    public ComputerSyncManifest checkManifest(Context context) throws ComputerSyncException {
        ComputerSyncConfig config = requireConfig(context);
        ComputerSyncManifest manifest = client.manifest(config);
        if (manifest.ok() && !manifest.sha256().isEmpty()) {
            store.save(context, config.withLastSeen(manifest.sha256(), nowIso()));
        }
        return manifest;
    }

    public ComputerSyncManifestV2 checkManifestV2(Context context) throws ComputerSyncException {
        ComputerSyncManifestV2 manifest = client.manifestV2(requireConfig(context));
        validateMinimumAppVersion(context, manifest);
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

    public File downloadV2Database(Context context, ComputerSyncManifestV2 manifest) throws ComputerSyncException {
        return downloadV2Database(context, manifest, new ComputerSyncClient.V2DownloadCancellation());
    }

    public File downloadV2Database(
            Context context,
            ComputerSyncManifestV2 manifest,
            ComputerSyncClient.V2DownloadCancellation cancellation
    ) throws ComputerSyncException {
        ComputerSyncConfig config = requireConfig(context);
        if (manifest == null) {
            throw new ComputerSyncException(ComputerSyncFailureReason.INVALID_RESPONSE, "电脑端未返回 v2 manifest");
        }
        validateMinimumAppVersion(context, manifest);
        File directory = v2TemporaryDirectory(context);
        validateV2DownloadSpace(manifest, directory.getUsableSpace());
        File target = null;
        boolean complete = false;
        try {
            target = client.downloadV2Snapshot(context, config, manifest, cancellation);
            throwIfCancelled(cancellation);
            if (target.length() != manifest.sizeBytes()
                    || !sha256(target, cancellation).equals(manifest.sha256())) {
                throw new ComputerSyncException(
                        ComputerSyncFailureReason.INVALID_RESPONSE,
                        "下载的 v2 快照与 manifest 不一致"
                );
            }
            throwIfCancelled(cancellation);
            complete = true;
            return target;
        } finally {
            if (!complete) {
                deleteV2TemporaryFile(directory, target);
            }
        }
    }

    public void markSynced(Context context, ComputerSyncManifest manifest) {
        if (manifest != null && manifest.ok() && !manifest.sha256().isEmpty()) {
            store.save(context, store.load(context).withLastSynced(manifest.sha256(), nowIso()));
        }
    }

    static ComputerSyncConfig validateManualConfig(
            String host,
            int port,
            String token
    ) throws ComputerSyncException {
        ComputerSyncConfig config = new ComputerSyncConfig(host, port, token, "", "", "", "");
        if (config.host().isEmpty()) {
            throw invalidConfig(MANUAL_CONFIG_MISSING_MESSAGE);
        }
        if (config.port() <= 0 || config.port() > 65535) {
            throw invalidConfig("电脑同步端口无效");
        }
        if (config.token().isEmpty()) {
            throw invalidConfig(MANUAL_CONFIG_MISSING_MESSAGE);
        }
        if ("127.0.0.1".equals(config.host())) {
            throw invalidConfig(LOOPBACK_IP_MESSAGE);
        }
        if ("localhost".equalsIgnoreCase(config.host()) || "0.0.0.0".equals(config.host())) {
            throw invalidConfig(INVALID_IP_MESSAGE);
        }
        if (!ComputerSyncConfig.isIpv4Address(config.host())) {
            throw invalidConfig(INVALID_IP_MESSAGE);
        }
        if (!config.configured()) {
            throw invalidConfig(MANUAL_CONFIG_MISSING_MESSAGE);
        }
        return config;
    }

    private static ComputerSyncException invalidConfig(String message) {
        return new ComputerSyncException(ComputerSyncFailureReason.INVALID_CONFIG, message);
    }

    private ComputerSyncConfig requireConfig(Context context) throws ComputerSyncException {
        ComputerSyncConfig config = store.load(context);
        validateManualConfig(config.host(), config.port(), config.token());
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

    static void validateV2DownloadSpace(ComputerSyncManifestV2 manifest, long availableBytes)
            throws ComputerSyncException {
        if (manifest == null) {
            throw new ComputerSyncException(ComputerSyncFailureReason.INVALID_RESPONSE, "电脑端未返回 v2 manifest");
        }
        try {
            long size = manifest.sizeBytes();
            V2Contract.requireDownloadSpace(availableBytes, size, size, size, size);
        } catch (IllegalArgumentException exception) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_RESPONSE,
                    "可用空间不足，无法安全下载 v2 快照",
                    exception
            );
        }
    }

    private void validateMinimumAppVersion(Context context, ComputerSyncManifestV2 manifest)
            throws ComputerSyncException {
        try {
            long installedVersion = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .getLongVersionCode();
            if (manifest.minimumAppVersion() > installedVersion) {
                throw new ComputerSyncException(
                        ComputerSyncFailureReason.INVALID_RESPONSE,
                        "电脑端 v2 快照需要更新应用后才能导入"
                );
            }
        } catch (ComputerSyncException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_RESPONSE,
                    "无法确认当前应用版本",
                    exception
            );
        }
    }

    private File v2TemporaryDirectory(Context context) throws ComputerSyncException {
        File directory = new File(context.getCacheDir(), "computer-sync-v2/tmp");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new ComputerSyncException("无法创建 v2 快照临时目录");
        }
        return directory;
    }

    private void deleteV2TemporaryFile(File directory, File temporaryFile) throws ComputerSyncException {
        try {
            if (temporaryFile == null || directory == null) {
                return;
            }
            File canonicalDirectory = directory.getCanonicalFile();
            File canonicalTemporary = temporaryFile.getCanonicalFile();
            if (!canonicalDirectory.equals(canonicalTemporary.getParentFile())
                    || !canonicalTemporary.getName().startsWith("snapshot-v2-")
                    || !canonicalTemporary.getName().endsWith(".part")) {
                throw new ComputerSyncException(
                        ComputerSyncFailureReason.INVALID_RESPONSE,
                        "拒绝清理不属于 v2 临时目录的文件"
                );
            }
            if (canonicalTemporary.exists() && (!canonicalTemporary.isFile() || !canonicalTemporary.delete())) {
                throw new ComputerSyncException(ComputerSyncFailureReason.INVALID_RESPONSE, "无法清理 v2 临时文件");
            }
        } catch (ComputerSyncException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.INVALID_RESPONSE,
                    "无法确认 v2 临时文件归属",
                    exception
            );
        }
    }

    private String sha256(File file) throws ComputerSyncException {
        return sha256(file, null);
    }

    private String sha256(File file, ComputerSyncClient.V2DownloadCancellation cancellation) throws ComputerSyncException {
        try {
            return sha256Stream(new FileInputStream(file), cancellation);
        } catch (ComputerSyncException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ComputerSyncException("计算下载文件 SHA-256 失败", ex);
        }
    }

    static String sha256Stream(
            InputStream source,
            ComputerSyncClient.V2DownloadCancellation cancellation
    ) throws ComputerSyncException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (BufferedInputStream input = new BufferedInputStream(source)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    throwIfCancelled(cancellation);
                    digest.update(buffer, 0, read);
                }
                throwIfCancelled(cancellation);
            }
            return hex(digest.digest());
        } catch (ComputerSyncException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ComputerSyncException("计算下载文件 SHA-256 失败", ex);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private String nowIso() {
        return Instant.now().toString();
    }

    private static void throwIfCancelled(ComputerSyncClient.V2DownloadCancellation cancellation) throws ComputerSyncException {
        if (Thread.currentThread().isInterrupted() || (cancellation != null && cancellation.cancelled())) {
            throw new ComputerSyncException(
                    ComputerSyncFailureReason.UNKNOWN,
                    "v2 快照下载已取消"
            );
        }
    }
}
