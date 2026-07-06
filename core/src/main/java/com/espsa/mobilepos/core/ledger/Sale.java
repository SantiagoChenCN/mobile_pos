package com.espsa.mobilepos.core.ledger;

import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.SaleStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Sale {
    private final String id;
    private final Instant createdAt;
    private final PaymentMethod paymentMethod;
    private final Money subtotal;
    private final Discount cartDiscount;
    private final Money cartDiscountAmount;
    private final Money total;
    private final SaleStatus status;
    private final List<SaleLine> lines;

    public Sale(
            String id,
            Instant createdAt,
            PaymentMethod paymentMethod,
            Money subtotal,
            Discount cartDiscount,
            Money cartDiscountAmount,
            Money total,
            SaleStatus status,
            List<SaleLine> lines
    ) {
        this.id = id;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.paymentMethod = paymentMethod;
        this.subtotal = subtotal;
        this.cartDiscount = cartDiscount;
        this.cartDiscountAmount = cartDiscountAmount;
        this.total = total;
        this.status = status == null ? SaleStatus.NORMAL : status;
        this.lines = Collections.unmodifiableList(new ArrayList<SaleLine>(lines));
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public PaymentMethod paymentMethod() {
        return paymentMethod;
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

    public SaleStatus status() {
        return status;
    }

    public List<SaleLine> lines() {
        return lines;
    }

    public Sale voided() {
        return new Sale(id, createdAt, paymentMethod, subtotal, cartDiscount, cartDiscountAmount, total, SaleStatus.VOIDED, lines);
    }
}

