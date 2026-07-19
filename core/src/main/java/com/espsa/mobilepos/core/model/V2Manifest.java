package com.espsa.mobilepos.core.model;

import java.time.Instant;
import java.util.regex.Pattern;

public final class V2Manifest {
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final String snapshotId;
    private final String createdAtUtc;
    private final long sizeBytes;
    private final String sha256;
    private final int minimumAppVersion;
    private final int productCount;
    private final int categoryCount;
    private final int unitCount;
    private final int promotionCandidateCount;
    private final int verifiedPromotionCount;
    private final int validationIssueCount;
    private final String downloadPath;

    public V2Manifest(
            boolean ok, int schemaVersion, String snapshotId, String sourceType,
            String createdAtUtc, long sizeBytes, String sha256, long minimumAppVersion,
            int productCount, long categoryCount, long unitCount, int promotionCandidateCount,
            int verifiedPromotionCount, int validationIssueCount, String downloadPath
    ) {
        if (!ok || schemaVersion != V2Contract.SCHEMA_VERSION || !V2Contract.SOURCE_TYPE.equals(sourceType)) {
            throw new IllegalArgumentException("Invalid manifest identity fields");
        }
        this.snapshotId = V2Contract.validateSnapshotId(snapshotId);
        validateUtc(createdAtUtc);
        if (sizeBytes < 0 || sizeBytes > V2Contract.SNAPSHOT_SOFT_BYTES
                || minimumAppVersion < 1 || minimumAppVersion > Integer.MAX_VALUE
                || productCount < 0 || productCount > V2Contract.PRODUCT_SOFT_COUNT
                || categoryCount < 0 || categoryCount > Integer.MAX_VALUE
                || unitCount < 0 || unitCount > Integer.MAX_VALUE
                || promotionCandidateCount < 0 || promotionCandidateCount > V2Contract.PROMOTION_SOFT_COUNT
                || verifiedPromotionCount < 0 || verifiedPromotionCount > promotionCandidateCount
                || validationIssueCount < 0 || validationIssueCount > V2Contract.ISSUE_SOFT_COUNT) {
            throw new IllegalArgumentException("Manifest numeric field is outside the contract");
        }
        if (sha256 == null || !SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256");
        }
        if (!V2Contract.downloadPath(snapshotId).equals(downloadPath)) {
            throw new IllegalArgumentException("downloadPath does not match snapshotId");
        }
        this.createdAtUtc = createdAtUtc;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.minimumAppVersion = (int) minimumAppVersion;
        this.productCount = productCount;
        this.categoryCount = (int) categoryCount;
        this.unitCount = (int) unitCount;
        this.promotionCandidateCount = promotionCandidateCount;
        this.verifiedPromotionCount = verifiedPromotionCount;
        this.validationIssueCount = validationIssueCount;
        this.downloadPath = downloadPath;
    }

    public String snapshotId() { return snapshotId; }
    public String createdAtUtc() { return createdAtUtc; }
    public long sizeBytes() { return sizeBytes; }
    public String sha256() { return sha256; }
    public int minimumAppVersion() { return minimumAppVersion; }
    public int productCount() { return productCount; }
    public int categoryCount() { return categoryCount; }
    public int unitCount() { return unitCount; }
    public int promotionCandidateCount() { return promotionCandidateCount; }
    public int verifiedPromotionCount() { return verifiedPromotionCount; }
    public int validationIssueCount() { return validationIssueCount; }
    public String downloadPath() { return downloadPath; }

    private static void validateUtc(String value) {
        if (value == null || !value.endsWith("Z")) {
            throw new IllegalArgumentException("createdAtUtc must be UTC");
        }
        try {
            Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("createdAtUtc must be a UTC instant", exception);
        }
    }
}
