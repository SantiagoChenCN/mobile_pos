package com.espsa.mobilepos.core.checkout;

import com.espsa.mobilepos.core.catalog.ProductRepository;
import com.espsa.mobilepos.core.catalog.InMemoryProductRepository;
import com.espsa.mobilepos.core.ledger.InMemorySaleRepository;
import com.espsa.mobilepos.core.model.Money;
import com.espsa.mobilepos.core.model.Product;
import com.espsa.mobilepos.core.model.ProductOrigin;
import com.espsa.mobilepos.core.model.Quantity;
import com.espsa.mobilepos.core.pricing.DefaultPriceCalculator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Contract coverage for the CB-01 immutable cart pricing lookup boundary. */
public final class CartPricingSnapshotContractTest {
    private static final String SNAPSHOT_A = "ms2011-20260718T120000Z-aaaaaaaaaaaa";
    private static final String SNAPSHOT_B = "ms2011-20260718T120001Z-bbbbbbbbbbbb";

    public static void main(String[] args) throws Exception {
        oldCartKeepsExplicitSnapshotAfterLiveRepositoryReplacement();
        newCartUsesNewExplicitLookup();
        emptyActiveV2SnapshotKeepsItsExplicitIdentity();
        laterBarcodeAddsAreNotVisibleToOldCart();
        manualAlmacenDoesNotConsultCatalog();
        legacyStartCartUsesLocalLibraryAndNone();
        establishedBarcodePrecedenceIsPreserved();
        stoppedSynchronizedBarcodeBlocksLocalFallback();
        invalidOrAmbiguousSnapshotLookupsFailClosed();
        System.out.println("Cart pricing snapshot contract test passed (25 assertions)");
    }

