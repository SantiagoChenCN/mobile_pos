package com.espsa.mobilepos.core.editing;

import com.espsa.mobilepos.core.model.Money;

public final class ParsedProductDraft {
    private final String barcode;
    private final String name;
    private final String category;
    private final String unitName;
    private final Money salePrice;
    private final Money promotionPrice;
    private final int promotionMinQuantity;

    public ParsedProductDraft(
            String barcode,
            String name,
            String category,
            String unitName,
            Money salePrice,
            Money promotionPrice,
            int promotionMinQuantity
    ) {
        this.barcode = barcode;
        this.name = name;
        this.category = category;
        this.unitName = unitName;
        this.salePrice = salePrice;
        this.promotionPrice = promotionPrice;
        this.promotionMinQuantity = promotionMinQuantity;
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
}
