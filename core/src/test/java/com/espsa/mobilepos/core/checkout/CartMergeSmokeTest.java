package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;

import java.time.Instant;
import java.time.ZoneId;

public final class CartMergeSmokeTest {
    public static void main(String[] args) throws Exception {
        CheckoutService argentinaCheckout = new CheckoutService(
                new InMemoryProductRepository(),
                new DefaultPriceCalculator(),
                new InMemorySaleRepository(),
                ZoneId.of("America/Argentina/Buenos_Aires")
        );
        assertTrue("sale id uses configured business zone", "SALE-20260710-233000-7".equals(
                argentinaCheckout.formatSaleId(Instant.parse("2026-07-11T02:30:00Z"), 7)
        ));

        Product product = new Product(
                "product-a",
                "7790000000001",
                "Producto A",
                "almacen",
                "un",
                Money.of(1000),
                Money.of(800),
                3,
                false
        );
        Cart cart = new Cart("cart-1");
        CartLine first = cart.addProduct(product, 1);
        CartLine merged = cart.addProduct(product, 2);
        assertTrue("formal product is merged into one line", cart.lines().size() == 1);
        assertTrue("merged quantity is accumulated", merged.quantity() == 3);
        assertTrue("line id is preserved", first.id().equals(merged.id()));
        assertTrue("quantity promotion is recalculated by pricing", new DefaultPriceCalculator()
                .calculateCart(cart).lines().get(0).automaticPromotionApplied());

        CartLine adjusted = merged.withManualUnitPrice(Money.of(1500)).withLineDiscount(Discount.percent(1000));
        cart.replaceLine(adjusted);
        CartLine adjustedMerge = cart.addProduct(product, 1);
        assertTrue("manual price is preserved", Money.of(1500).equals(adjustedMerge.manualUnitPrice()));
        assertTrue("line discount is preserved", adjustedMerge.lineDiscount().type() == Discount.percent(1000).type()
                && adjustedMerge.lineDiscount().value() == Discount.percent(1000).value());
        assertTrue("adjusted line id is preserved", adjusted.id().equals(adjustedMerge.id()));

        Product sameName = new Product(
                "product-b",
                "7790000000002",
                "Producto A",
                "almacen",
                "un",
                Money.of(1000),
                null,
                0,
                false
        );
        cart.addProduct(sameName, 1);
        assertTrue("different product id remains a separate line", cart.lines().size() == 2);

        Product manual = Product.manualAlmacen("manual-1", Money.of(1000));
        Cart manualCart = new Cart("cart-2");
        manualCart.addProduct(manual, 1);
        manualCart.addProduct(manual, 1);
        assertTrue("manual almacen products are not merged", manualCart.lines().size() == 2);

        Product sameIdManual = Product.manualAlmacen("same-id", Money.of(1000));
        Product sameIdFormal = new Product(
                "same-id",
                "7790000000001",
                "Producto formal",
                "almacen",
                "un",
                Money.of(1000),
                null,
                0,
                false
        );
        Cart manualFirst = new Cart("manual-first");
        manualFirst.addProduct(sameIdManual, 1);
        manualFirst.addProduct(sameIdFormal, 1);
        assertTrue("manual and formal product with same id stay separate", manualFirst.lines().size() == 2);

        Cart formalFirst = new Cart("formal-first");
        formalFirst.addProduct(sameIdFormal, 1);
        formalFirst.addProduct(sameIdManual, 1);
        assertTrue("formal and manual product with same id stay separate", formalFirst.lines().size() == 2);

        expectIllegalArgument("zero quantity is rejected", new Runnable() {
            @Override
            public void run() {
                cart.addProduct(product, 0);
            }
        });
        expectIllegalArgument("negative quantity is rejected", new Runnable() {
            @Override
            public void run() {
                cart.addProduct(product, -1);
            }
        });
        expectArithmeticException("quantity overflow is rejected", new Runnable() {
            @Override
            public void run() {
                Cart overflow = new Cart("overflow");
                overflow.addProduct(product, Integer.MAX_VALUE);
                overflow.addProduct(product, 1);
            }
        });

        System.out.println("Cart merge smoke test passed");
    }

    private static void expectIllegalArgument(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void expectArithmeticException(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (ArithmeticException expected) {
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
