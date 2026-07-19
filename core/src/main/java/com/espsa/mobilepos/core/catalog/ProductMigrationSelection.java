package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.ProductOrigin;

/** Explicit human selection for one issue; sync provenance is intentionally not selectable here. */
public final class ProductMigrationSelection {
    private final String productId;
    private final ProductOrigin selectedOrigin;

    public ProductMigrationSelection(String productId, ProductOrigin selectedOrigin) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (selectedOrigin != ProductOrigin.LOCAL && selectedOrigin != ProductOrigin.LEGACY_IMPORT) {
            throw new IllegalArgumentException("Only LOCAL or LEGACY_IMPORT may be selected");
        }
        this.productId = productId.trim();
        this.selectedOrigin = selectedOrigin;
    }

    public String productId() { return productId; }

    public ProductOrigin selectedOrigin() { return selectedOrigin; }
}
