package com.espsa.mobilepos.core.library;

import com.espsa.mobilepos.core.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProductLibraryState {
    private final List<Product> products;
    private final ProductLibraryMetadata metadata;

    public ProductLibraryState(List<Product> products, ProductLibraryMetadata metadata) {
        this.products = products == null
                ? Collections.<Product>emptyList()
                : Collections.unmodifiableList(new ArrayList<Product>(products));
        this.metadata = metadata == null ? ProductLibraryMetadata.empty() : metadata;
    }

    public static ProductLibraryState empty() {
        return new ProductLibraryState(Collections.<Product>emptyList(), ProductLibraryMetadata.empty());
    }

    public List<Product> products() {
        return products;
    }

    public ProductLibraryMetadata metadata() {
        return metadata;
    }
}
