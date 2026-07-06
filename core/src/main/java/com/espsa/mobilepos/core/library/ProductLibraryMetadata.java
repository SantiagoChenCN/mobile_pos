package com.espsa.mobilepos.core.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProductLibraryMetadata {
    private final String lastImportFileName;
    private final String lastImportTimeIso;
    private final int lastImportProductCount;
    private final int lastImportPromotionCount;
    private final boolean manuallyModified;
    private final List<ImportSnapshotInfo> recentImports;

    public ProductLibraryMetadata(
            String lastImportFileName,
            String lastImportTimeIso,
            int lastImportProductCount,
            int lastImportPromotionCount,
            boolean manuallyModified,
            List<ImportSnapshotInfo> recentImports
    ) {
        this.lastImportFileName = lastImportFileName == null ? "" : lastImportFileName.trim();
        this.lastImportTimeIso = lastImportTimeIso == null ? "" : lastImportTimeIso.trim();
        this.lastImportProductCount = Math.max(0, lastImportProductCount);
        this.lastImportPromotionCount = Math.max(0, lastImportPromotionCount);
        this.manuallyModified = manuallyModified;
        this.recentImports = recentImports == null
                ? Collections.<ImportSnapshotInfo>emptyList()
                : Collections.unmodifiableList(new ArrayList<ImportSnapshotInfo>(recentImports));
    }

    public static ProductLibraryMetadata empty() {
        return new ProductLibraryMetadata("", "", 0, 0, false, Collections.<ImportSnapshotInfo>emptyList());
    }

    public ProductLibraryMetadata withManualModification(boolean modified) {
        return new ProductLibraryMetadata(
                lastImportFileName,
                lastImportTimeIso,
                lastImportProductCount,
                lastImportPromotionCount,
                modified,
                recentImports
        );
    }

    public String lastImportFileName() {
        return lastImportFileName;
    }

    public String lastImportTimeIso() {
        return lastImportTimeIso;
    }

    public int lastImportProductCount() {
        return lastImportProductCount;
    }

    public int lastImportPromotionCount() {
        return lastImportPromotionCount;
    }

    public boolean manuallyModified() {
        return manuallyModified;
    }

    public List<ImportSnapshotInfo> recentImports() {
        return recentImports;
    }
}
