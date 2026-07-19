package com.espsa.mobilepos.core.pricing;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CashChangeCalculator;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.ledger.DailySummary;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.ledger.LedgerService;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;

public final class ExactMoneyPricingContractTest {
    public static void main(String[] args) throws Exception {
        Product product = new Product(
                "decimal-1", "10001", "Decimal product", "almacen", "un",
                Money.of("2099.99"), null, 0, false
        );
        InMemoryProductRepository products = new InMemoryProductRepository();
        products.replaceAll(Collections.singletonList(product));
        InMemorySaleRepository sales = new InMemorySaleRepository();
        CheckoutService checkout = new CheckoutService(
                products, new DefaultPriceCalculator(), sales, ZoneId.of("America/Argentina/Buenos_Aires")
        );

        Cart cart = checkout.startCart();
        cart.addProduct(product, Quantity.one());
        cart.replaceLine(cart.lines().get(0).withLineDiscount(Discount.percent("10")));
        cart.setCartDiscount(Discount.fixedAmount(Money.of("0.001")));

        CartPriceResult preview = checkout.preview(cart);
        assertMoney("2099.99", preview.lines().get(0).grossSubtotal(), "original currency gross subtotal");
        assertMoney("1889.991", preview.subtotal(), "line-discounted subtotal");
        assertMoney("209.999", preview.lines().get(0).lineDiscountAmount(), "exact percentage discount");
        assertMoney("1889.99", preview.total(), "exact cart total");

        Sale sale = checkout.checkout(cart, PaymentMethod.CASH);
        assertMoney("1889.99", sale.total(), "sale keeps original currency decimal");
        DailySummary summary = new LedgerService(
                sales, ZoneId.of("America/Argentina/Buenos_Aires")
        ).dailySummary(LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")));
        assertMoney("1889.99", summary.total(), "ledger sum remains exact");

        assertMoney(
                "0.01",
                new CashChangeCalculator().calculate(Money.of("2099.99"), Money.of("2100")).change(),
                "cash change remains exact"
        );

        System.out.println("Exact money pricing contract test passed");
    }

    private static void assertMoney(String expected, Money actual, String label) {
        if (!expected.equals(actual.canonicalText())) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual.canonicalText());
        }
    }
}
