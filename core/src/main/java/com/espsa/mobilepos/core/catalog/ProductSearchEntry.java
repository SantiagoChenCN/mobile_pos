package com.espsa.mobilepos.core.catalog;

import com.espsa.mobilepos.core.model.Product;

final class ProductSearchEntry {
    private final Product product;
    private final String normalizedName;
    private final String normalizedBarcode;
    private final String normalizedCategory;
    private final String normalizedUnitName;
    private final String searchable;
    private final String[] nameTokens;

    ProductSearchEntry(
            Product product,
            String normalizedName,
            String normalizedBarcode,
            String normalizedCategory,
            String normalizedUnitName,
            String[] nameTokens
    ) {
        this.product = product;
        this.normalizedName = normalizedName;
        this.normalizedBarcode = normalizedBarcode;
        this.normalizedCategory = normalizedCategory;
        this.normalizedUnitName = normalizedUnitName;
        this.searchable = normalizedName + " " + normalizedBarcode + " " + normalizedCategory + " " + normalizedUnitName;
        this.nameTokens = nameTokens;
    }

    Product product() {
        return product;
    }

    String normalizedName() {
        return normalizedName;
    }

    String normalizedBarcode() {
        return normalizedBarcode;
    }

    String normalizedCategory() {
        return normalizedCategory;
    }

    String searchable() {
        return searchable;
    }

    String[] nameTokens() {
        return nameTokens;
    }
}
