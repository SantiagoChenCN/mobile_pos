package com.espsa.mobilepos.core.pricing;

import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CartLine;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;

public final class FractionalQuantityPricingContractTest {
    public static void main(String[] args) {
        Product product = new Product(
                "weighted-1", "2001", "Weighted product", "almacen", "kg",
                Money.of("2.5"), Money.of("2"), 2, false
        );
        Cart cart = new Cart("quantity-cart");
        cart.addProduct(product, Quantity.of("1.25"));
        CartLine merged = cart.addProduct(product, Quantity.of("0.75"));
        assertQuantity("2", merged.quantityValue(), "exact fractional merge");

        DefaultPriceCalculator calculator = new DefaultPriceCalculator();
        LinePriceResult integer = calculator.calculateLine(merged);
        assertTrue(integer.automaticPromotionApplied(), "integer quantity can use existing automatic promo");
        assertMoney("4", integer.finalSubtotal(), "integer promotion total");

        CartLine fractional = merged.withQuantity(Quantity.of("1.5"));
        LinePriceResult fractionalPrice = calculator.calculateLine(fractional);
        assertTrue(!fractionalPrice.automaticPromotionApplied(),
                "fractional quantity is excluded from all automatic promotions");
        assertMoney("3.75", fractionalPrice.finalSubtotal(), "fractional normal-price total");

        Cart exactCart = new Cart("exact-cart");
        exactCart.addProduct(product, Quantity.of("0.1"));
        exactCart.addProduct(product, Quantity.of("0.2"));
        assertQuantity("0.3", exactCart.lines().get(0).quantityValue(), "0.1 + 0.2 remains exact");
        assertMoney("0.75", calculator.calculateCart(exactCart).total(), "exact fractional cart total");

        System.out.println("Fractional quantity pricing contract test passed");
    }

    private static void assertMoney(String expected, Money actual, String label) {
        if (!expected.equals(actual.canonicalText())) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual.canonicalText());
        }
    }

    private static void assertQuantity(String expected, Quantity actual, String label) {
        if (!expected.equals(actual.canonicalText())) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual.canonicalText());
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
