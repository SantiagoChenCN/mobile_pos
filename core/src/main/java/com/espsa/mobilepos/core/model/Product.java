package com.espsa.mobilepos.core.model;

import java.util.Objects;

public final class Product {
    public static final String MANUAL_ALMACEN_CATEGORY = "almacen";

    private final String id;
    private final String barcode;
    private final String name;
    private final String category;
    private final String unitName;
    private final Money salePrice;
    private final Money promotionPrice;
    private final int promotionMinQuantity;
    private final boolean manualPriceProduct;

    public Product(
            String id,
            String barcode,
            String name,
            String category,
            String unitName,
            Money salePrice,
            Money promotionPrice,
            int promotionMinQuantity,
            boolean manualPriceProduct
    ) {
        this.id = requireText(id, "id");
        this.barcode = barcode == null ? "" : barcode.trim();
        this.name = requireText(name, "name");
        this.category = category == null || category.trim().isEmpty() ? MANUAL_ALMACEN_CATEGORY : category.trim();
        this.unitName = unitName == null ? "" : unitName.trim();
        this.salePrice = Objects.requireNonNull(salePrice, "salePrice");
        this.promotionPrice = promotionPrice;
        this.promotionMinQuantity = Math.max(0, promotionMinQuantity);
        this.manualPriceProduct = manualPriceProduct;
    }

    public static Product manualAlmacen(String id, Money price) {
        return new Product(id, "", "almacen", MANUAL_ALMACEN_CATEGORY, "un", price, null, 0, true);
    }

    public String id() {
        return id;
    }

    public String barcode() {
        return barcode;
    }

    public String name() {
        return name;
    }

    public String category() {
        return category;
    }

    public String unitName() {
        return unitName;
    }

    public Money salePrice() {
        return salePrice;
    }

    public Money promotionPrice() {
        return promotionPrice;
    }

    public int promotionMinQuantity() {
        return promotionMinQuantity;
    }

    public boolean isManualPriceProduct() {
        return manualPriceProduct;
    }

    public boolean hasQuantityPromotion() {
        return promotionPrice != null && promotionPrice.amount() > 0 && promotionMinQuantity > 0;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}

