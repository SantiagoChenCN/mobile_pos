package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProductUpdateResult {
    private final Product originalProduct;
    private final Product updatedProduct;
    private final ProductValidationResult validation;
    private final List<ProductChange> changes;
    private final boolean criticalChanges;

    public ProductUpdateResult(
            Product originalProduct,
            Product updatedProduct,
            ProductValidationResult validation,
            List<ProductChange> changes,
            boolean criticalChanges
    ) {
        this.originalProduct = originalProduct;
        this.updatedProduct = updatedProduct;
        this.validation = validation;
        this.changes = changes == null
                ? Collections.<ProductChange>emptyList()
                : Collections.unmodifiableList(new ArrayList<ProductChange>(changes));
        this.criticalChanges = criticalChanges;
    }

    public boolean success() {
        return updatedProduct != null && validation != null && validation.valid();
    }

    public Product originalProduct() {
        return originalProduct;
    }

    public Product updatedProduct() {
        return updatedProduct;
    }

    public ProductValidationResult validation() {
        return validation;
    }

    public List<ProductChange> changes() {
        return changes;
    }

    public boolean criticalChanges() {
        return criticalChanges;
    }
}
