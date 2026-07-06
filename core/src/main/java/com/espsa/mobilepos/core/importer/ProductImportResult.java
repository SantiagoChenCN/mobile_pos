package com.espsa.mobilepos.core.importer;

import com.espsa.mobilepos.core.model.Product;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProductImportResult {
    private final String sourceFileName;
    private final Instant importedAt;
    private final List<Product> products;
    private final int promotionCount;
    private final List<String> warnings;

    public ProductImportResult(String sourceFileName, Instant importedAt, List<Product> products, int promotionCount, List<String> warnings) {
        this.sourceFileName = sourceFileName == null ? "" : sourceFileName;
        this.importedAt = importedAt == null ? Instant.now() : importedAt;
        this.products = products == null ? Collections.<Product>emptyList() : Collections.unmodifiableList(new ArrayList<Product>(products));
        this.promotionCount = Math.max(0, promotionCount);
        this.warnings = warnings == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<String>(warnings));
    }

    public String sourceFileName() {
        return sourceFileName;
    }

    public Instant importedAt() {
        return importedAt;
    }

    public List<Product> products() {
        return products;
    }

    public int productCount() {
        return products.size();
    }

    public int promotionCount() {
        return promotionCount;
    }

    public List<String> warnings() {
        return warnings;
    }
}

