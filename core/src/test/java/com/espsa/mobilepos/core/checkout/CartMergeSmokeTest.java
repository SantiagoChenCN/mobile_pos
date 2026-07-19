package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;
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
                Money.of("1000"),
                Money.of("800"),
                3,
                false
        );
        Cart cart = new Cart("cart-1");
        CartLine first = cart.addProduct(product, Quantity.one());
        CartLine merged = cart.addProduct(product, Quantity.of("2"));
        assertTrue("formal product is merged into one line", cart.lines().size() == 1);
        assertTrue("merged quantity is accumulated", Quantity.of("3").equals(merged.quantityValue()));
        assertTrue("line id is preserved", first.id().equals(merged.id()));
        assertTrue("quantity promotion is recalculated by pricing", new DefaultPriceCalculator()
                .calculateCart(cart).lines().get(0).automaticPromotionApplied());

        CartLine adjusted = merged.withManualUnitPrice(Money.of("1500")).withLineDiscount(Discount.percent("10"));
        cart.replaceLine(adjusted);
        CartLine adjustedMerge = cart.addProduct(product, Quantity.one());
        assertTrue("manual price is preserved", Money.of("1500").equals(adjustedMerge.manualUnitPrice()));
        assertTrue("line discount is preserved", adjustedMerge.lineDiscount().equals(Discount.percent("10")));
        assertTrue("adjusted line id is preserved", adjusted.id().equals(adjustedMerge.id()));

        Product sameName = new Product(
                "product-b",
                "7790000000002",
                "Producto A",
                "almacen",
                "un",
                Money.of("1000"),
                null,
                0,
                false
        );
        cart.addProduct(sameName, Quantity.one());
        assertTrue("different product id remains a separate line", cart.lines().size() == 2);

        Product manual = Product.manualAlmacen("manual-1", Money.of("1000"));
        Cart manualCart = new Cart("cart-2");
        manualCart.addProduct(manual, Quantity.one());
        manualCart.addProduct(manual, Quantity.one());
        assertTrue("manual almacen products are not merged", manualCart.lines().size() == 2);

        Product sameIdManual = Product.manualAlmacen("same-id", Money.of("1000"));
        Product sameIdFormal = new Product(
                "same-id",
                "7790000000001",
                "Producto formal",
                "almacen",
                "un",
                Money.of("1000"),
                null,
                0,
                false
        );
        Cart manualFirst = new Cart("manual-first");
        manualFirst.addProduct(sameIdManual, Quantity.one());
        manualFirst.addProduct(sameIdFormal, Quantity.one());
        assertTrue("manual and formal product with same id stay separate", manualFirst.lines().size() == 2);

        Cart formalFirst = new Cart("formal-first");
        formalFirst.addProduct(sameIdFormal, Quantity.one());
        formalFirst.addProduct(sameIdManual, Quantity.one());
        assertTrue("formal and manual product with same id stay separate", formalFirst.lines().size() == 2);

        expectIllegalArgument("zero quantity is rejected", new Runnable() {
            @Override
            public void run() {
                cart.addProduct(product, Quantity.of("0"));
            }
        });
        expectIllegalArgument("negative quantity is rejected", new Runnable() {
            @Override
            public void run() {
                cart.addProduct(product, Quantity.of("-1"));
            }
        });
        Cart beyondLegacyInt = new Cart("beyond-legacy-int");
        beyondLegacyInt.addProduct(product, Quantity.of("2147483647"));
        beyondLegacyInt.addProduct(product, Quantity.one());
        assertTrue("quantity no longer overflows at legacy int limit",
                "2147483648".equals(beyondLegacyInt.lines().get(0).quantityValue().canonicalText()));

        System.out.println("Cart merge smoke test passed");
    }

    private static void expectIllegalArgument(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
