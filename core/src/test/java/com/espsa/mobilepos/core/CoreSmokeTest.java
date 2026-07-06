package com.espsa.mobilepos.core;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CartLine;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.exporter.CsvSalesExportAdapter;
import com.espsa.mobilepos.core.importer.MingshengProductMapper;
import com.espsa.mobilepos.core.ledger.DailySummary;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.ledger.LedgerService;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.pricing.CartPriceResult;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CoreSmokeTest {
    public static void main(String[] args) throws Exception {
        Map<String, String> mingshengRow = new HashMap<String, String>();
        mingshengRow.put("GID", "1");
        mingshengRow.put("GBarcode", "7790580000001");
        mingshengRow.put("GNameX", "Yerba Oferta");
        mingshengRow.put("RTypeName", "almacen");
        mingshengRow.put("UName", "un");
        mingshengRow.put("GSalePrice", "2000");
        mingshengRow.put("GHuiPrice", "1499");
        mingshengRow.put("GHuiPriceCount", "2");
        Product promoProduct = new MingshengProductMapper().fromGoodListRow(mingshengRow);
        Product huevoBlanco = new Product("2", "7790000000002", "Huevo Blanco", "almacen", "un", Money.of(1200), null, 0, false);
        Product mapleHuevo = new Product("3", "7790000000003", "Huevo Blanco Maple", "almacen", "un", Money.of(3600), null, 0, false);
        Product chocolateHuevo = new Product("4", "7790000000004", "Chocolate con Huevo", "golosinas", "un", Money.of(900), null, 0, false);

        InMemoryProductRepository productRepository = new InMemoryProductRepository();
        productRepository.replaceAll(Arrays.asList(promoProduct, huevoBlanco, mapleHuevo, chocolateHuevo));
        assertTrue("keyword name search matches all words", productRepository.searchByName("yerba oferta", 5).size() == 1);
        assertTrue("keyword name search rejects missing words", productRepository.searchByName("yerba leche", 5).isEmpty());
        List<Product> huevoResults = productRepository.searchByName("huevo", Integer.MAX_VALUE);
        assertTrue("unlimited keyword search returns every huevo product", huevoResults.size() == 3);
        assertTrue("huevo search includes huevo blanco", containsProductName(huevoResults, "Huevo Blanco"));
        assertTrue("huevo search includes maple item", containsProductName(huevoResults, "Huevo Blanco Maple"));
        assertTrue("multi-word keyword search matches words in any order", productRepository.searchByName("maple huevo", Integer.MAX_VALUE).size() == 1);
        assertTrue("multi-word search ignores common connector words", productRepository.searchByName("maple de huevo", Integer.MAX_VALUE).size() == 1);

        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        CheckoutService checkout = new CheckoutService(productRepository, new DefaultPriceCalculator(), saleRepository);

        Cart cart = checkout.startCart();
        CartLine line = checkout.addProductByBarcode(cart, "7790580000001", 3);
        CartPriceResult automaticPromo = checkout.preview(cart);
        assertAmount("quantity promotion applies to all units", 4497, automaticPromo.subtotal());
        assertTrue("automatic promotion marker", automaticPromo.lines().get(0).automaticPromotionApplied());

        CartLine overridden = line.withManualUnitPrice(Money.of(1600)).withLineDiscount(Discount.percent(1000));
        cart.replaceLine(overridden);
        cart.setCartDiscount(Discount.fixedAmount(Money.of(320)));

        CartPriceResult manualPricing = checkout.preview(cart);
        assertAmount("manual price becomes line base price before line discount", 4320, manualPricing.subtotal());
        assertAmount("cart fixed discount applies after line discount", 4000, manualPricing.total());
        assertTrue("manual price suppresses automatic promotion", !manualPricing.lines().get(0).automaticPromotionApplied());

        checkout.addManualAlmacenItem(cart, Money.of(500), 1);
        CartPriceResult withManualAlmacen = checkout.preview(cart);
        assertAmount("manual almacen item participates in cart total", 4500, withManualAlmacen.total());

        Sale sale = checkout.checkout(cart, PaymentMethod.TRANSFERENCIA);
        assertAmount("sale total snapshot", 4500, sale.total());
        assertTrue("sale saved with two lines", sale.lines().size() == 2);
        assertTrue("manual item category is almacen", "almacen".equals(sale.lines().get(1).category()));

        LedgerService ledger = new LedgerService(saleRepository, ZoneId.systemDefault());
        DailySummary summary = ledger.dailySummary(LocalDate.now());
        assertAmount("daily transferencia total", 4500, summary.totalFor(PaymentMethod.TRANSFERENCIA));

        ByteArrayOutputStream csv = new ByteArrayOutputStream();
        new CsvSalesExportAdapter().exportSales(Arrays.asList(sale), LocalDate.now(), LocalDate.now(), csv);
        String csvText = csv.toString("UTF-8");
        assertTrue("csv export contains sale section", csvText.contains("sales"));
        assertTrue("csv export contains item barcode", csvText.contains("7790580000001"));

        checkout.voidSale(sale.id());
        DailySummary afterVoid = ledger.dailySummary(LocalDate.now());
        assertAmount("voided sale excluded from normal daily total", 0, afterVoid.total());
        assertAmount("voided total tracked separately", 4500, afterVoid.voidedTotal());

        System.out.println("Core smoke test passed");
    }

    private static void assertAmount(String label, long expected, Money actual) {
        if (actual.amount() != expected) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual.amount());
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static boolean containsProductName(List<Product> products, String name) {
        for (Product product : products) {
            if (product.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
