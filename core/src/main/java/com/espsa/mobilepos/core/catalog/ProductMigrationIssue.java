package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.ProductOrigin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** A legacy product whose provenance cannot be safely inferred. */
public final class ProductMigrationIssue {
    private static final List<ProductOrigin> ALLOWED_CHOICES = Collections.unmodifiableList(
            Arrays.asList(ProductOrigin.LOCAL, ProductOrigin.LEGACY_IMPORT)
    );

    private final String productId;
    private final String barcode;
    private final String reason;

    public ProductMigrationIssue(String productId, String barcode, String reason) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("reason is required");
        }
        this.productId = productId.trim();
        this.barcode = barcode == null ? "" : barcode.trim();
        this.reason = reason.trim();
    }

    public String productId() { return productId; }

    public String barcode() { return barcode; }

    public String reason() { return reason; }

    public List<ProductOrigin> allowedChoices() { return ALLOWED_CHOICES; }
}
