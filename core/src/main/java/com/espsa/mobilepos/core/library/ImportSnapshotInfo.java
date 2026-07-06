package com.espsa.mobilepos.core.library;

import java.util.Objects;

public final class ImportSnapshotInfo {
    private final String snapshotId;
    private final String fileName;
    private final String importedAtIso;
    private final int productCount;
    private final int promotionCount;

    public ImportSnapshotInfo(
            String snapshotId,
            String fileName,
            String importedAtIso,
            int productCount,
            int promotionCount
    ) {
        this.snapshotId = requireText(snapshotId, "snapshotId");
        this.fileName = fileName == null ? "" : fileName.trim();
        this.importedAtIso = requireText(importedAtIso, "importedAtIso");
        this.productCount = Math.max(0, productCount);
        this.promotionCount = Math.max(0, promotionCount);
    }

    public String snapshotId() {
        return snapshotId;
    }

    public String fileName() {
        return fileName;
    }

    public String importedAtIso() {
        return importedAtIso;
    }

    public int productCount() {
        return productCount;
    }

    public int promotionCount() {
        return promotionCount;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ImportSnapshotInfo)) {
            return false;
        }
        ImportSnapshotInfo other = (ImportSnapshotInfo) obj;
        return snapshotId.equals(other.snapshotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotId);
    }
}
