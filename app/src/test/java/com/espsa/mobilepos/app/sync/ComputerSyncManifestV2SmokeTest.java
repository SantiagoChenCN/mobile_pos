package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.model.V2Manifest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ComputerSyncManifestV2SmokeTest {
    private static final String SNAPSHOT_ID = "ms2011-20260718T123456Z-0123456789ab";
    private static final byte[] DATABASE = "sqlite-v2-fixture".getBytes(StandardCharsets.UTF_8);
    private static final String DATABASE_SHA256 = sha256(DATABASE);

    public static void main(String[] args) throws Exception {
        preservesOriginalUtf8BytesDefensively();
        ComputerSyncManifestV2 manifest = ComputerSyncManifestV2.fromValidatedManifest(
                new V2Manifest(
                        true, 2, SNAPSHOT_ID, "ms2011_live", "2026-07-18T12:34:56Z",
                        DATABASE.length, DATABASE_SHA256, 1, 0, 0, 0, 0, 0, 0,
                        "/v2/snapshots/" + SNAPSHOT_ID + ".db"
                )
        );
        assertTrue("snapshot ID is retained", SNAPSHOT_ID.equals(manifest.snapshotId()));
        assertTrue("manifest download path is retained", manifest.downloadPath().endsWith(SNAPSHOT_ID + ".db"));
        assertHostManifestValidation();

        expectInvalidRawManifest("empty raw UTF-8", new byte[0]);
        expectInvalidRawManifest("oversized raw UTF-8", oversizedManifest().getBytes(StandardCharsets.UTF_8));
        expectInvalidManifestLength("missing declared Content-Length", null);
        expectInvalidManifestLength("zero declared Content-Length", "0");
        expectInvalidManifestLength("oversized declared Content-Length", "262145");
        ComputerSyncClient.validateManifestContentLength("262144");
        assertTrue("v2 redirect is rejected", !ComputerSyncClient.isSuccessfulV2Status(302));
        assertTrue("v2 partial/other success is rejected", !ComputerSyncClient.isSuccessfulV2Status(206));
        assertTrue("v2 created response is rejected", !ComputerSyncClient.isSuccessfulV2Status(201));
        assertTrue("v2 success is accepted", ComputerSyncClient.isSuccessfulV2Status(200));
        assertTrue("v2 uses Bearer authentication", "Bearer token".equals(
                ComputerSyncClient.v2AuthorizationHeader(new ComputerSyncConfig(
                        "192.168.1.35", 8765, "token", "", "", "", ""
                ))
        ));

        expectInvalidSpace("negative space", manifest, -1L);
        expectInvalidSpace("insufficient reservation", manifest, (long) DATABASE.length * 4L - 1L);
        ComputerSyncService.validateV2DownloadSpace(manifest, (long) DATABASE.length * 4L);

        File directory = Files.createTempDirectory("computer-sync-v2-test-").toFile();
        try {
            File success = new File(directory, "success.part");
            invokeStreamV2Snapshot(
                    manifest, DATABASE.length, DATABASE_SHA256, SNAPSHOT_ID,
                    new ByteArrayInputStream(DATABASE), success
            );
            assertTrue("successful download bytes", DATABASE.length == success.length());
            assertTrue("successful download hash", DATABASE_SHA256.equals(sha256(Files.readAllBytes(success.toPath()))));

            File failed = new File(directory, "failed.part");
            expectDownloadFailure(manifest, failed, DATABASE_SHA256 + "0", SNAPSHOT_ID, DATABASE.length, DATABASE);
            assertTrue("header mismatch temporary file is removed", !failed.exists());

            File lengthMismatch = new File(directory, "length-mismatch.part");
            expectDownloadFailure(manifest, lengthMismatch, DATABASE_SHA256, SNAPSHOT_ID, DATABASE.length + 1L, DATABASE);
            assertTrue("length mismatch temporary file is removed", !lengthMismatch.exists());

            File wrongSnapshot = new File(directory, "wrong-snapshot.part");
            expectDownloadFailure(manifest, wrongSnapshot, DATABASE_SHA256, SNAPSHOT_ID + "0", DATABASE.length, DATABASE);
            assertTrue("wrong snapshot temporary file is removed", !wrongSnapshot.exists());

            File oversized = new File(directory, "oversized.part");
            expectDownloadFailure(manifest, oversized, DATABASE_SHA256, SNAPSHOT_ID, DATABASE.length, concat(DATABASE, new byte[] { 1 }));
            assertTrue("oversized response temporary file is removed", !oversized.exists());

            File truncated = new File(directory, "truncated.part");
            expectDownloadFailure(
                    manifest,
                    truncated,
                    DATABASE_SHA256,
                    SNAPSHOT_ID,
                    DATABASE.length,
                    Arrays.copyOf(DATABASE, DATABASE.length - 1)
            );
            assertTrue("truncated response temporary file is removed", !truncated.exists());

            File corrupted = new File(directory, "corrupted.part");
            byte[] corruptedBody = DATABASE.clone();
            corruptedBody[0] ^= 1;
            expectDownloadFailure(
                    manifest,
                    corrupted,
                    DATABASE_SHA256,
                    SNAPSHOT_ID,
                    DATABASE.length,
                    corruptedBody
            );
            assertTrue("same-length corrupted response is removed", !corrupted.exists());

            expectManifestBodyFailure("manifest body truncation", DATABASE.length, new byte[] { 1 });

            File interrupted = new File(directory, "interrupted.part");
            expectInterrupted(manifest, interrupted);
            assertTrue("interrupted temporary file is removed", !interrupted.exists());

            File cancelled = new File(directory, "cancelled.part");
            expectExplicitCancellation(manifest, cancelled);
            assertTrue("explicit cancellation temporary file is removed", !cancelled.exists());

            File cancelledDuringRead = new File(directory, "cancelled-during-read.part");
            expectCancellationDuringRead(manifest, cancelledDuringRead);
            assertTrue("running cancellation temporary file is removed", !cancelledDuringRead.exists());

            expectCancellationDuringServiceHash();

        } finally {
            deleteDirectory(directory);
        }

        System.out.println("Computer sync v2 manifest smoke test passed");
    }

    private static boolean assertHostManifestValidation() throws Exception {
        Map<String, Object> valid = manifestFields();
        assertTrue("host strict manifest valid", ComputerSyncManifestV2.fromFieldValues(valid).sizeBytes() == DATABASE.length);
        String[] invalid = new String[] { "missing", "extra", "ok-string", "float", "bool-int", "snapshot", "path", "size", "count", "version", "hash", "int32" };
        for (String kind : invalid) {
            Map<String, Object> fields = manifestFields();
            if ("missing".equals(kind)) fields.remove("sha256");
            else if ("extra".equals(kind)) fields.put("extra", Boolean.TRUE);
            else if ("ok-string".equals(kind)) fields.put("ok", "true");
            else if ("float".equals(kind)) fields.put("sizeBytes", Double.valueOf(1.5));
            else if ("bool-int".equals(kind)) fields.put("productCount", Boolean.FALSE);
            else if ("snapshot".equals(kind)) fields.put("snapshotId", "ms2011-20260230T123456Z-0123456789ab");
            else if ("path".equals(kind)) fields.put("downloadPath", "/v2/latest.db");
            else if ("size".equals(kind)) fields.put("sizeBytes", Long.valueOf(-1));
            else if ("count".equals(kind)) fields.put("productCount", Integer.valueOf(-1));
            else if ("version".equals(kind)) fields.put("minimumAppVersion", Integer.valueOf(0));
            else if ("hash".equals(kind)) fields.put("sha256", "bad");
            else fields.put("categoryCount", Long.valueOf(2147483648L));
            try { ComputerSyncManifestV2.fromFieldValues(fields); throw new AssertionError(kind); }
            catch (ComputerSyncException expected) { assertTrue(kind, expected.reason() == ComputerSyncFailureReason.INVALID_RESPONSE); }
        }
        return true;
    }

    private static Map<String, Object> manifestFields() {
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("ok", Boolean.TRUE); fields.put("schemaVersion", Integer.valueOf(2)); fields.put("snapshotId", SNAPSHOT_ID);
        fields.put("sourceType", "ms2011_live"); fields.put("createdAtUtc", "2026-07-18T12:34:56Z"); fields.put("sizeBytes", Long.valueOf(DATABASE.length));
        fields.put("sha256", DATABASE_SHA256); fields.put("minimumAppVersion", Integer.valueOf(1)); fields.put("productCount", Integer.valueOf(0));
        fields.put("categoryCount", Integer.valueOf(0)); fields.put("unitCount", Integer.valueOf(0)); fields.put("promotionCandidateCount", Integer.valueOf(0));
        fields.put("verifiedPromotionCount", Integer.valueOf(0)); fields.put("validationIssueCount", Integer.valueOf(0)); fields.put("downloadPath", "/v2/snapshots/" + SNAPSHOT_ID + ".db");
        return fields;
    }

    private static void expectManifestBodyFailure(String label, long declared, byte[] body) throws Exception {
        try { ComputerSyncClient.readManifestResponse(new ByteArrayInputStream(body), declared); throw new AssertionError(label); }
        catch (ComputerSyncException expected) { assertTrue(label, expected.reason() == ComputerSyncFailureReason.INVALID_RESPONSE); }
    }

    private static void expectDownloadFailure(
            ComputerSyncManifestV2 manifest,
            File target,
            String headerHash,
            String headerSnapshotId,
            long contentLength,
            byte[] response
    ) throws Exception {
        try {
            invokeStreamV2Snapshot(
                    manifest, contentLength, headerHash, headerSnapshotId,
                    new ByteArrayInputStream(response), target
            );
            throw new AssertionError("download metadata mismatch cleans temporary file");
        } catch (ComputerSyncException expected) {
            assertTrue("download error is invalid response", expected.reason() == ComputerSyncFailureReason.INVALID_RESPONSE);
        }
    }

    private static void preservesOriginalUtf8BytesDefensively() throws Exception {
        String source = new String(Files.readAllBytes(new File(
                "app/src/main/java/com/espsa/mobilepos/app/sync/ComputerSyncManifestV2.java"
        ).toPath()), StandardCharsets.UTF_8);
        assertTrue("raw parser makes a defensive UTF-8 copy", source.contains(
                "Arrays.copyOf(bytes, bytes.length)"));
        assertTrue("raw bytes are stored defensively", source.contains(
                "Arrays.copyOf(originalUtf8Bytes, originalUtf8Bytes.length)"));
        assertTrue("raw bytes are exposed defensively", source.contains(
                "return originalUtf8Bytes == null ? null : Arrays.copyOf(originalUtf8Bytes, originalUtf8Bytes.length)"));
    }

    private static void expectInterrupted(ComputerSyncManifestV2 manifest, File target) throws Exception {
        try {
            Thread.currentThread().interrupt();
            invokeStreamV2Snapshot(
                    manifest, DATABASE.length, DATABASE_SHA256, SNAPSHOT_ID,
                    new ByteArrayInputStream(DATABASE), target
            );
            throw new AssertionError("interrupted v2 stream");
        } catch (ComputerSyncException expected) {
            assertTrue("interrupted stream is cancelled", expected.reason() == ComputerSyncFailureReason.UNKNOWN);
        } finally {
            Thread.interrupted();
        }
    }

    private static void expectExplicitCancellation(ComputerSyncManifestV2 manifest, File target) throws Exception {
        ComputerSyncClient.V2DownloadCancellation cancellation = new ComputerSyncClient.V2DownloadCancellation();
        cancellation.cancel();
        try {
            invokeStreamV2Snapshot(
                    manifest, DATABASE.length, DATABASE_SHA256, SNAPSHOT_ID,
                    new ByteArrayInputStream(DATABASE), target, cancellation
            );
            throw new AssertionError("explicit cancellation v2 stream");
        } catch (ComputerSyncException expected) {
            assertTrue("explicit cancellation is cancelled", expected.reason() == ComputerSyncFailureReason.UNKNOWN);
        }
    }

    private static void expectCancellationDuringRead(
            ComputerSyncManifestV2 manifest,
            File target
    ) throws Exception {
        ComputerSyncClient.V2DownloadCancellation cancellation =
                new ComputerSyncClient.V2DownloadCancellation();
        try {
            invokeStreamV2Snapshot(
                    manifest,
                    DATABASE.length,
                    DATABASE_SHA256,
                    SNAPSHOT_ID,
                    new CancellingInputStream(DATABASE, cancellation),
                    target,
                    cancellation
            );
            throw new AssertionError("running cancellation stops v2 stream");
        } catch (ComputerSyncException expected) {
            assertTrue(
                    "running cancellation reason",
                    expected.reason() == ComputerSyncFailureReason.UNKNOWN
            );
        }
    }

    private static void expectCancellationDuringServiceHash() throws Exception {
        ComputerSyncClient.V2DownloadCancellation cancellation =
                new ComputerSyncClient.V2DownloadCancellation();
        try {
            ComputerSyncService.sha256Stream(
                    new CancellingInputStream(DATABASE, cancellation),
                    cancellation
            );
            throw new AssertionError("running cancellation stops service hash");
        } catch (ComputerSyncException expected) {
            assertTrue(
                    "service hash cancellation reason",
                    expected.reason() == ComputerSyncFailureReason.UNKNOWN
            );
        }
    }

    private static final class CancellingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private final ComputerSyncClient.V2DownloadCancellation cancellation;
        private boolean cancelled;

        CancellingInputStream(
                byte[] bytes,
                ComputerSyncClient.V2DownloadCancellation cancellation
        ) {
            this.delegate = new ByteArrayInputStream(bytes);
            this.cancellation = cancellation;
        }

        @Override
        public int read() {
            int value = delegate.read();
            cancelAfterRead(value < 0 ? 0 : 1);
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            int count = delegate.read(buffer, offset, length);
            cancelAfterRead(count);
            return count;
        }

        private void cancelAfterRead(int count) {
            if (!cancelled && count > 0) {
                cancelled = true;
                cancellation.cancel();
            }
        }
    }

    private static void invokeStreamV2Snapshot(
            ComputerSyncManifestV2 manifest,
            long contentLength,
            String headerSha256,
            String headerSnapshotId,
            InputStream response,
            File temporaryTarget
    ) throws Exception {
        invokeStreamV2Snapshot(
                manifest,
                contentLength,
                headerSha256,
                headerSnapshotId,
                response,
                temporaryTarget,
                new ComputerSyncClient.V2DownloadCancellation()
        );
    }

    private static void invokeStreamV2Snapshot(
            ComputerSyncManifestV2 manifest,
            long contentLength,
            String headerSha256,
            String headerSnapshotId,
            InputStream response,
            File temporaryTarget,
            ComputerSyncClient.V2DownloadCancellation cancellation
    ) throws Exception {
        Method method = ComputerSyncClient.class.getDeclaredMethod(
                "streamV2Snapshot",
                ComputerSyncManifestV2.class,
                long.class,
                String.class,
                String.class,
                InputStream.class,
                File.class,
                ComputerSyncClient.V2DownloadCancellation.class
        );
        method.setAccessible(true);
        try {
            method.invoke(
                    null,
                    manifest,
                    contentLength,
                    headerSha256,
                    headerSnapshotId,
                    response,
                    temporaryTarget,
                    cancellation
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ComputerSyncException) {
                throw (ComputerSyncException) cause;
            }
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw exception;
        }
    }

    private static void expectInvalidRawManifest(String label, byte[] raw) throws Exception {
        try {
            ComputerSyncManifestV2.validateManifestBytes(raw);
            throw new AssertionError(label);
        } catch (ComputerSyncException expected) {
            assertTrue(label + " reason", expected.reason() == ComputerSyncFailureReason.INVALID_RESPONSE);
        }
    }

    private static void expectInvalidManifestLength(String label, String value) throws Exception {
        try {
            ComputerSyncClient.validateManifestContentLength(value);
            throw new AssertionError(label);
        } catch (ComputerSyncException expected) {
            assertTrue(label + " reason", expected.reason() == ComputerSyncFailureReason.INVALID_RESPONSE);
        }
    }

    private static void expectInvalidSpace(String label, ComputerSyncManifestV2 manifest, long available) throws Exception {
        try {
            ComputerSyncService.validateV2DownloadSpace(manifest, available);
            throw new AssertionError(label);
        } catch (ComputerSyncException expected) {
            assertTrue(label + " reason", expected.reason() == ComputerSyncFailureReason.INVALID_RESPONSE);
        }
    }

    private static String manifestJson(long sizeBytes, String hash) {
        return "{"
                + "\"ok\":true,"
                + "\"schemaVersion\":2,"
                + "\"snapshotId\":\"" + SNAPSHOT_ID + "\","
                + "\"sourceType\":\"ms2011_live\","
                + "\"createdAtUtc\":\"2026-07-18T12:34:56Z\","
                + "\"sizeBytes\":" + sizeBytes + ","
                + "\"sha256\":\"" + hash + "\","
                + "\"minimumAppVersion\":1,"
                + "\"productCount\":0,"
                + "\"categoryCount\":0,"
                + "\"unitCount\":0,"
                + "\"promotionCandidateCount\":0,"
                + "\"verifiedPromotionCount\":0,"
                + "\"validationIssueCount\":0,"
                + "\"downloadPath\":\"/v2/snapshots/" + SNAPSHOT_ID + ".db\""
                + "}";
    }

    private static String oversizedManifest() {
        String valid = manifestJson(DATABASE.length, DATABASE_SHA256);
        StringBuilder result = new StringBuilder(valid);
        while (result.toString().getBytes(StandardCharsets.UTF_8).length <= 256 * 1024) {
            result.append(' ');
        }
        return result.toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static void deleteDirectory(File directory) {
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                child.delete();
            }
        }
        directory.delete();
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
