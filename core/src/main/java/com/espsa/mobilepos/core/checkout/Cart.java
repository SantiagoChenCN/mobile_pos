package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Cart {
    private final String id;
    private final List<CartLine> lines;
    private Discount cartDiscount;

    public Cart() {
        this(UUID.randomUUID().toString());
    }

    public Cart(String id) {
        this.id = id == null || id.trim().isEmpty() ? UUID.randomUUID().toString() : id;
        this.lines = new ArrayList<CartLine>();
        this.cartDiscount = Discount.NONE;
    }

    public String id() {
        return id;
    }

    public List<CartLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public Discount cartDiscount() {
        return cartDiscount;
    }

    public CartLine addProduct(Product product, int quantity) {
        Objects.requireNonNull(product, "product");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        if (!product.isManualPriceProduct()) {
            for (int index = 0; index < lines.size(); index++) {
                CartLine existing = lines.get(index);
                if (sameProduct(existing.product(), product)) {
                    CartLine updated = existing.withQuantity(Math.addExact(existing.quantity(), quantity));
                    lines.set(index, updated);
                    return updated;
                }
            }
        }

        CartLine line = new CartLine(product, quantity);
        lines.add(line);
        return line;
    }

    private boolean sameProduct(Product left, Product right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.isManualPriceProduct() || right.isManualPriceProduct()) {
            return false;
        }
        return left.id().equals(right.id());
    }

    public void replaceLine(CartLine updatedLine) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(updatedLine.id())) {
                lines.set(i, updatedLine);
                return;
            }
        }
        throw new IllegalArgumentException("Cart line not found: " + updatedLine.id());
    }

    public void removeLine(String lineId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(lineId)) {
                lines.remove(i);
                return;
            }
        }
    }

    public void setCartDiscount(Discount discount) {
        this.cartDiscount = discount == null ? Discount.NONE : discount;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
