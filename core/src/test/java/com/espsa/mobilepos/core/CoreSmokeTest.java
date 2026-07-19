package com.espsa.mobilepos.core;

import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.checkout.CashChangeCalculator;
import com.espsa.mobilepos.core.checkout.CashChangeResult;
import com.espsa.mobilepos.core.checkout.Cart;
import com.espsa.mobilepos.core.checkout.CartLine;
import com.espsa.mobilepos.core.checkout.CheckoutService;
import com.espsa.mobilepos.core.editing.ProductCreateResult;
import com.espsa.mobilepos.core.editing.ProductDraft;
import com.espsa.mobilepos.core.editing.ProductEditingService;
import com.espsa.mobilepos.core.editing.ProductOptionProvider;
import com.espsa.mobilepos.core.editing.ProductPersistenceException;
import com.espsa.mobilepos.core.editing.ProductPersistencePort;
import com.espsa.mobilepos.core.editing.ProductUpdateResult;
import com.espsa.mobilepos.core.exporter.CsvSalesExportAdapter;
import com.espsa.mobilepos.core.importer.CsvProductImportAdapter;
import com.espsa.mobilepos.core.importer.MingshengProductMapper;
import com.espsa.mobilepos.core.importer.ProductImportException;
import com.espsa.mobilepos.core.importer.ProductImportResult;
import com.espsa.mobilepos.core.ledger.DailySummary;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.ledger.LedgerService;
import com.espsa.mobilepos.core.ledger.Sale;
import com.espsa.mobilepos.core.model.Discount;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.PaymentMethod;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.Quantity;
import com.espsa.mobilepos.core.pricing.CartPriceResult;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
        Product huevoBlanco = new Product("2", "7790000000002", "Huevo Blanco", "almacen", "un", Money.of("1200"), null, 0, false);
        Product mapleHuevo = new Product("3", "7790000000003", "Huevo Blanco Maple", "almacen", "un", Money.of("3600"), null, 0, false);
        Product chocolateHuevo = new Product("4", "7790000000004", "Chocolate con Huevo", "golosinas", "un", Money.of("900"), null, 0, false);

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
        assertTrue("barcode exact match sorts first", "Huevo Blanco Maple".equals(productRepository.searchByName("7790000000003", Integer.MAX_VALUE).get(0).name()));
        runSearchIndexUpdateChecks(productRepository);
        runProductEditingChecks(productRepository);
        runCashChangeChecks();
        runCsvImportChecks();

        InMemorySaleRepository saleRepository = new InMemorySaleRepository();
        CheckoutService checkout = new CheckoutService(productRepository, new DefaultPriceCalculator(), saleRepository);

        Cart cart = checkout.startCart();
        CartLine line = checkout.addProductByBarcode(cart, "7790580000001", Quantity.of("3"));
        CartPriceResult automaticPromo = checkout.preview(cart);
        assertAmount("quantity promotion applies to all units", 4497, automaticPromo.subtotal());
        assertTrue("automatic promotion marker", automaticPromo.lines().get(0).automaticPromotionApplied());

        CartLine overridden = line.withManualUnitPrice(Money.of("1600")).withLineDiscount(Discount.percent("10"));
        cart.replaceLine(overridden);
        cart.setCartDiscount(Discount.fixedAmount(Money.of("320")));

        CartPriceResult manualPricing = checkout.preview(cart);
        assertAmount("manual price becomes line base price before line discount", 4320, manualPricing.subtotal());
        assertAmount("cart fixed discount applies after line discount", 4000, manualPricing.total());
        assertTrue("manual price suppresses automatic promotion", !manualPricing.lines().get(0).automaticPromotionApplied());

        checkout.addManualAlmacenItem(cart, Money.of("500"), Quantity.one());
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

    private static void runProductEditingChecks(InMemoryProductRepository productRepository) throws Exception {
        ProductEditingService editing = new ProductEditingService(
                productRepository,
                new NoOpProductPersistence(),
                new ProductOptionProvider(productRepository::all)
        );

        ProductCreateResult created = editing.createProduct(new ProductDraft(
                "001",
                "Azucar Local",
                "",
                "un",
                "1500",
                "",
                ""
        ));
        assertTrue("product create succeeds", created.success());
        assertTrue("created product searchable by barcode", editing.findByBarcode("001").isPresent());
        assertTrue("empty category normalizes to almacen", Product.MANUAL_ALMACEN_CATEGORY.equals(created.product().category()));

        ProductCreateResult duplicateBarcode = editing.createProduct(new ProductDraft(
                "001",
                "Azucar Copia",
                "",
                "un",
                "1500",
                "",
                ""
        ));
        assertTrue("duplicate barcode rejected", !duplicateBarcode.success());

        ProductUpdateResult updated = editing.updateProduct(created.product().id(), new ProductDraft(
                "002",
                "Azucar Local 1kg",
                "almacen",
                "un",
                "1800",
                "1600",
                "2"
        ));
        assertTrue("product update succeeds", updated.success());
        assertTrue("critical update detected", updated.criticalChanges());
        assertTrue("old barcode removed", !editing.findByBarcode("001").isPresent());
        assertTrue("new barcode indexed", editing.findByBarcode("002").isPresent());

        assertTrue("delete removes product", editing.deleteProduct(created.product().id()).success());
        assertTrue("deleted product not indexed", !editing.findByBarcode("002").isPresent());
    }

    private static void runSearchIndexUpdateChecks(InMemoryProductRepository productRepository) {
        Product indexed = new Product("index-1", "00991", "Cafe Molido", "almacen", "un", Money.of("2500"), null, 0, false);
        productRepository.upsert(indexed);
        assertTrue("upserted product is searchable", containsProductName(productRepository.searchByName("cafe molido", Integer.MAX_VALUE), "Cafe Molido"));

        Product renamed = new Product("index-1", "00991", "Te Negro", "almacen", "un", Money.of("2600"), null, 0, false);
        productRepository.upsert(renamed);
        assertTrue("old name no longer matches after upsert", !containsProductName(productRepository.searchByName("cafe molido", Integer.MAX_VALUE), "Cafe Molido"));
        assertTrue("new name matches after upsert", containsProductName(productRepository.searchByName("te negro", Integer.MAX_VALUE), "Te Negro"));

        productRepository.deleteById("index-1");
        assertTrue("deleted product is removed from search index", productRepository.searchByName("te negro", Integer.MAX_VALUE).isEmpty());
    }

    private static void runCashChangeChecks() {
        CashChangeCalculator calculator = new CashChangeCalculator();
        CashChangeResult exact = calculator.calculate(Money.of("1000"), Money.of("1000"));
        assertAmount("cash change exact payment", 0, exact.change());

        CashChangeResult overpaid = calculator.calculate(Money.of("1000"), Money.of("1500"));
        assertAmount("cash change overpayment", 500, overpaid.change());

        expectIllegalArgument("cash change rejects insufficient payment", new Runnable() {
            @Override
            public void run() {
                calculator.calculate(Money.of("1000"), Money.of("999"));
            }
        });
        expectIllegalArgument("cash change rejects null total", new Runnable() {
            @Override
            public void run() {
                calculator.calculate(null, Money.of("1000"));
            }
        });
        expectIllegalArgument("cash change rejects null received", new Runnable() {
            @Override
            public void run() {
                calculator.calculate(Money.of("1000"), null);
            }
        });
    }

    private static void runCsvImportChecks() throws Exception {
        CsvProductImportAdapter adapter = new CsvProductImportAdapter();
        String csv = "barcode,name,price,category,unit\n"
                + "1001,Arroz Largo,1800,almacen,un\n"
                + "1002,\"Cafe, Molido\",2500,bebidas,pack\n"
                + "1002,Cafe Duplicado,2600,bebidas,pack\n"
                + ",Sin Barcode,1000,almacen,un\n";
        ProductImportResult result = adapter.importProducts(csvStream(csv), "products.csv");
        assertTrue("csv import keeps valid products", result.productCount() == 2);
        assertTrue("csv import stores quoted comma name", containsProductName(result.products(), "Cafe, Molido"));
        assertTrue("csv import reports row warnings", result.warnings().size() == 2);

        String aliasCsv = "codigo,nombre,precio,categoria,unidad\n"
                + "2001,Leche Entera,1200,lacteos,un\n";
        ProductImportResult aliasResult = adapter.importProducts(csvStream(aliasCsv), "alias.csv");
        assertTrue("csv import supports aliases", aliasResult.productCount() == 1);
        assertTrue("csv import alias barcode is searchable data", "2001".equals(aliasResult.products().get(0).barcode()));

        expectProductImportException("csv import rejects missing price column", new ImportAction() {
            @Override
            public void run() throws ProductImportException {
                adapter.importProducts(csvStream("barcode,name\n1,No Price\n"), "bad.csv");
            }
        });
    }

    private static void assertAmount(String label, long expected, Money actual) {
        if (!Money.of(Long.toString(expected)).equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but got " + actual.canonicalText());
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectIllegalArgument(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
        } catch (NullPointerException expected) {
        }
    }

    private static void expectProductImportException(String label, ImportAction action) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (ProductImportException expected) {
        }
    }

    private static ByteArrayInputStream csvStream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean containsProductName(List<Product> products, String name) {
        for (Product product : products) {
            if (product.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static final class NoOpProductPersistence implements ProductPersistencePort {
        @Override
        public void saveManualProducts(List<Product> products) throws ProductPersistenceException {
        }
    }

    private interface ImportAction {
        void run() throws ProductImportException;
    }
}
