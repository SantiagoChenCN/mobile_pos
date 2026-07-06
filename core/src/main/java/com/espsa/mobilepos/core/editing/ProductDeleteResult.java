package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.model.Product;

public final class ProductDeleteResult {
    private final Product deletedProduct;

    public ProductDeleteResult(Product deletedProduct) {
        this.deletedProduct = deletedProduct;
    }

    public boolean success() {
        return deletedProduct != null;
    }

    public Product deletedProduct() {
        return deletedProduct;
    }
}
