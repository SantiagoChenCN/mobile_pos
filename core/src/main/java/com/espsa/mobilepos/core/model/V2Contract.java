package com.espsa.mobilepos.core.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class V2Contract {
    public static final int SCHEMA_VERSION = 2;
    public static final String SOURCE_TYPE = "ms2011_live";
    public static final long MANIFEST_SOFT_BYTES = 256L * 1024L;
    public static final long SNAPSHOT_SOFT_BYTES = 256L * 1024L * 1024L;
    public static final int PRODUCT_SOFT_COUNT = 250_000;
    public static final int PROMOTION_SOFT_COUNT = 50_000;
    public static final int ISSUE_SOFT_COUNT = 10_000;

    private static final Pattern SNAPSHOT = Pattern.compile("^ms2011-([0-9]{8}T[0-9]{6}Z)-([0-9a-f]{12})$");
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final DateTimeFormatter BASIC_UTC = DateTimeFormatter
            .ofPattern("uuuuMMdd'T'HHmmss'Z'")
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneOffset.UTC);
    private static final Map<String, String> PREFIXES;

    static {
        Map<String, String> prefixes = new HashMap<String, String>();
        prefixes.put("candidate", "pc");
        prefixes.put("mapping", "map");
        prefixes.put("tier", "tier");
        prefixes.put("schedule", "schedule");
        prefixes.put("group", "group");
        PREFIXES = Collections.unmodifiableMap(prefixes);
    }

    private V2Contract() {
    }

    public static String sourceProductKey(long gid) {
        if (gid <= 0) {
            throw new IllegalArgumentException("GID must be positive");
        }
        return "ms2011:" + gid;
    }

    public static String snapshotId(Instant createdAtUtc, String sourceHash) {
        if (createdAtUtc == null || sourceHash == null || !SHA256.matcher(sourceHash).matches()) {
            throw new IllegalArgumentException("Invalid snapshot inputs");
        }
        return "ms2011-" + BASIC_UTC.format(createdAtUtc) + "-" + sourceHash.substring(0, 12);
    }

    public static String validateSnapshotId(String value) {
        Matcher matcher = value == null ? null : SNAPSHOT.matcher(value);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException("Invalid snapshotId");
        }
        try {
            Instant.from(BASIC_UTC.parse(matcher.group(1)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid snapshot timestamp", exception);
        }
        return value;
    }

    public static String derivedId(String kind, String sourceType, String canonicalSourceKey) {
        String prefix = PREFIXES.get(kind);
        if (prefix == null || sourceType == null || sourceType.isEmpty()
                || canonicalSourceKey == null || canonicalSourceKey.isEmpty()) {
            throw new IllegalArgumentException("Invalid derived ID inputs");
        }
        return prefix + "-" + sha256(sourceType + "\n" + canonicalSourceKey).substring(0, 24);
    }

    public static String downloadPath(String snapshotId) {
        return "/v2/snapshots/" + validateSnapshotId(snapshotId) + ".db";
    }

    public static void requireDownloadSpace(long available, long incoming, long active, long pending, long rollback) {
        if (available < 0 || incoming < 0 || active < 0 || pending < 0 || rollback < 0) {
            throw new IllegalArgumentException("Disk-space values cannot be negative");
        }
        long required;
        try {
            required = Math.addExact(Math.addExact(incoming, active), Math.addExact(pending, rollback));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Disk-space values overflow", exception);
        }
        if (available < required) {
            throw new IllegalArgumentException("Insufficient disk space");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
