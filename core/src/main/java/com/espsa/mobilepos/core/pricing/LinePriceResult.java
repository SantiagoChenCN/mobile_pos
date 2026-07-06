package com.espsa.mobilepos.core.pricing;

import com.espsa.mobilepos.core.checkout.CartLine;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;

public final class LinePriceResult {
    private final CartLine line;
    private final Money originalUnitPrice;
    private final Money appliedUnitPrice;
    private final Money grossSubtotal;
    private final Discount lineDiscount;
    private final Money lineDiscountAmount;
    private final Money finalSubtotal;
    private final boolean automaticPromotionApplied;
    private final boolean manualPriceApplied;

    public LinePriceResult(
            CartLine line,
            Money originalUnitPrice,
            Money appliedUnitPrice,
            Money grossSubtotal,
            Discount lineDiscount,
            Money lineDiscountAmount,
            Money finalSubtotal,
            boolean automaticPromotionApplied,
            boolean manualPriceApplied
    ) {
        this.line = line;
        this.originalUnitPrice = originalUnitPrice;
        this.appliedUnitPrice = appliedUnitPrice;
        this.grossSubtotal = grossSubtotal;
        this.lineDiscount = lineDiscount;
        this.lineDiscountAmount = lineDiscountAmount;
        this.finalSubtotal = finalSubtotal;
        this.automaticPromotionApplied = automaticPromotionApplied;
        this.manualPriceApplied = manualPriceApplied;
    }

    public CartLine line() {
        return line;
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
}