    private static void oldCartKeepsExplicitSnapshotAfterLiveRepositoryReplacement() throws Exception {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        repository.replaceAll(Collections.singletonList(syncProduct("1001", "7790000000001", "Version A", "100.00", SNAPSHOT_A)));
        CheckoutService checkout = checkout(repository);
        Cart oldCart = checkout.startCart(PricingSnapshotRef.capture(
                SNAPSHOT_A, "none", repository));

        repository.replaceAll(Collections.singletonList(syncProduct("1001", "7790000000001", "Version B", "250.00", SNAPSHOT_B)));
        CartLine line = checkout.addProductByBarcode(oldCart, "7790000000001", Quantity.of("1.25"));

        assertEquals("old cart keeps supplied snapshot id", SNAPSHOT_A, oldCart.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("old cart keeps supplied rule version", "none", oldCart.pricingSnapshotRef().promotionRuleVersion());
        assertEquals("old cart keeps original product name", "Version A", line.product().name());
        assertEquals("old cart keeps exact original price", Money.of("100.00"), line.product().salePrice());
    }

    private static void newCartUsesNewExplicitLookup() throws Exception {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        CheckoutService checkout = checkout(repository);
        Cart newCart = checkout.startCart(PricingSnapshotRef.of(
                SNAPSHOT_B, "none", Collections.singletonList(syncProduct("1001", "7790000000001", "Version B", "250.00", SNAPSHOT_B))));
        CartLine line = checkout.addProductByBarcode(newCart, "7790000000001", Quantity.one());

        assertEquals("new cart uses new explicit snapshot", SNAPSHOT_B, newCart.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("new cart uses new product version", "Version B", line.product().name());
        assertEquals("new cart uses exact new price", Money.of("250.00"), line.product().salePrice());
    }

    private static void emptyActiveV2SnapshotKeepsItsExplicitIdentity() throws Exception {
        Cart emptyActiveCart = checkout(new ThrowingProductRepository()).startCart(PricingSnapshotRef.capture(
                SNAPSHOT_A, "none", new InMemoryProductRepository()));

        assertEquals("empty active v2 retains exact snapshot id", SNAPSHOT_A, emptyActiveCart.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("empty active v2 retains exact rule version", "none", emptyActiveCart.pricingSnapshotRef().promotionRuleVersion());
        expectNotFound("empty active lookup has no product", new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                checkout(new ThrowingProductRepository()).addProductByBarcode(emptyActiveCart, "7790000000001", Quantity.one());
            }
        });
    }

    private static void laterBarcodeAddsAreNotVisibleToOldCart() throws Exception {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        Product initial = syncProduct("1001", "7790000000001", "Initial", "100.00", SNAPSHOT_A);
        repository.replaceAll(Collections.singletonList(initial));
        CheckoutService checkout = checkout(repository);
        Cart cart = checkout.startCart(PricingSnapshotRef.capture(SNAPSHOT_A, "none", repository));
        repository.replaceAll(Arrays.asList(initial, syncProduct("1002", "7790000000002", "Added later", "200.00", SNAPSHOT_B)));

        expectNotFound("later live barcode cannot enter old cart", new ThrowingRunnable() {
            @Override
            public void run() throws Exception {
                checkout.addProductByBarcode(cart, "7790000000002", Quantity.one());
            }
        });
    }

    private static void manualAlmacenDoesNotConsultCatalog() {
        CheckoutService checkout = checkout(new ThrowingProductRepository());
        Cart cart = checkout.startCart(PricingSnapshotRef.of(SNAPSHOT_A, "none", Collections.<Product>emptyList()));
        CartLine manual = checkout.addManualAlmacenItem(cart, Money.of("2099.99"), Quantity.of("1.25"));

        assertTrue("manual almacen is temporary cart product", manual.product().isManualPriceProduct());
        assertEquals("manual almacen retains exact decimal price", Money.of("2099.99"), manual.product().salePrice());
        assertEquals("manual almacen retains exact decimal quantity", "1.25", manual.quantityValue().canonicalText());
        assertEquals("manual almacen does not alter frozen lookup", 0, cart.pricingSnapshotRef().products().size());
    }

    private static void legacyStartCartUsesLocalLibraryAndNone() throws Exception {
        InMemoryProductRepository repository = new InMemoryProductRepository();
        repository.replaceAll(Collections.singletonList(localProduct("local", "7790000000010", "Local", "12.50")));
        CheckoutService checkout = checkout(repository);
        Cart legacyCart = checkout.startCart();
        CartLine localLine = checkout.addProductByBarcode(legacyCart, "7790000000010", Quantity.one());

        assertEquals("legacy start cart uses local library identity", "local-library", legacyCart.pricingSnapshotRef().pricingSnapshotId());
        assertEquals("legacy start cart uses none rule version", "none", legacyCart.pricingSnapshotRef().promotionRuleVersion());
        assertEquals("legacy lookup freezes local product", "Local", localLine.product().name());
    }

    private static void establishedBarcodePrecedenceIsPreserved() throws Exception {
        Product local = localProduct("local-conflict", "7790000000099", "Local fallback", "9.99");
        Product synchronizedProduct = syncProduct("1099", "7790000000099", "Synchronized authority", "10.00", SNAPSHOT_A);
        InMemoryProductRepository repository = new InMemoryProductRepository();
        repository.replaceAll(Arrays.asList(local, synchronizedProduct));
        PricingSnapshotRef ref = PricingSnapshotRef.capture(SNAPSHOT_A, "none", repository);
        Cart cart = checkout(new ThrowingProductRepository()).startCart(ref);
        CartLine line = checkout(new ThrowingProductRepository()).addProductByBarcode(cart, "7790000000099", Quantity.one());

        assertEquals("synchronized product keeps established barcode precedence", "Synchronized authority", line.product().name());
        assertEquals("synchronized product retains exact price", Money.of("10.00"), line.product().salePrice());
    }

    private static void stoppedSynchronizedBarcodeBlocksLocalFallback() throws Exception {
        Product local = localProduct("local-stopped", "7790000000098", "Local fallback", "9.99");
        Product stopped = new Product("ms2011:1098", "7790000000098", "Stopped authority", "sync", "un",
                Money.of("10.00"), null, 0, false, ProductOrigin.MS2011_SYNC,
                "ms2011:1098", SNAPSHOT_A, true);
        InMemoryProductRepository repository = new InMemoryProductRepository();
        repository.replaceAll(Arrays.asList(local, stopped));
        PricingSnapshotRef ref = PricingSnapshotRef.capture(SNAPSHOT_A, "none", repository);
        Cart cart = checkout(new ThrowingProductRepository()).startCart(ref);

        expectNotFound("stopped synchronized barcode blocks local fallback", new ThrowingRunnable() {
            @Override public void run() throws Exception {
                checkout(new ThrowingProductRepository()).addProductByBarcode(cart, "7790000000098", Quantity.one());
            }
        });
        assertEquals("stopped barcode retains complete lookup", 2, ref.products().size());
    }

    private static void invalidOrAmbiguousSnapshotLookupsFailClosed() {
        expectIllegalArgument("empty snapshot id rejected", new Runnable() {
            @Override public void run() { PricingSnapshotRef.of("", "none", Collections.<Product>emptyList()); }
        });
        expectIllegalArgument("empty rule version rejected", new Runnable() {
            @Override public void run() { PricingSnapshotRef.of(SNAPSHOT_A, "", Collections.<Product>emptyList()); }
        });
        expectIllegalArgument("duplicate non-empty barcode rejected", new Runnable() {
            @Override public void run() {
                PricingSnapshotRef.of(SNAPSHOT_A, "none", Arrays.asList(
                        syncProduct("1001", "7790000000099", "One", "1.00", SNAPSHOT_A),
                        syncProduct("1002", "7790000000099", "Two", "2.00", SNAPSHOT_A)
                ));
            }
        });
    }

    private static CheckoutService checkout(ProductRepository repository) {
        return new CheckoutService(repository, new DefaultPriceCalculator(), new InMemorySaleRepository());
    }

    private static Product syncProduct(String gid, String barcode, String name, String price, String snapshotId) {
        String sourceProductKey = "ms2011:" + gid;
        return new Product(sourceProductKey, barcode, name, "sync", "un", Money.of(price), null, 0, false,
                ProductOrigin.MS2011_SYNC, sourceProductKey, snapshotId, false);
    }

    private static Product localProduct(String id, String barcode, String name, String price) {
        return new Product(id, barcode, name, "almacen", "un", Money.of(price), null, 0, false,
                ProductOrigin.LOCAL, "", "", false);
    }

    private static void expectNotFound(String label, ThrowingRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (ProductNotFoundException expected) {
        }
    }

    private static void expectIllegalArgument(String label, Runnable action) {
        try {
            action.run();
            throw new AssertionError(label);
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    private interface ThrowingRunnable { void run() throws Exception; }

    private static final class ThrowingProductRepository implements ProductRepository {
        @Override public List<Product> all() { throw new AssertionError("catalog must not be consulted"); }
        @Override public Optional<Product> findById(String productId) { throw new AssertionError("catalog must not be consulted"); }
        @Override public Optional<Product> findByBarcode(String barcode) { throw new AssertionError("catalog must not be consulted"); }
        @Override public List<Product> findAllByBarcode(String barcode) { throw new AssertionError("catalog must not be consulted"); }
        @Override public List<Product> searchByName(String query, int limit) { throw new AssertionError("catalog must not be consulted"); }
        @Override public void replaceAll(List<Product> products) { throw new AssertionError("catalog must not be consulted"); }
        @Override public void upsert(Product product) { throw new AssertionError("catalog must not be consulted"); }
        @Override public Optional<Product> deleteById(String productId) { throw new AssertionError("catalog must not be consulted"); }
        @Override public boolean barcodeExists(String barcode, String excludedProductId) { throw new AssertionError("catalog must not be consulted"); }
        @Override public boolean exactNameExists(String name, String excludedProductId) { throw new AssertionError("catalog must not be consulted"); }
        @Override public int count() { throw new AssertionError("catalog must not be consulted"); }
    }
}
