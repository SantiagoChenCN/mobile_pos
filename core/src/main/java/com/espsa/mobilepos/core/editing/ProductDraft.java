package com.espsa.mobilepos.core.editing;

public final class ProductDraft {
    private final String barcode;
    private final String name;
    private final String category;
    private final String unitName;
    private final String salePriceText;
    private final String promotionPriceText;
    private final String promotionMinQuantityText;

    public ProductDraft(
            String barcode,
            String name,
            String category,
            String unitName,
            String salePriceText,
            String promotionPriceText,
            String promotionMinQuantityText
    ) {
        this.barcode = clean(barcode);
        this.name = clean(name);
        this.category = clean(category);
        this.unitName = clean(unitName);
        this.salePriceText = clean(salePriceText);
        this.promotionPriceText = clean(promotionPriceText);
        this.promotionMinQuantityText = clean(promotionMinQuantityText);
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

    public String salePriceText() {
        return salePriceText;
    }

    public String promotionPriceText() {
        return promotionPriceText;
    }

    public String promotionMinQuantityText() {
        return promotionMinQuantityText;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
