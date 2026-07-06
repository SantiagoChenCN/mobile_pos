package com.espsa.mobilepos.core.pricing;

import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CartPriceResult {
    private final List<LinePriceResult> lines;
    private final Money subtotal;
    private final Discount cartDiscount;
    private final Money cartDiscountAmount;
    private final Money total;

    public CartPriceResult(List<LinePriceResult> lines, Money subtotal, Discount cartDiscount, Money cartDiscountAmount, Money total) {
        this.lines = Collections.unmodifiableList(new ArrayList<LinePriceResult>(lines));
        this.subtotal = subtotal;
        this.cartDiscount = cartDiscount;
        this.cartDiscountAmount = cartDiscountAmount;
        this.total = total;
    }

    public List<LinePriceResult> lines() {
        return lines;
    }

    public Money subtotal() {
        return subtotal;
    }

    public Discount cartDiscount() {
        return cartDiscount;
    }

    public Money cartDiscountAmount() {
        return cartDiscountAmount;
    }

    public Money total() {
        return total;
    }
}

