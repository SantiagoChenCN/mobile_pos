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
    private final ProductOrigin origin;
    private final String sourceProductKey;
    private final String sourceSnapshotId;
    private final boolean stopped;

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
        this(id, barcode, name, category, unitName, salePrice, promotionPrice, promotionMinQuantity,
                manualPriceProduct, ProductOrigin.LEGACY_IMPORT, "", "", false);
    }

    public Product(
            String id,
            String barcode,
            String name,
            String category,
            String unitName,
            Money salePrice,
            Money promotionPrice,
            int promotionMinQuantity,
            boolean manualPriceProduct,
            ProductOrigin origin,
            String sourceProductKey,
            String sourceSnapshotId,
            boolean stopped
    ) {
        this.id = requireText(id, "id");
        this.barcode = barcode == null ? "" : barcode.trim();
        this.name = requireText(name, "name");
        this.category = normalizeCategory(category, origin);
        this.unitName = unitName == null ? "" : unitName.trim();
        this.salePrice = Objects.requireNonNull(salePrice, "salePrice");
        this.promotionPrice = promotionPrice;
        this.promotionMinQuantity = Math.max(0, promotionMinQuantity);
        this.manualPriceProduct = manualPriceProduct;
        this.origin = Objects.requireNonNull(origin, "origin");
        this.sourceProductKey = normalizeSourceProductKey(origin, sourceProductKey);
        this.sourceSnapshotId = sourceSnapshotId == null ? "" : sourceSnapshotId.trim();
        this.stopped = stopped;
    }

    public static Product manualAlmacen(String id, Money price) {
        return new Product(id, "", "almacen", MANUAL_ALMACEN_CATEGORY, "un", price, null, 0, true,
                ProductOrigin.LOCAL, "", "", false);
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

    public ProductOrigin origin() { return origin; }

    public String sourceProductKey() { return sourceProductKey; }

    public String sourceSnapshotId() { return sourceSnapshotId; }

    public boolean stopped() { return stopped; }

    public Product resolveLegacyOrigin(ProductOrigin nextOrigin) {
        if (origin != ProductOrigin.LEGACY_IMPORT) {
            throw new IllegalStateException("Only LEGACY_IMPORT provenance may be resolved by a human");
        }
        if (nextOrigin != ProductOrigin.LOCAL && nextOrigin != ProductOrigin.LEGACY_IMPORT) {
            throw new IllegalArgumentException("Legacy provenance may resolve only to LOCAL or LEGACY_IMPORT");
        }
        return new Product(id, barcode, name, category, unitName, salePrice, promotionPrice,
                promotionMinQuantity, manualPriceProduct, nextOrigin, "", "", stopped);
    }

    public boolean hasQuantityPromotion() {
        return promotionPrice != null && !promotionPrice.isZero() && promotionMinQuantity > 0;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeSourceProductKey(ProductOrigin origin, String sourceProductKey) {
        String value = sourceProductKey == null ? "" : sourceProductKey.trim();
        if (origin != ProductOrigin.MS2011_SYNC) {
            if (!value.isEmpty()) {
                throw new IllegalArgumentException("Only MS2011_SYNC may have sourceProductKey");
            }
            return "";
        }
        if (!value.matches("ms2011:[1-9][0-9]*")) {
            throw new IllegalArgumentException("MS2011_SYNC sourceProductKey must be ms2011:<GID>");
        }
        return value;
    }

    /** Snapshot category codes are source facts; local and legacy products keep their fallback. */
    private static String normalizeCategory(String category, ProductOrigin origin) {
        String value = category == null ? "" : category.trim();
        if (origin == ProductOrigin.MS2011_SYNC) {
            return value;
        }
        return value.isEmpty() ? MANUAL_ALMACEN_CATEGORY : value;
    }
}
