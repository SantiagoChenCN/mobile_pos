package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;

import java.util.Objects;
import java.util.UUID;

public final class CartLine {
    private final String id;
    private final Product product;
    private final Quantity quantity;
    private final Money manualUnitPrice;
    private final Discount lineDiscount;

    public CartLine(Product product, Quantity quantity) {
        this(UUID.randomUUID().toString(), product, quantity, null, Discount.NONE);
    }

    public CartLine(String id, Product product, Quantity quantity, Money manualUnitPrice, Discount lineDiscount) {
        this.id = id == null || id.trim().isEmpty() ? UUID.randomUUID().toString() : id;
        this.product = Objects.requireNonNull(product, "product");
        this.quantity = Objects.requireNonNull(quantity, "quantity");
        this.manualUnitPrice = manualUnitPrice;
        this.lineDiscount = lineDiscount == null ? Discount.NONE : lineDiscount;
    }

    public String id() {
        return id;
    }

    public Product product() {
        return product;
    }

    public Quantity quantityValue() {
        return quantity;
    }

    public Money manualUnitPrice() {
        return manualUnitPrice;
    }

    public Discount lineDiscount() {
        return lineDiscount;
    }

    public CartLine withQuantity(Quantity newQuantity) {
        return new CartLine(id, product, newQuantity, manualUnitPrice, lineDiscount);
    }

    public CartLine withManualUnitPrice(Money price) {
        return new CartLine(id, product, quantity, Objects.requireNonNull(price, "price"), lineDiscount);
    }

    public CartLine withoutManualUnitPrice() {
        return new CartLine(id, product, quantity, null, lineDiscount);
    }

    public CartLine withLineDiscount(Discount discount) {
        return new CartLine(id, product, quantity, manualUnitPrice, discount);
    }

    public CartLine withoutLineDiscount() {
        return new CartLine(id, product, quantity, manualUnitPrice, Discount.NONE);
    }

    public CartLine withoutManualAdjustments() {
        return new CartLine(id, product, quantity, null, Discount.NONE);
    }
}
