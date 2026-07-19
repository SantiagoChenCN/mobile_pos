package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Non-destructive LOCAL barcode collision with an authoritative synchronized product. */
public final class ProductBarcodeConflict {
    private final String barcode;
    private final Product syncProduct;
    private final List<Product> localProducts;

    public ProductBarcodeConflict(String barcode, Product syncProduct, List<Product> localProducts) {
        if (barcode == null || barcode.trim().isEmpty() || syncProduct == null || localProducts == null || localProducts.isEmpty()) {
            throw new IllegalArgumentException("A conflict needs barcode, sync product, and LOCAL products");
        }
        this.barcode = barcode.trim();
        this.syncProduct = syncProduct;
        this.localProducts = Collections.unmodifiableList(new ArrayList<Product>(localProducts));
    }

    public String barcode() { return barcode; }
    public Product syncProduct() { return syncProduct; }
    public List<Product> localProducts() { return localProducts; }
}
