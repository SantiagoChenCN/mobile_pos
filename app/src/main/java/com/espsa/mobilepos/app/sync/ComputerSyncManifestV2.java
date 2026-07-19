package com.espsa.mobilepos.app.sync;

import com.espsa.mobilepos.core.model.V2Contract;
import com.espsa.mobilepos.core.model.V2Manifest;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Strict Android adapter for the frozen CT-02/CT-03 v2 manifest contract. */
public final class ComputerSyncManifestV2 {
    private static final String[] REQUIRED_FIELDS = new String[] {
            "ok", "schemaVersion", "snapshotId", "sourceType", "createdAtUtc", "sizeBytes",
            "sha256", "minimumAppVersion", "productCount", "categoryCount", "unitCount",
            "promotionCandidateCount", "verifiedPromotionCount", "validationIssueCount", "downloadPath"
    };
    private static final Set<String> REQUIRED_FIELD_SET = new HashSet<String>(Arrays.asList(REQUIRED_FIELDS));

    private final V2Manifest manifest;
    private final byte[] originalUtf8Bytes;

    private ComputerSyncManifestV2(V2Manifest manifest) {
        this(manifest, null);
    }

    private ComputerSyncManifestV2(V2Manifest manifest, byte[] originalUtf8Bytes) {
        this.manifest = manifest;
        this.originalUtf8Bytes = originalUtf8Bytes == null ? null : Arrays.copyOf(originalUtf8Bytes, originalUtf8Bytes.length);
    }

    public static ComputerSyncManifestV2 fromUtf8Json(byte[] bytes) throws ComputerSyncException {
        try {
            byte[] defensiveCopy = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
            CharBuffer decoded = validateManifestBytes(defensiveCopy);
            ComputerSyncManifestV2 parsed = fromJson(new JSONObject(decoded.toString()));
            return new ComputerSyncManifestV2(parsed.manifest, defensiveCopy);
        } catch (JSONException exception) {
            throw invalidManifest();
        }
    }

    public static ComputerSyncManifestV2 fromJson(JSONObject object) throws ComputerSyncException {
        if (object == null) {
            throw invalidManifest();
        }
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try { values.put(key, object.isNull(key) ? null : object.get(key)); }
            catch (JSONException exception) { throw invalidManifest(); }
        }
        return fromFieldValues(values);
    }

    static ComputerSyncManifestV2 fromFieldValues(Map<String, Object> values) throws ComputerSyncException {
        if (values == null || values.size() != REQUIRED_FIELDS.length || !values.keySet().equals(REQUIRED_FIELD_SET)) throw invalidManifest();
        try {
            Object ok = values.get("ok");
            if (!Boolean.TRUE.equals(ok)) {
                throw invalidManifest();
            }
            V2Manifest parsed = new V2Manifest(
                    true,
                    requireExactInt(values.get("schemaVersion"), 0, Integer.MAX_VALUE), requireString(values.get("snapshotId")), requireString(values.get("sourceType")), requireString(values.get("createdAtUtc")), requireExactLong(values.get("sizeBytes"), 0L, V2Contract.SNAPSHOT_SOFT_BYTES), requireString(values.get("sha256")), requireExactInt(values.get("minimumAppVersion"), 1, Integer.MAX_VALUE), requireExactInt(values.get("productCount"), 0, V2Contract.PRODUCT_SOFT_COUNT), requireExactInt(values.get("categoryCount"), 0, Integer.MAX_VALUE), requireExactInt(values.get("unitCount"), 0, Integer.MAX_VALUE), requireExactInt(values.get("promotionCandidateCount"), 0, V2Contract.PROMOTION_SOFT_COUNT), requireExactInt(values.get("verifiedPromotionCount"), 0, V2Contract.PROMOTION_SOFT_COUNT), requireExactInt(values.get("validationIssueCount"), 0, V2Contract.ISSUE_SOFT_COUNT), requireString(values.get("downloadPath"))
            );
            return new ComputerSyncManifestV2(parsed);
        } catch (IllegalArgumentException exception) {
            throw invalidManifest();
        }
    }

    static ComputerSyncManifestV2 fromValidatedManifest(V2Manifest manifest) throws ComputerSyncException {
        if (manifest == null) {
            throw invalidManifest();
        }
        return new ComputerSyncManifestV2(manifest);
    }

    static CharBuffer validateManifestBytes(byte[] bytes) throws ComputerSyncException {
        if (bytes == null || bytes.length == 0 || bytes.length > V2Contract.MANIFEST_SOFT_BYTES) {
            throw invalidManifest();
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException exception) {
            throw invalidManifest();
        }
    }

    public V2Manifest coreManifest() {
        return manifest;
    }

    /** Package-private only: V2SnapshotStore requires exact wire bytes for immutable pair persistence. */
    byte[] originalUtf8Bytes() {
        return originalUtf8Bytes == null ? null : Arrays.copyOf(originalUtf8Bytes, originalUtf8Bytes.length);
    }

    public String snapshotId() { return manifest.snapshotId(); }
    public String createdAtUtc() { return manifest.createdAtUtc(); }
    public long sizeBytes() { return manifest.sizeBytes(); }
    public String sha256() { return manifest.sha256(); }
    public int minimumAppVersion() { return manifest.minimumAppVersion(); }
    public int productCount() { return manifest.productCount(); }
    public int categoryCount() { return manifest.categoryCount(); }
    public int unitCount() { return manifest.unitCount(); }
    public int promotionCandidateCount() { return manifest.promotionCandidateCount(); }
    public int verifiedPromotionCount() { return manifest.verifiedPromotionCount(); }
    public int validationIssueCount() { return manifest.validationIssueCount(); }
    public String downloadPath() { return manifest.downloadPath(); }

    private static String requireString(Object value) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Expected JSON string");
        }
        return (String) value;
    }

    private static int requireExactInt(Object value, int minimum, int maximum) {
        long parsed = requireExactLong(value, minimum, maximum);
        return (int) parsed;
    }

    private static long requireExactLong(Object value, long minimum, long maximum) {
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new IllegalArgumentException("Expected JSON integer");
        }
        long parsed = ((Number) value).longValue();
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException("JSON integer is outside the contract");
        }
        return parsed;
    }

    private static ComputerSyncException invalidManifest() {
        return new ComputerSyncException(
                ComputerSyncFailureReason.INVALID_RESPONSE,
                "电脑同步工具返回的 v2 manifest 无效"
        );
    }
}
