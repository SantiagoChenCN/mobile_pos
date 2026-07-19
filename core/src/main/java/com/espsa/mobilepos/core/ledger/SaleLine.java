package com.espsa.mobilepos.core.ledger;

import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Quantity;

import java.util.Objects;

public final class SaleLine {
    private final String productId;
    private final String barcode;
    private final String name;
    private final String category;
    private final Quantity quantity;
    private final Money originalUnitPrice;
    private final Money appliedUnitPrice;
    private final Money grossSubtotal;
    private final Discount lineDiscount;
    private final Money lineDiscountAmount;
    private final Money finalSubtotal;
    private final boolean automaticPromotionApplied;
    private final boolean manualPriceApplied;
    private final boolean manualPriceProduct;

    public SaleLine(
            String productId,
            String barcode,
            String name,
            String category,
            Quantity quantity,
            Money originalUnitPrice,
            Money appliedUnitPrice,
            Money grossSubtotal,
            Discount lineDiscount,
            Money lineDiscountAmount,
            Money finalSubtotal,
            boolean automaticPromotionApplied,
            boolean manualPriceApplied,
            boolean manualPriceProduct
    ) {
        this.productId = productId;
        this.barcode = barcode;
        this.name = name;
        this.category = category;
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.originalUnitPrice = originalUnitPrice;
        this.appliedUnitPrice = appliedUnitPrice;
        this.grossSubtotal = grossSubtotal;
        this.lineDiscount = lineDiscount;
        this.lineDiscountAmount = lineDiscountAmount;
        this.finalSubtotal = finalSubtotal;
        this.automaticPromotionApplied = automaticPromotionApplied;
        this.manualPriceApplied = manualPriceApplied;
        this.manualPriceProduct = manualPriceProduct;
    }

    public String productId() {
        return productId;
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

    public Quantity quantity() {
        return quantity;
    }

    public Money originalUnitPrice() {
        return originalUnitPrice;
    }

    public Money appliedUnitPrice() {
        return appliedUnitPrice;
    }

    public Money grossSubtotal() {
        return grossSubtotal;
    }

    public Discount lineDiscount() {
        return lineDiscount;
    }

    public Money lineDiscountAmount() {
        return lineDiscountAmount;
    }

    public Money finalSubtotal() {
        return finalSubtotal;
    }

    public boolean automaticPromotionApplied() {
        return automaticPromotionApplied;
    }

    public boolean manualPriceApplied() {
        return manualPriceApplied;
    }

    public boolean manualPriceProduct() {
        return manualPriceProduct;
    }
}
