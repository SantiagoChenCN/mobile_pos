package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.model.Product;

public final class ProductCreateResult {
    private final Product product;
    private final ProductValidationResult validation;
    private final boolean duplicateName;

    public ProductCreateResult(Product product, ProductValidationResult validation, boolean duplicateName) {
        this.product = product;
        this.validation = validation;
        this.duplicateName = duplicateName;
    }

    public boolean success() {
        return product != null && validation != null && validation.valid();
    }

    public Product product() {
        return product;
    }

    public ProductValidationResult validation() {
        return validation;
    }

    public boolean duplicateName() {
        return duplicateName;
    }
}
